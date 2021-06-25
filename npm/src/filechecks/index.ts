import { GraphqlClient } from "../graphql"
import {
  getConsignmentData,
  IFileCheckProcessed,
  getConsignmentId
} from "./file-check-processing"
export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  dataCallback: (fileChecksProcessed: IFileCheckProcessed | null) => void = (
    fileCheckProcessed
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
      }
    }
  }

  updateFileCheckProgress() {
    const interval = setInterval(() => {
      getConsignmentData(this.client, this.dataCallback)
    }, 2000)
  }
}
