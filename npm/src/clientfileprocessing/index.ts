import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import { handleUploadError } from "../errorhandling"
import {
  IFileMetadata,
  IFileWithPath,
  IProgressInformation
} from "@nationalarchives/file-information"
import { ITdrFile, S3Upload } from "../s3upload"
import { FileUploadInfo } from "../upload/upload-form"

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

  metadataProgressCallback(progressInformation: IProgressInformation) {
    const weightedPercent = progressInformation.percentageProcessed / 2

    const fileUpload: HTMLDivElement | null = document.querySelector(
      "#file-upload"
    )
    const progressBar: HTMLDivElement | null = document.querySelector(
      "#progress-bar"
    )
    const progressBarElement: HTMLDivElement | null = document.querySelector(
      ".progress-display"
    )

    if (fileUpload && progressBar) {
      fileUpload.classList.add("hide")
      progressBar.classList.remove("hide")
    }

    if (progressBarElement) {
      progressBarElement.setAttribute("value", weightedPercent.toString())
    }
  }

  s3ProgressCallback(progressInformation: IProgressInformation) {
    const weightedPercent = 50 + progressInformation.percentageProcessed / 2

    const progressBarElement: HTMLDivElement | null = document.querySelector(
      ".progress-display"
    )

    if (progressBarElement) {
      progressBarElement.setAttribute("value", weightedPercent.toString())
    }
  }

  async processClientFiles(
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo,
    stage: string
  ): Promise<void> {
    try {
      const fileIds: string[] = await this.clientFileMetadataUpload.saveFileInformation(
        files.length,
        uploadFilesInfo
      )
      const metadata: IFileMetadata[] = await this.clientFileExtractMetadata.extract(
        files,
        this.metadataProgressCallback
      )
      const tdrFiles = await this.clientFileMetadataUpload.saveClientFileMetadata(
        fileIds,
        metadata
      )
      await this.s3Upload.uploadToS3(
        uploadFilesInfo.consignmentId,
        tdrFiles,
        this.s3ProgressCallback,
        stage
      )
    } catch (e) {
      handleUploadError(e, "Processing client files failed")
    }
  }
}
