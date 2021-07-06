import { GraphqlClient } from "../graphql"
import {
  displayChecksCompletedBanner,
  haveFileChecksCompleted
} from "./verify-checks-completed-and-display-banner"
import { getFileChecksInfo, IFileCheckProgress } from "./file-check-processing"

export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  updateFileCheckProgress: () => void = () => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const fileChecksProgress: IFileCheckProgress | null =
        await getFileChecksInfo(this.client)
      const checksCompleted = haveFileChecksCompleted(fileChecksProgress)
      if (checksCompleted) {
        clearInterval(intervalId)
        displayChecksCompletedBanner()
      }
    }, 2000)
  }
}
