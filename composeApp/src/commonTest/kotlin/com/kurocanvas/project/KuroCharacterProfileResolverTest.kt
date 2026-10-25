package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KuroCharacterProfileResolverTest {
    private val broker = KuroOperationBroker()

    private fun addFact(
        project: KuroProjectState,
        id: String,
        predicate: String,
        value: String,
        from: Int,
        until: Int? = null,
        branch: String = "main"
    ): KuroProjectState = broker.execute(
        project,
        AddCanonFact(
            operationId = "op-$id",
            projectId = project.projectId,
            expectedRevision = project.revision,
            fact = KuroCanonFact(
                id = id,
                subjectId = "mira",
                predicate = predicate,
                value = value,
                branchId = branch,
                fromChapter = from,
                untilChapterExclusive = until
            )
        )
    ).project

    @Test
    fun resolvesCharacterAppearanceAndTimelineAtRequestedChapter() {
        var project = KuroProjectState.empty(projectId = "profile")
        project = broker.execute(
            project,
            AddStoryEntity("entity-mira", project.projectId, project.revision, KuroStoryEntity("mira", "Mira"))
        ).project
        project = addFact(project, "hair-short", "Hairstyle", "Short black bob", 1, 5)
        project = addFact(project, "hair-long", "Hairstyle", "Long black hair", 5)
        project = addFact(project, "age-1", "Age", "17", 1, 10)
        project = addFact(project, "age-2", "Age", "18", 10)

        val early = KuroCharacterProfileResolver.resolve(project, "mira", 3)!!
        val later = KuroCharacterProfileResolver.resolve(project, "mira", 7)!!
        val adult = KuroCharacterProfileResolver.resolve(project, "mira", 12)!!

        assertEquals("Short black bob", early.fields.first { it.predicate == "Hairstyle" }.value)
        assertEquals("Long black hair", later.fields.first { it.predicate == "Hairstyle" }.value)
        assertEquals("17", later.fields.first { it.predicate == "Age" }.value)
        assertEquals("18", adult.fields.first { it.predicate == "Age" }.value)
        assertEquals(KuroProfileFieldStatus.UNKNOWN, later.fields.first { it.predicate == "Height" }.status)
    }

    @Test
    fun reportsConflictsAndDoesNotLeakAlternateTimelineFacts() {
        var project = KuroProjectState.empty(projectId = "profile-branches")
        project = broker.execute(
            project,
            AddStoryEntity("entity-mira", project.projectId, project.revision, KuroStoryEntity("mira", "Mira"))
        ).project
        project = addFact(project, "hair-a", "Hair color", "Black", 1, 6)
        // Simulate an imported legacy/corrupted project containing a contradiction the broker would reject.
        project = project.copy(
            canonFacts = project.canonFacts + KuroCanonFact(
                id = "hair-b",
                subjectId = "mira",
                predicate = "Hair color",
                value = "Silver",
                fromChapter = 3,
                untilChapterExclusive = 8,
                createdRevision = project.revision + 1
            )
        )
        project = addFact(project, "alt-status", "Status", "Dead", 1, branch = "alternate")

        val main = KuroCharacterProfileResolver.resolve(project, "mira", 4)!!
        val alternate = KuroCharacterProfileResolver.resolve(project, "mira", 4, "alternate")!!

        assertEquals(KuroProfileFieldStatus.CONFLICT, main.fields.first { it.predicate == "Hair color" }.status)
        assertEquals(listOf("Black", "Silver"), main.fields.first { it.predicate == "Hair color" }.alternatives)
        assertEquals(KuroProfileFieldStatus.UNKNOWN, main.fields.first { it.predicate == "Status" }.status)
        assertEquals("Dead", alternate.fields.first { it.predicate == "Status" }.value)
    }

    @Test
    fun missingEntityOrInvalidChapterReturnsNoSnapshot() {
        val project = KuroProjectState.empty(projectId = "profile-empty")
        assertNull(KuroCharacterProfileResolver.resolve(project, "missing", 1))
        assertNull(KuroCharacterProfileResolver.resolve(project, "missing", 0))
    }
}
