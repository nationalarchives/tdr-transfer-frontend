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

  renderWeightedPercent = (weightedPercent: number) => {
    const progressBarElement: HTMLDivElement | null =
      document.querySelector(".progress-display")
    const progressLabelElement: HTMLDivElement | null =
      document.querySelector(".progress-label")

    if (progressBarElement && progressLabelElement) {
      const stringWeightedPercentage = weightedPercent.toString()
      progressBarElement.setAttribute("value", stringWeightedPercentage)
      progressLabelElement.innerText = `Uploading records ${stringWeightedPercentage}%`
    }
  }

  metadataProgressCallback = (progressInformation: IProgressInformation) => {
    const weightedPercent = progressInformation.percentageProcessed / 2

    const fileUpload: HTMLDivElement | null =
      document.querySelector("#file-upload")
    const progressBar: HTMLDivElement | null =
      document.querySelector("#progress-bar")
    const progressBarElement: HTMLDivElement | null =
      document.querySelector(".progress-display")
    const progressLabelElement: HTMLDivElement | null =
      document.querySelector(".progress-label")

    if (fileUpload && progressBar) {
      fileUpload.setAttribute("hidden", "true")
      progressBar.removeAttribute("hidden")
    }

    this.renderWeightedPercent(weightedPercent)
  }

  s3ProgressCallback = (progressInformation: IProgressInformation) => {
    const weightedPercent = 50 + progressInformation.percentageProcessed / 2
    this.renderWeightedPercent(weightedPercent)
  }

  async processClientFiles(
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo,
    stage: string
  ): Promise<void> {
    try {
      const fileIds: string[] =
        await this.clientFileMetadataUpload.saveFileInformation(
          files.length,
          uploadFilesInfo
        )
      const metadata: IFileMetadata[] =
        await this.clientFileExtractMetadata.extract(
          files,
          this.metadataProgressCallback
        )
      const tdrFiles =
        await this.clientFileMetadataUpload.saveClientFileMetadata(
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
