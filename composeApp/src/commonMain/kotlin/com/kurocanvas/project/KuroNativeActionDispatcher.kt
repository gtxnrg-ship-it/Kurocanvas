package com.kurocanvas.project

/**
 * Converts a parsed, allowlisted proposal into a typed native operation.
 * This layer does not persist anything itself; the caller must inspect the result and save only
 * a committed project. The broker remains the final authority for revisions and canon invariants.
 */
object KuroNativeActionDispatcher {
    fun apply(
        current: KuroProjectState,
        proposal: KuroNativeActionProposal,
        broker: KuroOperationBroker = KuroOperationBroker(),
        actor: String = "kuro-approved-by-user"
    ): KuroOperationResult {
        val revision = current.revision
        val operationId = "op-kuro-${proposal.type.name.lowercase()}-${revision + 1}"
        return when (proposal.type) {
            KuroNativeActionType.ADD_STORYBOARD_PANEL -> broker.execute(
                current,
                AddStoryboardPanel(
                    operationId = operationId,
                    projectId = current.projectId,
                    expectedRevision = revision,
                    actor = actor
                )
            )
            KuroNativeActionType.ADD_STORY_ENTITY -> {
                val name = proposal.entityName
                val type = proposal.entityType
                if (name.isNullOrBlank() || type == null) {
                    KuroOperationResult(
                        OperationStatus.INVALID_STORY_ENTITY,
                        current,
                        "The story entity proposal is missing a name or entity type."
                    )
                } else {
                    broker.execute(
                        current,
                        AddStoryEntity(
                            operationId = operationId,
                            projectId = current.projectId,
                            expectedRevision = revision,
                            entity = KuroStoryEntity(
                                id = "entity-${revision + 1}",
                                name = name,
                                type = type
                            ),
                            actor = actor
                        )
                    )
                }
            }
            KuroNativeActionType.ADD_CANON_FACT -> {
                val subjectId = proposal.subjectId
                val predicate = proposal.predicate
                val value = proposal.value
                val fromChapter = proposal.fromChapter
                val branchId = proposal.branchId
                if (subjectId.isNullOrBlank() || predicate.isNullOrBlank() || value.isNullOrBlank() ||
                    fromChapter == null || branchId.isNullOrBlank() ||
                    (proposal.untilChapterExclusive != null && proposal.untilChapterExclusive <= fromChapter)
                ) {
                    KuroOperationResult(
                        OperationStatus.INVALID_CANON_FACT,
                        current,
                        "The canon fact proposal is missing required fields or has an invalid chapter interval."
                    )
                } else {
                    broker.execute(
                        current,
                        AddCanonFact(
                            operationId = operationId,
                            projectId = current.projectId,
                            expectedRevision = revision,
                            fact = KuroCanonFact(
                                id = "fact-${revision + 1}",
                                subjectId = subjectId,
                                predicate = predicate,
                                value = value,
                                branchId = branchId,
                                fromChapter = fromChapter,
                                untilChapterExclusive = proposal.untilChapterExclusive
                            ),
                            actor = actor
                        )
                    )
                }
            }
        }
    }
}
