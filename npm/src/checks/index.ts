import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { redirectToChecksResults } from "./redirect-to-checks-results"
import {
  continueTransfer,
  getDraftMetadataValidationProgress,
  getFileChecksProgress,
  getTransferProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"

const FILE_CHECKS_POLL_INTERVAL_MS = 10000
const DRAFT_METADATA_POLL_INTERVAL_MS = 5000
const MAX_POLL_INTERVAL_MS = 120000
const MAX_CONSECUTIVE_POLL_FAILURES = 3

interface IChecksPollOptions<T> {
  intervalMs: number
  getProgress: () => Promise<T | Error>
  isComplete: (progress: T) => boolean
  shouldReload?: (progress: T) => boolean
}

// Polls until the checks complete, then redirects to the checks results page.
const pollChecksProgress = <T>({
  intervalMs,
  getProgress,
  isComplete,
  shouldReload = () => false
}: IChecksPollOptions<T>): void => {
  let consecutiveFailures = 0

  // Chained rather than setInterval so slow reads cannot pile up concurrent requests.
  const scheduleNextPoll = (delayMs: number) => {
    setTimeout(poll, delayMs)
  }

  const backoffDelayMs = () =>
    Math.min(intervalMs * 2 ** consecutiveFailures, MAX_POLL_INTERVAL_MS)

  // A single failed read used to stop polling for good, leaving the page stuck after the checks finished.
  const poll = async () => {
    // An uncaught throw would end the chain and leave the page inert.
    let progress: T | Error
    try {
      progress = await getProgress()
    } catch (err) {
      progress = err instanceof Error ? err : Error(String(err))
    }

    if (isError(progress)) {
      consecutiveFailures += 1
      // Reloading retries at the cost of re-running the same query, so retry in place first.
      if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
        window.location.reload()
        return
      }
      scheduleNextPoll(backoffDelayMs())
      return
    }

    consecutiveFailures = 0

    if (shouldReload(progress)) {
      window.location.reload()
      return
    }

    if (isComplete(progress)) {
      redirectToChecksResults()
      return
    }

    scheduleNextPoll(intervalMs)
  }

  scheduleNextPoll(intervalMs)
}

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
  ) => void = (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => {
    if (isJudgmentUser) {
      this.checkJudgmentTransferProgress(goToNextPage)
      return
    }

    pollChecksProgress({
      intervalMs: FILE_CHECKS_POLL_INTERVAL_MS,
      getProgress: getFileChecksProgress,
      shouldReload: (progress) => Boolean(progress.backendChecksFailed),
      isComplete: haveFileChecksCompleted
    })
  }

  updateDraftMetadataValidationProgress: () => void = () => {
    pollChecksProgress({
      intervalMs: DRAFT_METADATA_POLL_INTERVAL_MS,
      getProgress: getDraftMetadataValidationProgress,
      isComplete: hasDraftMetadataValidationCompleted
    })
  }
}
