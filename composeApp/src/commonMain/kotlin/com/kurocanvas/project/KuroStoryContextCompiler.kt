package com.kurocanvas.project

enum class KuroContextStatus {
    READY,
    INVALID_REQUEST,
    STORY_SCOPE_NOT_FOUND
}

enum class KuroContextIssueKind {
    UNKNOWN_FACT,
    CONFLICTING_FACTS
}

data class KuroContextIssue(
    val kind: KuroContextIssueKind,
    val subjectId: String,
    val predicate: String,
    val values: List<String> = emptyList()
)

data class KuroContextRequest(
    val chapter: Int,
    val branchId: String = "main",
    /** Empty scope fails closed to prevent unbounded long-story retrieval. */
    val entityIds: Set<String>,
    val requestedPredicates: Set<String> = emptySet(),
    /** Number of previous chapters to include in addition to the current chapter. */
    val lookbackChapters: Int = 3,
    val includeProposed: Boolean = false
)

data class KuroCompiledStoryContext(
    val status: KuroContextStatus,
    val chapter: Int,
    val branchId: String,
    val entities: List<KuroStoryEntity> = emptyList(),
    val facts: List<KuroCanonFact> = emptyList(),
    val relationships: List<KuroStoryRelationship> = emptyList(),
    val recentEvents: List<KuroStoryEvent> = emptyList(),
    val issues: List<KuroContextIssue> = emptyList(),
    val includesProposedRecords: Boolean = false,
    val message: String = ""
) {
    fun renderForModel(): String {
        if (status != KuroContextStatus.READY) {
            return "STORY CONTEXT UNAVAILABLE: $message Do not invent missing canon."
        }
        val lines = mutableListOf(
            "CANON CONTEXT",
            "Branch: $branchId",
            "Current chapter: $chapter",
            "Scope contains ${entities.size} relevant entities.",
            if (includesProposedRecords) "Confirmed records are canon. Proposed records are unconfirmed suggestions and must never be treated as established facts." else "Use only confirmed facts below. Unknown facts are unknown, not permission to guess."
        )
        lines += "\nENTITIES"
        entities.forEach { lines += "- ${it.id}: ${it.name} (${it.type.name.lowercase()})" }
        lines += "\nFACT RECORDS"
        if (facts.isEmpty()) lines += if (includesProposedRecords) "- No matching confirmed or proposed facts were found." else "- No matching confirmed facts were found."
        facts.forEach { fact ->
            val end = fact.untilChapterExclusive?.let { (it - 1).toString() } ?: "ongoing"
            lines += "- [${fact.status.name.lowercase()}] ${fact.subjectId} / ${fact.predicate}: ${fact.value} (branch=${fact.branchId}, chapters ${fact.fromChapter}–$end)"
            if (fact.sourceNote.isNotBlank()) lines += "  Source note: ${fact.sourceNote}"
        }
        lines += "\nACTIVE RELATIONSHIPS"
        if (relationships.isEmpty()) lines += if (includesProposedRecords) "- No matching confirmed or proposed relationships were found." else "- No matching confirmed relationships were found."
        relationships.forEach { relation ->
            lines += "- [${relation.status.name.lowercase()}] ${relation.fromEntityId} — ${relation.relationshipType} — ${relation.toEntityId}"
        }
        lines += "\nRECENT STORY EVENTS"
        if (recentEvents.isEmpty()) lines += if (includesProposedRecords) "- No confirmed or proposed events in the requested chapter window." else "- No confirmed events in the requested chapter window."
        recentEvents.forEach { event ->
            val description = if (event.description.isNotBlank()) " — ${event.description}" else ""
            lines += "- [${event.status.name.lowercase()}] Chapter ${event.chapter}: ${event.title}$description"
        }
        if (issues.isNotEmpty()) {
            lines += "\nCANON ISSUES — DO NOT RESOLVE BY GUESSING"
            issues.forEach { issue ->
                val values = if (issue.values.isEmpty()) "" else ": ${issue.values.joinToString(" vs ")}"
                lines += "- ${issue.kind.name}: ${issue.subjectId} / ${issue.predicate}$values"
            }
        }
        return lines.joinToString("\n")
    }
}

/**
 * Deterministic, bounded source-of-truth lookup. It never fabricates a value and refuses an
 * unscoped request, so long histories can be compiled by relevant entities and chapter windows.
 */
object KuroStoryContextCompiler {
    fun compile(
        project: KuroProjectState,
        request: KuroContextRequest
    ): KuroCompiledStoryContext {
        if (request.chapter < 1 || request.branchId.isBlank() || request.lookbackChapters < 0) {
            return KuroCompiledStoryContext(
                KuroContextStatus.INVALID_REQUEST,
                request.chapter,
                request.branchId,
                message = "Use a positive chapter, a non-empty branch, and a non-negative event lookback."
            )
        }
        if (request.entityIds.isEmpty()) {
            return KuroCompiledStoryContext(
                KuroContextStatus.STORY_SCOPE_NOT_FOUND,
                request.chapter,
                request.branchId,
                message = "No story entities were supplied for context retrieval."
            )
        }

        val allEntities = project.storyEntities.associateBy { it.id }
        val requestedIds = request.entityIds.filter { it in allEntities }.toSet()
        if (requestedIds.isEmpty()) {
            return KuroCompiledStoryContext(
                KuroContextStatus.STORY_SCOPE_NOT_FOUND,
                request.chapter,
                request.branchId,
                message = "None of the requested story entity IDs exist in this project."
            )
        }

        val activeStatuses = if (request.includeProposed) {
            setOf(KuroCanonFactStatus.CONFIRMED, KuroCanonFactStatus.PROPOSED)
        } else {
            setOf(KuroCanonFactStatus.CONFIRMED)
        }
        val activeRelationships = project.relationships.filter { relation ->
            relation.branchId == request.branchId &&
                relation.status in activeStatuses &&
                relation.fromChapter <= request.chapter &&
                (relation.untilChapterExclusive == null || request.chapter < relation.untilChapterExclusive) &&
                (relation.fromEntityId in requestedIds || relation.toEntityId in requestedIds)
        }
        val scopedIds = requestedIds + activeRelationships.flatMap { listOf(it.fromEntityId, it.toEntityId) }
        val scopedEntities = scopedIds.mapNotNull { allEntities[it] }.sortedBy { it.name.lowercase() }

        val matchingFacts = project.canonFacts.filter { fact ->
            fact.subjectId in scopedIds &&
                fact.branchId == request.branchId &&
                fact.status in activeStatuses &&
                fact.fromChapter <= request.chapter &&
                (fact.untilChapterExclusive == null || request.chapter < fact.untilChapterExclusive) &&
                (request.requestedPredicates.isEmpty() ||
                    request.requestedPredicates.any { it.equals(fact.predicate, ignoreCase = true) })
        }.sortedWith(compareBy<KuroCanonFact> { it.subjectId }.thenBy { it.predicate.lowercase() })

        val issues = mutableListOf<KuroContextIssue>()
        if (request.requestedPredicates.isNotEmpty()) {
            scopedIds.forEach { entityId ->
                request.requestedPredicates.forEach { predicate ->
                    when (val resolved = project.resolveCanonFact(entityId, predicate, request.chapter, request.branchId)) {
                        KuroCanonResolution.Unknown -> issues += KuroContextIssue(
                            KuroContextIssueKind.UNKNOWN_FACT, entityId, predicate
                        )
                        is KuroCanonResolution.Conflict -> issues += KuroContextIssue(
                            KuroContextIssueKind.CONFLICTING_FACTS,
                            entityId,
                            predicate,
                            resolved.facts.map { it.value }.distinct()
                        )
                        is KuroCanonResolution.Known -> Unit
                    }
                }
            }
        }

        matchingFacts.groupBy { it.subjectId to it.predicate.lowercase() }.forEach { (key, facts) ->
            val values = facts.map { it.value.trim().lowercase() }.distinct()
            if (values.size > 1 && issues.none {
                    it.kind == KuroContextIssueKind.CONFLICTING_FACTS &&
                        it.subjectId == key.first && it.predicate.equals(key.second, ignoreCase = true)
                }
            ) {
                issues += KuroContextIssue(
                    KuroContextIssueKind.CONFLICTING_FACTS,
                    key.first,
                    facts.first().predicate,
                    facts.map { it.value }.distinct()
                )
            }
        }

        val startChapter = (request.chapter - request.lookbackChapters).coerceAtLeast(1)
        val events = project.storyEvents.filter { event ->
            event.branchId == request.branchId &&
                event.chapter in startChapter..request.chapter &&
                event.status in activeStatuses &&
                (event.participantEntityIds.isEmpty() || event.participantEntityIds.any { it in scopedIds })
        }.sortedWith(compareBy<KuroStoryEvent> { it.chapter }.thenBy { it.createdRevision })

        return KuroCompiledStoryContext(
            status = KuroContextStatus.READY,
            chapter = request.chapter,
            branchId = request.branchId,
            entities = scopedEntities,
            facts = matchingFacts,
            relationships = activeRelationships,
            recentEvents = events,
            issues = issues.distinct(),
            includesProposedRecords = request.includeProposed,
            message = "Compiled a chapter- and branch-scoped canon context."
        )
    }
}
