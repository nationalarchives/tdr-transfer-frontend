import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  getDraftMetadataValidationProgress,
  getFileChecksProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"

export class Checks {
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
            : displayChecksCompletedBanner("file-checks")
        }
      } else {
        return fileChecksProgress
      }
    }, 5000)
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
          displayChecksCompletedBanner("draft-metadata-checks")
        }
      } else {
        clearInterval(intervalId)
        return validationChecksProgress
      }
    }, 5000)
  }
}
