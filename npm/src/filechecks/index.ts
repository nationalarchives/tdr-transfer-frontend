import { GraphqlClient } from "../graphql"
import { haveFileChecksCompleted } from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  getFileChecksProgress,
  IFileCheckProgress
} from "./get-file-check-progress"

export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  updateFileCheckProgress: () => void = () => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const fileChecksProgress: IFileCheckProgress | null =
        await getFileChecksProgress(this.client)
      const checksCompleted = haveFileChecksCompleted(fileChecksProgress)
      if (checksCompleted) {
        clearInterval(intervalId)
        displayChecksCompletedBanner()
      }
    }, 2000)
  }
}
