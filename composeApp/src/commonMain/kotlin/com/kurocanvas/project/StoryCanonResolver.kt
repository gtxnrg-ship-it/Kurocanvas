package com.kurocanvas.project

sealed interface KuroCanonResolution {
    data class Known(val fact: KuroCanonFact) : KuroCanonResolution
    data class Conflict(val facts: List<KuroCanonFact>) : KuroCanonResolution
    data object Unknown : KuroCanonResolution
}

/**
 * Resolves confirmed facts for one story branch and chapter.
 * Chapter ranges are start-inclusive and end-exclusive. Branches are intentionally isolated:
 * a branch must have its own confirmed facts rather than silently inheriting facts from main.
 */
fun KuroProjectState.resolveCanonFact(
    subjectId: String,
    predicate: String,
    chapter: Int,
    branchId: String = "main"
): KuroCanonResolution {
    if (chapter < 1) return KuroCanonResolution.Unknown
    val applicable = canonFacts.filter { fact ->
        fact.subjectId == subjectId &&
            fact.predicate.equals(predicate.trim(), ignoreCase = true) &&
            fact.branchId == branchId &&
            fact.status == KuroCanonFactStatus.CONFIRMED &&
            fact.fromChapter <= chapter &&
            (fact.untilChapterExclusive == null || chapter < fact.untilChapterExclusive)
    }
    if (applicable.isEmpty()) return KuroCanonResolution.Unknown
    if (applicable.map { it.value.trim().lowercase() }.distinct().size > 1) {
        return KuroCanonResolution.Conflict(applicable)
    }
    return KuroCanonResolution.Known(applicable.maxBy { it.createdRevision })
}
