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
 * Resolves file sizes that are reported as 0 due to the Windows long file path
 * limitation (paths > 260 characters). The browser File API reports size as 0
 * for such files even when the registry LongPathsEnabled key is set, because
 * the browser executable may not declare longPathAware in its manifest.
 *
 * The same bug also causes file.slice() to return empty blobs, which means the
 * checksum computed by the file-information library is also incorrect (it hashes
 * empty content). This function re-reads the file via stream to compute both
 * the correct size and SHA-256 checksum.
 */
async function resolveFileSizeAndChecksumByStreaming(
  file: File
): Promise<{ size: number; checksum: string }> {
  let size = 0
  const reader = file.stream().getReader()
  const chunks: Uint8Array[] = []
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.byteLength
    chunks.push(value)
  }

  // Compute SHA-256 checksum from the streamed content
  const combined = new Uint8Array(size)
  let offset = 0
  for (const chunk of chunks) {
    combined.set(chunk, offset)
    offset += chunk.byteLength
  }
  const hashBuffer = await crypto.subtle.digest("SHA-256", combined)
  const hashArray = new Uint8Array(hashBuffer)
  const checksum = Array.from(hashArray)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")

  return { size, checksum }
}

// SHA-256 hash of empty content
const EMPTY_FILE_CHECKSUM =
  "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

async function resolveMetadataFileSizes(
  metadata: IFileMetadata[]
): Promise<IFileMetadata[]> {
  const resolved: IFileMetadata[] = []
  for (const entry of metadata) {
    if (entry.size === 0 && entry.file.name !== "") {
      const { size, checksum } = await resolveFileSizeAndChecksumByStreaming(
        entry.file
      )
      if (size === 0 && entry.checksum === EMPTY_FILE_CHECKSUM) {
        // Genuinely empty file — stream confirmed 0 bytes and the original
        // checksum matches the SHA-256 of empty content. Keep as-is.
        resolved.push(entry)
      } else if (size === 0) {
        // Stream returned 0 bytes but the original checksum doesn't match
        // empty content — the browser cannot read the file, likely due to
        // Windows long path limitation without OS-level support.
        // Use -1 to signal an unreadable file to the server.
        resolved.push({ ...entry, size: -1 })
      } else {
        resolved.push({ ...entry, size, checksum })
      }
    } else {
      resolved.push(entry)
    }
  }
  return resolved
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
        // Resolve any 0-byte sizes caused by Windows long path limitation
        const metadata = await resolveMetadataFileSizes(metadataOrError)

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
