import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { redirectToChecksResults } from "./redirect-to-checks-results"
import {
  continueTransfer,
  getDraftMetadataValidationProgress,
  getFileChecksProgress,
  getTransferProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"

const FILE_CHECKS_POLL_INTERVAL_MS = 10000
const FILE_CHECKS_MAX_POLL_INTERVAL_MS = 120000
const FILE_CHECKS_MAX_CONSECUTIVE_FAILURES = 5

export class Checks {
  checkJudgmentTransferProgress: (
    goToNextPage: (formId: string) => void
  ) => void | Error = (goToNextPage: (formId: string) => void) => {
    continueTransfer()
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const isCompleted: Boolean | Error = await getTransferProgress()
      if (!isError(isCompleted)) {
        if (isCompleted) {
          clearInterval(intervalId)
          goToNextPage("#file-checks-form")
        }
      } else {
        return isCompleted
      }
    }, 5000)
  }

  updateFileCheckProgress: (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => void | Error = (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => {
    if (isJudgmentUser) {
      this.checkJudgmentTransferProgress(goToNextPage)
      return
    }

    let consecutiveFailures = 0

    // The next poll is only scheduled once the previous one has settled. setInterval fires
    // regardless of whether the last request has returned, so a slow progress response made
    // the browser pile up concurrent requests against an already struggling endpoint.
    const scheduleNextPoll = (delayMs: number) => {
      setTimeout(poll, delayMs)
    }

    const backoffDelayMs = () =>
      Math.min(
        FILE_CHECKS_POLL_INTERVAL_MS * 2 ** consecutiveFailures,
        FILE_CHECKS_MAX_POLL_INTERVAL_MS
      )

    const poll = async () => {
      // Any throw here would end the chain for good and leave the page inert, so unexpected
      // errors are funnelled into the same retry path as a failed read.
      let fileChecksProgress: IFileCheckProgress | Error
      try {
        fileChecksProgress = await getFileChecksProgress()
      } catch (err) {
        fileChecksProgress = err instanceof Error ? err : Error(String(err))
      }

      if (isError(fileChecksProgress)) {
        consecutiveFailures += 1
        // A single failed read used to stop polling for good. Retry with backoff first and
        // only fall back to reloading once the endpoint looks persistently unavailable.
        if (consecutiveFailures >= FILE_CHECKS_MAX_CONSECUTIVE_FAILURES) {
          window.location.reload()
          return
        }
        scheduleNextPoll(backoffDelayMs())
        return
      }

      consecutiveFailures = 0

      if (fileChecksProgress.backendChecksFailed) {
        window.location.reload()
        return
      }

      if (haveFileChecksCompleted(fileChecksProgress)) {
        redirectToChecksResults()
        return
      }

      scheduleNextPoll(FILE_CHECKS_POLL_INTERVAL_MS)
    }

    scheduleNextPoll(FILE_CHECKS_POLL_INTERVAL_MS)
  }

  updateDraftMetadataValidationProgress: () => void | Error = () => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const validationChecksProgress: IDraftMetadataValidationProgress | Error =
        await getDraftMetadataValidationProgress()
      if (!isError(validationChecksProgress)) {
        const checksCompleted = hasDraftMetadataValidationCompleted(
          validationChecksProgress
        )
        if (checksCompleted) {
          clearInterval(intervalId)
          redirectToChecksResults()
        }
      } else {
        clearInterval(intervalId)
        return validationChecksProgress
      }
    }, 5000)
  }
}
