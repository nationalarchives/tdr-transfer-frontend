import {
  GetFileCheckProgress,
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
import { FetchResult } from "@apollo/client/core"
import { GraphqlClient } from "../graphql"

export interface IFileCheckProgress {
  antivirusProcessed: number
  checksumProcessed: number
  ffidProcessed: number
  totalFiles: number
}

export const getConsignmentId: () => string = () => {
  const consignmentIdElement: HTMLInputElement | null =
    document.querySelector("#consignmentId")
  if (!consignmentIdElement) {
    throw Error("No consignment provided")
  }
  return consignmentIdElement.value
}

export const getFileChecksProgress: (
  client: GraphqlClient
) => Promise<IFileCheckProgress> = async (client) => {
  const consignmentId = getConsignmentId()
  const variables: GetFileCheckProgressQueryVariables = {
    consignmentId
  }

  const result: FetchResult<GetFileCheckProgressQuery> = await client.mutation(
    GetFileCheckProgress,
    variables
  )

  if (!result.data || result.errors) {
    const errorMessage: string = result.errors
      ? result.errors.toString()
      : "no data"
    throw Error(`Add files failed: ${errorMessage}`)
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
      throw Error(`No progress metadata found for consignment ${consignmentId}`)
    }
  }
}
