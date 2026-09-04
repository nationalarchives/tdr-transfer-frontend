import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import {
  IFileMetadata,
  IProgressInformation
} from "@nationalarchives/file-information"
import { S3Upload } from "../s3upload"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"
import {
  IEntry,
  IFileEntry,
  isDirectory,
  isFile
} from "../upload/form/file-types"

export class ClientFileProcessing {
  clientFileMetadataUpload: ClientFileMetadataUpload
  clientFileExtractMetadata: ClientFileExtractMetadata
  s3Upload: S3Upload
  private progressBarElement: HTMLDivElement | null = null
  private progressLabelElement: HTMLDivElement | null = null
  private renderedPercent: number | null = null

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    s3Upload: S3Upload
  ) {
    this.clientFileMetadataUpload = clientFileMetadataUpload
    this.clientFileExtractMetadata = new ClientFileExtractMetadata()
    this.s3Upload = s3Upload
  }

  renderWeightedPercent = (weightedPercent: number) => {
    if (this.renderedPercent === weightedPercent) {
      return
    }

    // The progress callback fires for every file, so the elements are looked up once
    // rather than on each of the thousands of updates a large consignment produces.
    this.progressBarElement ??= document.querySelector(".progress-display")
    this.progressLabelElement ??= document.querySelector("#upload-percentage")

    const progressBarElement = this.progressBarElement
    const progressLabelElement = this.progressLabelElement

    if (progressBarElement && progressLabelElement) {
      this.renderedPercent = weightedPercent
      const stringWeightedPercentage = weightedPercent.toString()
      const stringWeightedPercentageWithSign = `${stringWeightedPercentage}%`
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
    files: IEntry[],
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

      const metadata: IFileMetadata[] | Error =
        await this.clientFileExtractMetadata.extract(
          files.filter((f) => isFile(f)) as IFileEntry[],
          this.metadataProgressCallback
        )

      if (!isError(metadata)) {
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
        return metadata
      }
    } else {
      return uploadResult
    }
  }
}
