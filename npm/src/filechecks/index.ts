import { GraphqlClient } from "../graphql"
import {
  getConsignmentData,
  IFileCheckProcessed
} from "./file-check-processing"

export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  dataCallback: (fileCheckProcessed: IFileCheckProcessed | null) => boolean = (
    fileCheckProcessed: IFileCheckProcessed | null
  ) => {
    if (fileCheckProcessed) {
      const {
        antivirusProcessed,
        checksumProcessed,
        ffidProcessed,
        totalFiles
      } = fileCheckProcessed

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
    const interval: ReturnType<typeof setInterval> = setInterval(async () => {
      const checksCompleted = await getConsignmentData(
        this.client,
        this.dataCallback
      )
      if (checksCompleted) {
        clearInterval(interval)
      }
    }, 2000)
  }
}
