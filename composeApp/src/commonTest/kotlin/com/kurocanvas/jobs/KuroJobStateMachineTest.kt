package com.kurocanvas.jobs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KuroJobStateMachineTest {
    private val machine = KuroJobStateMachine()

    @Test
    fun pauseCheckpointAndResumePreserveProgress() {
        val queued = KuroJob(id = "job-1", title = "Build scene")
        val started = (machine.apply(queued, KuroJobCommand.Start) as KuroJobTransition.Accepted).job
        val progressing = (machine.apply(started, KuroJobCommand.Progress(35, "Resolve assets")) as KuroJobTransition.Accepted).job
        val checkpointed = (machine.apply(progressing, KuroJobCommand.Checkpoint("cp-1", "Scene plan saved")) as KuroJobTransition.Accepted).job
        val paused = (machine.apply(checkpointed, KuroJobCommand.Pause) as KuroJobTransition.Accepted).job
        val resumed = (machine.apply(paused, KuroJobCommand.Resume) as KuroJobTransition.Accepted).job

        assertEquals(KuroJobStatus.PAUSED, paused.status)
        assertEquals(35, paused.progressPercent)
        assertEquals("cp-1", paused.checkpointId)
        assertEquals(KuroJobStatus.RUNNING, resumed.status)
        assertEquals(35, resumed.progressPercent)
        assertEquals(paused.revision + 1, resumed.revision)
    }

    @Test
    fun runningJobCancellationWaitsForSafeCheckpoint() {
        val queued = KuroJob(id = "job-2", title = "Build scene")
        val started = (machine.apply(queued, KuroJobCommand.Start) as KuroJobTransition.Accepted).job
        val cancelling = (machine.apply(started, KuroJobCommand.RequestCancel) as KuroJobTransition.Accepted).job
        val cancelled = (machine.apply(cancelling, KuroJobCommand.ConfirmCancelled) as KuroJobTransition.Accepted).job

        assertEquals(KuroJobStatus.CANCELLING, cancelling.status)
        assertEquals(KuroJobStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun progressCannotGoBackwardsOrReachOneHundredBeforeCompletion() {
        val started = (machine.apply(KuroJob("job-3", "Build scene"), KuroJobCommand.Start) as KuroJobTransition.Accepted).job
        val progressing = (machine.apply(started, KuroJobCommand.Progress(50, "Build geometry")) as KuroJobTransition.Accepted).job

        assertIs<KuroJobTransition.Rejected>(machine.apply(progressing, KuroJobCommand.Progress(25, "Regress")))
        assertIs<KuroJobTransition.Rejected>(machine.apply(progressing, KuroJobCommand.Progress(100, "Pretend finished")))
        assertEquals(50, progressing.progressPercent)
    }

    @Test
    fun completionRecordsOutputsAndSetsProgressToOneHundred() {
        val started = (machine.apply(KuroJob("job-4", "Build scene"), KuroJobCommand.Start) as KuroJobTransition.Accepted).job
        val done = (machine.apply(started, KuroJobCommand.Complete(listOf("scene-1", "panel-1"))) as KuroJobTransition.Accepted).job

        assertEquals(KuroJobStatus.COMPLETED, done.status)
        assertEquals(100, done.progressPercent)
        assertEquals(listOf("scene-1", "panel-1"), done.outputObjectIds)
        assertIs<KuroJobTransition.Rejected>(machine.apply(done, KuroJobCommand.RequestCancel))
    }

    @Test
    fun failedJobCanBeRetriedWithResetProgress() {
        val started = (machine.apply(KuroJob("job-5", "Build scene"), KuroJobCommand.Start) as KuroJobTransition.Accepted).job
        val progressing = (machine.apply(started, KuroJobCommand.Progress(42, "Build geometry")) as KuroJobTransition.Accepted).job
        val failed = (machine.apply(progressing, KuroJobCommand.Fail("Renderer allocation failed")) as KuroJobTransition.Accepted).job
        val retried = (machine.apply(failed, KuroJobCommand.Retry) as KuroJobTransition.Accepted).job

        assertEquals(KuroJobStatus.FAILED, failed.status)
        assertEquals("Renderer allocation failed", failed.errorMessage)
        assertEquals(KuroJobStatus.QUEUED, retried.status)
        assertEquals(0, retried.progressPercent)
        assertEquals(null, retried.errorMessage)
        assertEquals(null, retried.checkpointId)
    }
}
