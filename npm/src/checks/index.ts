import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  continueTransfer,
  getDraftMetadataValidationProgress,
  getTransferProgress,
  getFileChecksProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"

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
    } else {
      const intervalId: ReturnType<typeof setInterval> = setInterval(
        async () => {
          const fileChecksProgress: IFileCheckProgress | Error =
            await getFileChecksProgress()
          if (!isError(fileChecksProgress)) {
            const checksCompleted = haveFileChecksCompleted(fileChecksProgress)
            if (checksCompleted) {
              clearInterval(intervalId)
              displayChecksCompletedBanner("file-checks")
            }
          } else {
            return fileChecksProgress
          }
        },
        10000
      )
    }
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
