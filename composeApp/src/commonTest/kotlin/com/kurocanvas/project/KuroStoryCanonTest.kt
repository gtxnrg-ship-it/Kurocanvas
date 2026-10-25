package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KuroStoryCanonTest {
    private val broker = KuroOperationBroker()

    private fun addCharacter(project: KuroProjectState): KuroProjectState {
        val result = broker.execute(
            project,
            AddStoryEntity(
                "entity-op-1",
                project.projectId,
                project.revision,
                KuroStoryEntity("char-1", "Mira")
            )
        )
        assertEquals(OperationStatus.COMMITTED, result.status)
        return result.project
    }

    @Test
    fun storyEntityAndTimeScopedFactCommitAndResolve() {
        val initial = KuroProjectState.empty(projectId = "canon-test")
        val withCharacter = addCharacter(initial)
        val fact = KuroCanonFact(
            id = "fact-hair-1",
            subjectId = "char-1",
            predicate = "Hair color",
            value = "Black",
            fromChapter = 1,
            untilChapterExclusive = 5
        )
        val result = broker.execute(
            withCharacter,
            AddCanonFact("fact-op-1", withCharacter.projectId, withCharacter.revision, fact)
        )
        assertEquals(OperationStatus.COMMITTED, result.status)
        assertIs<KuroCanonResolution.Known>(result.project.resolveCanonFact("char-1", "hair color", 3))
        assertEquals(KuroCanonResolution.Unknown, result.project.resolveCanonFact("char-1", "hair color", 5))
    }

    @Test
    fun confirmedContradictionOverlappingInTimeIsRejected() {
        val withCharacter = addCharacter(KuroProjectState.empty(projectId = "canon-test"))
        val first = broker.execute(
            withCharacter,
            AddCanonFact(
                "fact-op-1", withCharacter.projectId, withCharacter.revision,
                KuroCanonFact("fact-1", "char-1", "Age", "18", fromChapter = 2, untilChapterExclusive = 7)
            )
        )
        val conflict = broker.execute(
            first.project,
            AddCanonFact(
                "fact-op-2", first.project.projectId, first.project.revision,
                KuroCanonFact("fact-2", "char-1", "Age", "19", fromChapter = 4, untilChapterExclusive = 9)
            )
        )
        assertEquals(OperationStatus.COMMITTED, first.status)
        assertEquals(OperationStatus.CANON_CONTRADICTION, conflict.status)
        assertEquals(first.project, conflict.project)
    }

    @Test
    fun differentBranchesCanHaveDifferentFacts() {
        val withCharacter = addCharacter(KuroProjectState.empty(projectId = "canon-test"))
        val first = broker.execute(
            withCharacter,
            AddCanonFact(
                "fact-main-op", withCharacter.projectId, withCharacter.revision,
                KuroCanonFact("fact-main", "char-1", "Status", "Alive", branchId = "main", fromChapter = 1)
            )
        )
        val second = broker.execute(
            first.project,
            AddCanonFact(
                "fact-alt-op", first.project.projectId, first.project.revision,
                KuroCanonFact("fact-alt", "char-1", "Status", "Dead", branchId = "alternate", fromChapter = 1)
            )
        )
        assertEquals(OperationStatus.COMMITTED, second.status)
        assertIs<KuroCanonResolution.Known>(second.project.resolveCanonFact("char-1", "Status", 2, "main"))
        val alternate = second.project.resolveCanonFact("char-1", "Status", 2, "alternate")
        assertIs<KuroCanonResolution.Known>(alternate)
        assertEquals("Dead", alternate.fact.value)
    }

    @Test
    fun entityCannotBeUndoneWhileCanonFactsDependOnIt() {
        val initial = KuroProjectState.empty(projectId = "canon-test")
        val withCharacter = addCharacter(initial)
        val withFact = broker.execute(
            withCharacter,
            AddCanonFact(
                "fact-op-1", withCharacter.projectId, withCharacter.revision,
                KuroCanonFact("fact-1", "char-1", "Age", "18", fromChapter = 1)
            )
        ).project
        val undo = broker.execute(
            withFact,
            UndoAddedStoryItem("undo-entity", withFact.projectId, withFact.revision, "entity-op-1")
        )
        assertEquals(OperationStatus.DEPENDENCY_EXISTS, undo.status)
        assertEquals(withFact, undo.project)
    }
}
