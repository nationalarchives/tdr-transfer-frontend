import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import {
  IFileMetadata,
  IFileWithPath,
  IProgressInformation
} from "@nationalarchives/file-information"
import { S3Upload } from "../s3upload"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"
import {
  IEntryWithPath,
  isDirectory,
  isFile
} from "../upload/form/get-files-from-drag-event"

/**
 * Checks files whose size is reported as 0. Attempts to read the file to
 * distinguish genuinely empty files from files that are unreadable due to
 * the Windows long file path limitation (paths > 260 characters).
 *
 * - If the file can be read and is truly 0 bytes: left unchanged.
 * - If the file cannot be read (stream/arrayBuffer throws): marked as size -1
 *   so the server can identify affected files and inform the user.
 */
async function flagUnreadableFiles(
  metadata: IFileMetadata[]
): Promise<IFileMetadata[]> {
  const resolved: IFileMetadata[] = []
  for (const entry of metadata) {
    if (entry.size === 0 && entry.file.name !== "") {
      const readable = await canReadFile(entry.file)
      if (!readable) {
        resolved.push({ ...entry, size: -1 })
      } else {
        resolved.push(entry)
      }
    } else {
      resolved.push(entry)
    }
  }
  return resolved
}

async function canReadFile(file: File): Promise<boolean> {
  try {
    await file.arrayBuffer()
    return true
  } catch {
    return false
  }
}

export class ClientFileProcessing {
  clientFileMetadataUpload: ClientFileMetadataUpload
  clientFileExtractMetadata: ClientFileExtractMetadata
  s3Upload: S3Upload

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    s3Upload: S3Upload
  ) {
    this.clientFileMetadataUpload = clientFileMetadataUpload
    this.clientFileExtractMetadata = new ClientFileExtractMetadata()
    this.s3Upload = s3Upload
  }

  renderWeightedPercent = (weightedPercent: number) => {
    const progressBarElement: HTMLDivElement | null =
      document.querySelector(".progress-display")
    const progressLabelElement: HTMLDivElement | null =
      document.querySelector("#upload-percentage")

    const currentPercentageWithSign = progressLabelElement?.innerText
    const stringWeightedPercentage = weightedPercent.toString()
    const stringWeightedPercentageWithSign = `${stringWeightedPercentage}%`

    if (
      progressBarElement &&
      progressLabelElement &&
      stringWeightedPercentageWithSign !== currentPercentageWithSign
    ) {
      progressLabelElement.innerText = stringWeightedPercentageWithSign
      progressBarElement.style.width = stringWeightedPercentageWithSign
      progressBarElement.setAttribute("aria-valuenow", stringWeightedPercentage)
    }
  }

  metadataProgressCallback = (progressInformation: IProgressInformation) => {
    const weightedPercent = Math.floor(
      progressInformation.percentageProcessed / 2
    )
    this.renderWeightedPercent(weightedPercent)
  }

  s3ProgressCallback = (progressInformation: IProgressInformation) => {
    const weightedPercent =
      50 + Math.floor(progressInformation.percentageProcessed / 2)
    this.renderWeightedPercent(weightedPercent)
  }

  async processClientFiles(
    files: IEntryWithPath[],
    uploadFilesInfo: FileUploadInfo,
    stage: string,
    userId: string | undefined
  ): Promise<void | Error> {
    const uploadResult =
      await this.clientFileMetadataUpload.startUpload(uploadFilesInfo)
    if (!isError(uploadResult)) {
      const emptyFolders = files
        .filter((f) => isDirectory(f))
        .map((f) => f.path)

      const metadataOrError: IFileMetadata[] | Error =
        await this.clientFileExtractMetadata.extract(
          files.filter((f) => isFile(f)) as IFileWithPath[],
          this.metadataProgressCallback
        )

      if (!isError(metadataOrError)) {
        // Flag files affected by Windows long path limitation (size: -1)
        const metadata = await flagUnreadableFiles(metadataOrError)

        const tdrFiles =
          await this.clientFileMetadataUpload.saveClientFileMetadata(
            uploadFilesInfo.consignmentId,
            metadata,
            emptyFolders
          )
        if (!isError(tdrFiles)) {
          const uploadResult = await this.s3Upload.uploadToS3(
            uploadFilesInfo.consignmentId,
            userId,
            tdrFiles,
            this.s3ProgressCallback,
            stage
          )
          if (isError(uploadResult)) {
            return uploadResult
          }
        } else {
          return tdrFiles
        }
      } else {
        return metadataOrError
      }
    } else {
      return uploadResult
    }
  }
}
