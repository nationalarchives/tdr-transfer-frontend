import { GraphqlClient } from "../graphql"
import {
  getFileChecksInfo,
  IFileCheckProgress
} from "./file-check-processing"

export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  verifyFileChecksCompletedAndDisplayBanner: (fileChecksProgress: IFileCheckProgress | null) => boolean = (
    fileChecksProgress: IFileCheckProgress | null
  ) => {
    if (fileChecksProgress) {
      const {
        antivirusProcessed,
        checksumProcessed,
        ffidProcessed,
        totalFiles
      } = fileChecksProgress

      if (
        antivirusProcessed == totalFiles &&
        checksumProcessed == totalFiles &&
        ffidProcessed == totalFiles
      ) {
        const checkCompleted = true
        const checksCompletedBanner: HTMLDivElement | null =
          document.querySelector("#file-checks-completed-banner")
        if (checksCompletedBanner) {
          checksCompletedBanner.removeAttribute("hidden")
          checksCompletedBanner.focus()
        }
        const continueButton = document.querySelector("#file-checks-continue")
        if (continueButton) {
          continueButton.classList.remove("govuk-button--disabled")
          continueButton.removeAttribute("disabled")
        }
        return checkCompleted
      }
    }
    return false
  }

  updateFileCheckProgress: () => void = () => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const checksCompleted = await getFileChecksInfo(
        this.client,
        this.verifyFileChecksCompletedAndDisplayBanner
      )
      if (checksCompleted) {
        clearInterval(intervalId)
      }
    }, 2000)
  }
}
