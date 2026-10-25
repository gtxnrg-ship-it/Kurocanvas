package com.kurocanvas.project

fun KuroProjectState.relationshipsFor(
    entityId: String,
    chapter: Int,
    branchId: String = "main",
    includeProposed: Boolean = false
): List<KuroStoryRelationship> {
    if (chapter < 1) return emptyList()
    return relationships.filter { relation ->
        (relation.fromEntityId == entityId || relation.toEntityId == entityId) &&
            relation.branchId == branchId &&
            (relation.status == KuroCanonFactStatus.CONFIRMED ||
                (includeProposed && relation.status == KuroCanonFactStatus.PROPOSED)) &&
            relation.fromChapter <= chapter &&
            (relation.untilChapterExclusive == null || chapter < relation.untilChapterExclusive)
    }.sortedWith(compareBy<KuroStoryRelationship> { it.fromChapter }.thenBy { it.relationshipType })
}

fun KuroProjectState.eventsInTimeline(
    branchId: String = "main",
    fromChapter: Int = 1,
    untilChapterExclusive: Int? = null,
    includeProposed: Boolean = false
): List<KuroStoryEvent> {
    if (fromChapter < 1 || (untilChapterExclusive != null && untilChapterExclusive <= fromChapter)) {
        return emptyList()
    }
    return storyEvents.filter { event ->
        event.branchId == branchId &&
            event.chapter >= fromChapter &&
            (untilChapterExclusive == null || event.chapter < untilChapterExclusive) &&
            (event.status == KuroCanonFactStatus.CONFIRMED ||
                (includeProposed && event.status == KuroCanonFactStatus.PROPOSED))
    }.sortedWith(compareBy<KuroStoryEvent> { it.chapter }.thenBy { it.createdRevision })
}
