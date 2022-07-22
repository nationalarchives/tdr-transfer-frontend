import {
  Consignment,
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
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

export const getFileChecksProgress: () => Promise<
  IFileCheckProgress | Error
> = async () => {
  const consignmentId = getConsignmentId()
  if (!isError(consignmentId)) {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!

    const result: Response | Error = await fetch("file-check-progress", {
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
      return Error(`Add client file metadata failed: ${result.statusText}`)
    } else {
      const response = (await result.json()) as Consignment
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
          `No progress metadata found for consignment ${consignmentId}`
        )
      }
    }
  } else {
    return consignmentId
  }
}
