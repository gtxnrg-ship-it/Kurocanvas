package com.kurocanvas.jobs

enum class KuroJobStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class KuroJob(
    val id: String,
    val title: String,
    val status: KuroJobStatus = KuroJobStatus.QUEUED,
    val progressPercent: Int = 0,
    val currentStep: String = "Queued",
    val checkpointId: String? = null,
    val outputObjectIds: List<String> = emptyList(),
    val errorMessage: String? = null,
    val revision: Long = 0L
)

sealed interface KuroJobCommand {
    data object Start : KuroJobCommand
    data class Progress(val percent: Int, val step: String) : KuroJobCommand
    data class Checkpoint(val checkpointId: String, val step: String) : KuroJobCommand
    data object Pause : KuroJobCommand
    data object Resume : KuroJobCommand
    data object RequestCancel : KuroJobCommand
    data object ConfirmCancelled : KuroJobCommand
    data class Complete(val outputObjectIds: List<String> = emptyList()) : KuroJobCommand
    data class Fail(val reason: String) : KuroJobCommand
    data object Retry : KuroJobCommand
}

sealed interface KuroJobTransition {
    data class Accepted(val job: KuroJob) : KuroJobTransition
    data class Rejected(val current: KuroJob, val reason: String) : KuroJobTransition
}

/**
 * Pure state machine for resumable creative jobs.
 *
 * This deliberately performs no I/O and runs no background work by itself. A scheduler/executor
 * must obey these transitions, checkpoint its real engine state, and stop mutations on cancel.
 */
class KuroJobStateMachine {
    fun apply(job: KuroJob, command: KuroJobCommand): KuroJobTransition {
        fun accept(next: KuroJob) = KuroJobTransition.Accepted(
            next.copy(revision = job.revision + 1)
        )
        fun reject(reason: String) = KuroJobTransition.Rejected(job, reason)
        fun nonBlank(value: String) = value.isNotBlank()

        return when (command) {
            KuroJobCommand.Start -> {
                if (job.status != KuroJobStatus.QUEUED) reject("Only queued jobs can start.")
                else accept(job.copy(status = KuroJobStatus.RUNNING, currentStep = "Starting", errorMessage = null))
            }

            is KuroJobCommand.Progress -> {
                if (job.status != KuroJobStatus.RUNNING) reject("Progress can only change while a job is running.")
                else if (command.percent !in 0..99) reject("Progress updates must be between 0 and 99; use Complete for 100.")
                else if (command.percent < job.progressPercent) reject("Progress cannot move backwards within a job attempt.")
                else if (!nonBlank(command.step)) reject("A progress step must have a label.")
                else accept(job.copy(progressPercent = command.percent, currentStep = command.step))
            }

            is KuroJobCommand.Checkpoint -> {
                if (job.status != KuroJobStatus.RUNNING) reject("Only running jobs can create checkpoints.")
                else if (!nonBlank(command.checkpointId)) reject("Checkpoint IDs cannot be blank.")
                else if (!nonBlank(command.step)) reject("A checkpoint step must have a label.")
                else accept(job.copy(checkpointId = command.checkpointId, currentStep = command.step))
            }

            KuroJobCommand.Pause -> {
                if (job.status != KuroJobStatus.RUNNING) reject("Only running jobs can be paused.")
                else accept(job.copy(status = KuroJobStatus.PAUSED, currentStep = "Paused at: ${job.currentStep}"))
            }

            KuroJobCommand.Resume -> {
                if (job.status != KuroJobStatus.PAUSED) reject("Only paused jobs can resume.")
                else if (job.checkpointId == null) reject("This job has no checkpoint to resume from.")
                else accept(job.copy(status = KuroJobStatus.RUNNING, currentStep = "Resuming from checkpoint"))
            }

            KuroJobCommand.RequestCancel -> {
                when (job.status) {
                    KuroJobStatus.QUEUED,
                    KuroJobStatus.PAUSED -> accept(job.copy(status = KuroJobStatus.CANCELLED, currentStep = "Cancelled"))
                    KuroJobStatus.RUNNING -> accept(job.copy(status = KuroJobStatus.CANCELLING, currentStep = "Stopping at a safe checkpoint"))
                    KuroJobStatus.CANCELLING -> reject("Cancellation is already in progress.")
                    KuroJobStatus.COMPLETED -> reject("Completed jobs cannot be cancelled.")
                    KuroJobStatus.FAILED -> reject("Retry or dismiss a failed job instead.")
                    KuroJobStatus.CANCELLED -> reject("This job is already cancelled.")
                }
            }

            KuroJobCommand.ConfirmCancelled -> {
                if (job.status != KuroJobStatus.CANCELLING) reject("Only cancelling jobs can confirm cancellation.")
                else accept(job.copy(status = KuroJobStatus.CANCELLED, currentStep = "Cancelled at safe checkpoint"))
            }

            is KuroJobCommand.Complete -> {
                if (job.status != KuroJobStatus.RUNNING) reject("Only running jobs can complete.")
                else accept(job.copy(status = KuroJobStatus.COMPLETED, progressPercent = 100, currentStep = "Completed", outputObjectIds = command.outputObjectIds, errorMessage = null))
            }

            is KuroJobCommand.Fail -> {
                if (job.status != KuroJobStatus.RUNNING && job.status != KuroJobStatus.CANCELLING) {
                    reject("Only running or cancelling jobs can fail.")
                } else if (!nonBlank(command.reason)) {
                    reject("Failure reason cannot be blank.")
                } else {
                    accept(job.copy(status = KuroJobStatus.FAILED, currentStep = "Failed", errorMessage = command.reason))
                }
            }

            KuroJobCommand.Retry -> {
                if (job.status != KuroJobStatus.FAILED) reject("Only failed jobs can be retried.")
                else accept(job.copy(status = KuroJobStatus.QUEUED, progressPercent = 0, currentStep = "Queued for retry", errorMessage = null, checkpointId = null, outputObjectIds = emptyList()))
            }
        }
    }
}
