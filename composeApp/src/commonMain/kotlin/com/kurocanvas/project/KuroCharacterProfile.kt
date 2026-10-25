package com.kurocanvas.project

enum class KuroProfileFieldStatus {
    KNOWN,
    UNKNOWN,
    CONFLICT
}

data class KuroCharacterProfileField(
    val predicate: String,
    val status: KuroProfileFieldStatus,
    val value: String? = null,
    val alternatives: List<String> = emptyList()
)

data class KuroCharacterProfileSnapshot(
    val entityId: String,
    val entityName: String,
    val chapter: Int,
    val branchId: String,
    val fields: List<KuroCharacterProfileField>
)

/**
 * Read-only projection of temporal canon for character design and continuity checks.
 * It never supplies defaults for unknown fields and never chooses between conflicting values.
 */
object KuroCharacterProfileResolver {
    val standardPredicates: List<String> = listOf(
        "Age",
        "Height",
        "Hair color",
        "Hairstyle",
        "Eye color",
        "Body type",
        "Clothing",
        "Status",
        "Scars",
        "Posture"
    )

    fun resolve(
        project: KuroProjectState,
        entityId: String,
        chapter: Int,
        branchId: String = "main",
        predicates: List<String> = standardPredicates
    ): KuroCharacterProfileSnapshot? {
        if (chapter < 1 || branchId.isBlank()) return null
        val entity = project.storyEntities.firstOrNull { it.id == entityId } ?: return null
        val fields = predicates.map { predicate ->
            when (val resolution = project.resolveCanonFact(entityId, predicate, chapter, branchId)) {
                KuroCanonResolution.Unknown -> KuroCharacterProfileField(
                    predicate = predicate,
                    status = KuroProfileFieldStatus.UNKNOWN
                )
                is KuroCanonResolution.Known -> KuroCharacterProfileField(
                    predicate = predicate,
                    status = KuroProfileFieldStatus.KNOWN,
                    value = resolution.fact.value
                )
                is KuroCanonResolution.Conflict -> KuroCharacterProfileField(
                    predicate = predicate,
                    status = KuroProfileFieldStatus.CONFLICT,
                    alternatives = resolution.facts.map { it.value }.distinct()
                )
            }
        }
        return KuroCharacterProfileSnapshot(entity.id, entity.name, chapter, branchId, fields)
    }
}
