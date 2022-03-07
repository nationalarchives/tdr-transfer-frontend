import {
  GetFileCheckProgress,
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
import { FetchResult } from "@apollo/client/core"
import { GraphqlClient } from "../graphql"
import { isError } from "../errorhandling"

export interface IFileCheckProgress {
  antivirusProcessed: number
  checksumProcessed: number
  ffidProcessed: number
  totalFiles: number
}

export const getConsignmentId: () => string | Error = () => {
  const consignmentIdElement: HTMLInputElement | null =
    document.querySelector("#consignmentId")
  if (!consignmentIdElement) {
    return Error("No consignment provided")
  }
  return consignmentIdElement.value
}

export const getFileChecksProgress: (
  client: GraphqlClient
) => Promise<IFileCheckProgress | Error> = async (client) => {
  const consignmentId = getConsignmentId()
  if (!isError(consignmentId)) {
    const variables: GetFileCheckProgressQueryVariables = {
      consignmentId
    }

    const result: FetchResult<GetFileCheckProgressQuery> =
      await client.mutation(GetFileCheckProgress, variables)

    if (!result.data || result.errors) {
      const errorMessage: string = result.errors
        ? result.errors.toString()
        : "no data"
      return Error(`Add files failed: ${errorMessage}`)
    } else {
      const getConsignment = result.data.getConsignment

      if (getConsignment) {
        const fileChecks = getConsignment.fileChecks
        return {
          antivirusProcessed: fileChecks.antivirusProgress.filesProcessed,
          checksumProcessed: fileChecks.checksumProgress.filesProcessed,
          ffidProcessed: fileChecks.ffidProgress.filesProcessed,
          totalFiles: getConsignment.totalFiles
        }
      } else {
        return Error(
          `No progress metadata found for consignment ${consignmentId}`
        )
      }
    }
  } else {
    return consignmentId
  }
}
