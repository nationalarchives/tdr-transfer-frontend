import { haveFileChecksCompleted } from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  getFileChecksProgress,
  IFileCheckProgress
} from "./get-file-check-progress"
import { isError } from "../errorhandling"

export class FileChecks {
  updateFileCheckProgress: (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => void | Error = (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const fileChecksProgress: IFileCheckProgress | Error =
        await getFileChecksProgress()
      if (!isError(fileChecksProgress)) {
        const checksCompleted = haveFileChecksCompleted(fileChecksProgress)
        if (checksCompleted) {
          clearInterval(intervalId)
          isJudgmentUser
            ? goToNextPage("#file-checks-form")
            : displayChecksCompletedBanner()
        }
      } else {
        return fileChecksProgress
      }
    }, 5000)
  }
}
