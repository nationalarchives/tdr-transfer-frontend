import {
  Consignment,
  ConsignmentStatus
} from "@nationalarchives/tdr-generated-graphql"
import { isError } from "../errorhandling"

// Without this a stalled request never settles, so the poll chain stops silently.
const PROGRESS_REQUEST_TIMEOUT_MS = 30000

export interface IProgress {}

export interface IFileCheckProgress extends IProgress {
  isBackendChecksCompleted: boolean
  antivirusProcessed: number
  checksumProcessed: number
  ffidProcessed: number
  totalFiles: number
  backendChecksFailed?: boolean
}

export interface IDraftMetadataValidationProgress extends IProgress {
  progressStatus: string | undefined
}

export interface ITransferProgress extends IProgress {
  fileChecksStatus: string | undefined
  exportStatus: string | undefined
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
        return {
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
    const response = progress as Consignment & { backendChecksFailed?: boolean }
    const fileChecks = response?.fileChecks
    // An unexpected shape would otherwise throw while reading the nested counts.
    if (
      !fileChecks?.antivirusProgress ||
      !fileChecks?.checksumProgress ||
      !fileChecks?.ffidProgress
    ) {
      return Error(
        `No file checks progress metadata found for consignment: ${JSON.stringify(
          response
        )}`
      )
    }

    const isBackendChecksCompleted =
      response.consignmentStatuses?.every(
        ({ value }) => value !== "InProgress"
      ) ?? false

    return {
      isBackendChecksCompleted: isBackendChecksCompleted,
      antivirusProcessed: fileChecks.antivirusProgress.filesProcessed,
      checksumProcessed: fileChecks.checksumProgress.filesProcessed,
      ffidProcessed: fileChecks.ffidProgress.filesProcessed,
      totalFiles: response.totalFiles,
      backendChecksFailed: Boolean(response.backendChecksFailed)
    }
  } else
    return Error(
      `Failed to retrieve progress for file checks: ${progress.message}`
    )
}

export const getTransferProgress: () => Promise<Boolean | Error> = async () => {
  const progress = await getProgress("transfer-progress")
  if (!isError(progress)) {
    const transferProgress = progress as ITransferProgress
    const transferProgressInput: HTMLInputElement | null =
      document.querySelector("#transferProgress")

    // Declared nullable but dereferenced regardless, which threw on pages without the input.
    if (transferProgressInput) {
      transferProgressInput.value = transferProgress.fileChecksStatus
    }

    return (
      transferProgress.fileChecksStatus === "CompletedWithIssues" ||
      (transferProgress.fileChecksStatus === "Completed" &&
        transferProgress.exportStatus !== undefined)
    )
  } else
    return Error(
      `Failed to retrieve progress for file checks: ${progress.message}`
    )
}

export const continueTransfer: () => Promise<String | Error> = async () => {
  const progress = await getProgress("continue-transfer")
  if (isError(progress)) {
    return Error(
      `Failed to retrieve progress for file checks: ${progress.message}`
    )
  }
}

const getProgress: (
  progressEndpoint: string
) => Promise<IProgress | Error> = async (progressEndpoint) => {
  const consignmentId = getConsignmentId()
  if (!isError(consignmentId)) {
    const csrfInput: HTMLInputElement | null = document.querySelector(
      "input[name='csrfToken']"
    )

    if (!csrfInput) {
      return Error("No csrfToken found on the page")
    }

    const controller = new AbortController()
    const timeoutId = setTimeout(
      () => controller.abort(),
      PROGRESS_REQUEST_TIMEOUT_MS
    )

    try {
      const result: Response | Error = await fetch(progressEndpoint, {
        credentials: "include",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Csrf-Token": csrfInput.value,
          "X-Requested-With": "XMLHttpRequest"
        },
        signal: controller.signal
      }).catch((err) => {
        return err?.name === "AbortError"
          ? Error(
              `Retrieving progress timed out after ${PROGRESS_REQUEST_TIMEOUT_MS}ms`
            )
          : Error(err)
      })

      if (isError(result)) {
        return result
      } else if (result.status != 200) {
        return Error(`Retrieving progress failed: ${result.statusText}`)
      } else {
        // A non JSON body would otherwise reject rather than return an Error.
        return await result
          .json()
          .catch(() => Error(`Progress response was not valid JSON`))
      }
    } finally {
      clearTimeout(timeoutId)
    }
  } else {
    return Error(`Failed to retrieve consignment id: ${consignmentId.message}`)
  }
}
