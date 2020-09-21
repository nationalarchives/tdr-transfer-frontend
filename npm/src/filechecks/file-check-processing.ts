import {
  GetFileCheckProgressQueryVariables,
  GetFileCheckProgressQuery,
  GetFileCheckProgress
} from "@nationalarchives/tdr-generated-graphql"
import { FetchResult } from "apollo-boost"
import { GraphqlClient } from "../graphql"

export interface IFileCheckProcessed {
  antivirusProcessed: number
  checksumProcessed: number
  ffidProcessed: number
  totalFiles: number
}

export const getConsignmentId: () => string = () => {
  const consignmentIdElement: HTMLInputElement | null = document.querySelector(
    "#consignmentId"
  )
  if (!consignmentIdElement) {
    throw Error("No consignment provided")
  }
  return consignmentIdElement.value
}

export const getConsignmentData: (
  client: GraphqlClient,
  callback: (fileCheckProcessed: IFileCheckProcessed | null) => void
) => void = (client, callback) => {
  const consignmentId = getConsignmentId()
  const variables: GetFileCheckProgressQueryVariables = {
    consignmentId
  }
  const resultPromise: Promise<FetchResult<
    GetFileCheckProgressQuery
  >> = client.mutation(GetFileCheckProgress, variables)

  resultPromise
    .then(result => {
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
          callback({
            antivirusProcessed,
            checksumProcessed,
            ffidProcessed,
            totalFiles
          })
        } else {
          console.log(
            `No progress metadata found for consignment ${consignmentId}`
          )
          callback(null)
        }
      }
    })
    .catch(() => callback(null))
}

export const updateProgressBar: (
  processed: number,
  total: number,
  selector: string
) => void = (processed, total, selector) => {
  let progress = 0
  if (total > 0) {
    progress = Math.round((processed / total) * 100)
  }
  const progressBar: HTMLProgressElement | null = document.querySelector(
    selector
  )
  if (progressBar) {
    progressBar.value = progress
  }
}
