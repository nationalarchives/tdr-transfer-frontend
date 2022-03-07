import { GraphqlClient } from "../graphql"
import { haveFileChecksCompleted } from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  getFileChecksProgress,
  IFileCheckProgress
} from "./get-file-check-progress"
import { isError } from "../errorhandling"

export class FileChecks {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  updateFileCheckProgress: (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => void | Error = (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void
  ) => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const fileChecksProgress: IFileCheckProgress | Error =
        await getFileChecksProgress(this.client)
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
    }, 20000)
  }
}
