import {
  GetFileCheckProgress,
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
import { FetchResult } from "apollo-boost"
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

export const getFileChecksInfo: (
  client: GraphqlClient,
  callback: (fileChecksProgress: IFileCheckProgress | null) => boolean
) => Promise<boolean> = async (client, callback) => {
  const consignmentId = getConsignmentId()
  const variables: GetFileCheckProgressQueryVariables = {
    consignmentId
  }

  try {
    const result: FetchResult<GetFileCheckProgressQuery> =
      await client.mutation(GetFileCheckProgress, variables)
    if (!result.data || result.errors) {
      const errorMessage: string = result.errors
        ? result.errors.toString()
        : "no data"
      throw Error("Add files failed: " + errorMessage)
    } else {
      const getConsignment = result.data.getConsignment
      if (getConsignment) {
        const fileChecks = getConsignment.fileChecks
        const totalFiles = getConsignment.totalFiles
        const antivirusProcessed = fileChecks.antivirusProgress.filesProcessed
        const checksumProcessed = fileChecks.checksumProgress.filesProcessed
        const ffidProcessed = fileChecks.ffidProgress.filesProcessed
        return callback({
          antivirusProcessed,
          checksumProcessed,
          ffidProcessed,
          totalFiles
        })
      } else {
        console.log(
          `No progress metadata found for consignment ${consignmentId}`
        )
        return callback(null)
      }
    }
  } catch (error) {
    return callback(null)
  }
}
