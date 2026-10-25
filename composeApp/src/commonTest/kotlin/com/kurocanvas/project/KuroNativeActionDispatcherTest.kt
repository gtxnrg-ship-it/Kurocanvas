package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KuroNativeActionDispatcherTest {
    @Test
    fun appliesOnlyTypedApprovedActionsThroughTheBroker() {
        val broker = KuroOperationBroker()
        var project = KuroProjectState.empty(projectId = "native-actions")

        val panel = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(KuroNativeActionType.ADD_STORYBOARD_PANEL, "Add panel"),
            broker
        )
        assertEquals(OperationStatus.COMMITTED, panel.status)
        project = panel.project

        val entity = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                type = KuroNativeActionType.ADD_STORY_ENTITY,
                summary = "Create Mira",
                entityName = "Mira",
                entityType = KuroStoryEntityType.CHARACTER
            ),
            broker
        )
        assertEquals(OperationStatus.COMMITTED, entity.status)
        project = entity.project
        val miraId = project.storyEntities.single().id

        val fact = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                type = KuroNativeActionType.ADD_CANON_FACT,
                summary = "Set hair color",
                subjectId = miraId,
                predicate = "Hair color",
                value = "Black",
                fromChapter = 1,
                branchId = "main"
            ),
            broker
        )
        assertEquals(OperationStatus.COMMITTED, fact.status)
        assertEquals("Black", fact.project.canonFacts.single().value)
        assertEquals(3L, fact.project.revision)
        assertTrue(fact.project.auditLog.any { it.actor == "kuro-approved-by-user" })
    }

    @Test
    fun rejectsCanonContradictionsAndUnknownSubjectsWithoutMutatingProject() {
        val broker = KuroOperationBroker()
        var project = KuroProjectState.empty(projectId = "native-actions-conflict")
        val entity = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_STORY_ENTITY,
                "Create Mira",
                entityName = "Mira",
                entityType = KuroStoryEntityType.CHARACTER
            ),
            broker
        )
        project = entity.project
        val id = project.storyEntities.single().id
        val firstFact = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_CANON_FACT,
                "Black hair",
                subjectId = id,
                predicate = "Hair color",
                value = "Black",
                fromChapter = 1,
                untilChapterExclusive = 10,
                branchId = "main"
            ),
            broker
        )
        project = firstFact.project

        val contradiction = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_CANON_FACT,
                "Silver hair",
                subjectId = id,
                predicate = "Hair color",
                value = "Silver",
                fromChapter = 4,
                branchId = "main"
            ),
            broker
        )
        val missingSubject = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_CANON_FACT,
                "Unknown subject",
                subjectId = "not-in-project",
                predicate = "Status",
                value = "Alive",
                fromChapter = 1,
                branchId = "main"
            ),
            broker
        )

        assertEquals(OperationStatus.CANON_CONTRADICTION, contradiction.status)
        assertEquals(OperationStatus.MISSING_CANON_SUBJECT, missingSubject.status)
        assertEquals(project, contradiction.project)
        assertEquals(project, missingSubject.project)
    }

    @Test
    fun rejectsIncompleteProposals() {
        val project = KuroProjectState.empty(projectId = "native-actions-invalid")
        val missingEntityName = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_STORY_ENTITY,
                "Missing name",
                entityType = KuroStoryEntityType.CHARACTER
            )
        )
        val invalidRange = KuroNativeActionDispatcher.apply(
            project,
            KuroNativeActionProposal(
                KuroNativeActionType.ADD_CANON_FACT,
                "Invalid range",
                subjectId = "mira",
                predicate = "Age",
                value = "18",
                fromChapter = 3,
                untilChapterExclusive = 2,
                branchId = "main"
            )
        )

        assertEquals(OperationStatus.INVALID_STORY_ENTITY, missingEntityName.status)
        assertEquals(OperationStatus.INVALID_CANON_FACT, invalidRange.status)
        assertEquals(0L, project.revision)
    }
}
