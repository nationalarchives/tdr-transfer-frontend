import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  continueTransfer,
  getDraftMetadataValidationProgress, getExportProgress,
  getFileChecksProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"

export class Checks {
  updateFileCheckProgress: (isJudgmentUser: boolean, goToNextPage: (formId: string) => void) => Promise<void> = async (
      isJudgmentUser: boolean,
      goToNextPage: (formId: string) => void
  ) => {
    continueTransfer()
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
        const isCompleted: Boolean | Error = await getExportProgress()
        if (!isError(isCompleted)) {
          const v = isCompleted as boolean
          if (v) {
            clearInterval(intervalId)
            goToNextPage("#file-checks-form")
          }
        } else {
          return isCompleted
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
