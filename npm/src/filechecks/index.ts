import { GraphqlClient } from "../graphql"
import {
  getConsignmentData,
  updateProgressBar,
  updateProgressSpan,
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

      updateProgressBar(
        antivirusProcessed,
        totalFiles,
        "#av-metadata-progress-bar"
      )
      updateProgressBar(checksumProcessed, totalFiles, "#checksum-progress-bar")
      updateProgressBar(ffidProcessed, totalFiles, "#ffid-progress-bar")

      updateProgressSpan(
        antivirusProcessed,
        totalFiles,
        "#av-status-screen-reader"
      )
      updateProgressSpan(
        checksumProcessed,
        totalFiles,
        "#checksum-status-screen-reader"
      )
      updateProgressSpan(
        ffidProcessed,
        totalFiles,
        "#ffid-status-screen-reader"
      )

      if (
        antivirusProcessed == totalFiles &&
        checksumProcessed == totalFiles &&
        ffidProcessed == totalFiles
      ) {
        const location = `${
          window.location.origin
        }/consignment/${getConsignmentId()}/records-results`
        window.location.href = location
      }
    }
  }

  updateFileCheckProgress() {
    const interval = setInterval(() => {
      getConsignmentData(this.client, this.dataCallback)
    }, 2000)
  }
}
