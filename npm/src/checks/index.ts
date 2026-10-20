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
const FILE_CHECKS_MAX_CONSECUTIVE_FAILURES = 3

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

    // Chained rather than setInterval so slow reads cannot pile up concurrent requests.
    const scheduleNextPoll = (delayMs: number) => {
      setTimeout(poll, delayMs)
    }

    const backoffDelayMs = () =>
      Math.min(
        FILE_CHECKS_POLL_INTERVAL_MS * 2 ** consecutiveFailures,
        FILE_CHECKS_MAX_POLL_INTERVAL_MS
      )

    const poll = async () => {
      // An uncaught throw would end the chain and leave the page inert.
      let fileChecksProgress: IFileCheckProgress | Error
      try {
        fileChecksProgress = await getFileChecksProgress()
      } catch (err) {
        fileChecksProgress = err instanceof Error ? err : Error(String(err))
      }

      if (isError(fileChecksProgress)) {
        consecutiveFailures += 1
        // Reloading retries at the cost of re-running the same query, so retry in place first.
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
