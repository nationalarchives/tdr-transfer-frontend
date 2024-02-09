import {
  Consignment,
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
import { isError } from "../errorhandling"

export interface IProgress {}

export interface IFileCheckProgress extends IProgress {
  antivirusProcessed: number
  checksumProcessed: number
  ffidProcessed: number
  totalFiles: number
}

export interface IDraftMetadataValidationProgress extends IProgress {
  progressStatus: string
}

export const getConsignmentId: () => string | Error = () => {
  const consignmentIdElement: HTMLInputElement | null =
    document.querySelector("#consignmentId")
  if (!consignmentIdElement) {
    return Error("No consignment provided")
  }
  return consignmentIdElement.value
}

export const getDraftMetadataValidationProgress: () => Promise<
  IDraftMetadataValidationProgress | Error
> = async () => {
  const progress = await getProgress("draft-metadata-validation-progress")
  if (!isError(progress)) {
    return progress as IDraftMetadataValidationProgress
  } else return progress
}

export const getFileChecksProgress: () => Promise<
  IFileCheckProgress | Error
> = async () => {
  const progress = await getProgress("file-check-progress")
  if (!isError(progress)) {
    const response = progress as Consignment
    if (response) {
      const fileChecks = response.fileChecks
      return {
        antivirusProcessed: fileChecks.antivirusProgress.filesProcessed,
        checksumProcessed: fileChecks.checksumProgress.filesProcessed,
        ffidProcessed: fileChecks.ffidProgress.filesProcessed,
        totalFiles: response.totalFiles
      }
    } else {
      return Error(`No progress metadata found for consignment: ${response}`)
    }
  } else return progress
}

const getProgress: (
  progressEndpoint: string
) => Promise<IProgress | Error> = async (progressEndpoint) => {
  const consignmentId = getConsignmentId()
  if (!isError(consignmentId)) {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!

    const result: Response | Error = await fetch(progressEndpoint, {
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Failed: ${result.statusText}`)
    } else {
      return await result.json()
    }
  } else {
    return Error(`${consignmentId}`)
  }
}
