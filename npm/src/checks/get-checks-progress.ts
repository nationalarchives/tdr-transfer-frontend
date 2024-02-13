import {
  Consignment,
  ConsignmentStatus,
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
  progressStatus: string | undefined
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

  const progress = await getProgress("validation-progress")
  if (!isError(progress)) {
    const response = progress as [ConsignmentStatus]
    if (response) {
      const draftMetadataStatus = response.find(
        (e) => e.statusType === "DraftMetadata"
      )

      if (draftMetadataStatus === undefined) {
        return Error("No 'DraftMetadata' status set")
      } else {
        const statusValue = draftMetadataStatus.value
        return statusValue === "Failed"
          ? Error("Draft metadata validation failed")
          : {
              progressStatus: statusValue
            }
      }
    } else {
      return Error(
        `No metadata validation status found for consignment: ${response}`
      )
    }
  } else
    return Error(
      `Failed to retrieve progress for draft metadata validation: ${progress.message}`
    )
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
      return Error(
        `No file checks progress metadata found for consignment: ${response}`
      )
    }
  } else
    return Error(
      `Failed to retrieve progress for file checks: ${progress.message}`
    )
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
      return Error(`Retrieving progress failed: ${result.statusText}`)
    } else {
      return await result.json()
    }
  } else {
    return Error(`Failed to retrieve consignment id: ${consignmentId.message}`)
  }
}
