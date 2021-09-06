import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import {
  IFileMetadata,
  IFileWithPath,
  IProgressInformation
} from "@nationalarchives/file-information"
import { S3Upload } from "../s3upload"
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

    const currentPercentage = progressBarElement?.getAttribute("value")
    const stringWeightedPercentage = weightedPercent.toString()

    if (
      progressBarElement &&
      progressLabelElement &&
      stringWeightedPercentage !== currentPercentage
    ) {
      progressBarElement.setAttribute("value", stringWeightedPercentage)
      progressLabelElement.innerText = `Uploading records ${stringWeightedPercentage}%`
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
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo,
    stage: string
  ): Promise<void> {

    await this.clientFileMetadataUpload.startUpload(uploadFilesInfo)
    const metadata: IFileMetadata[] =
      await this.clientFileExtractMetadata.extract(
        files,
        this.metadataProgressCallback
      )
    const tdrFiles = await this.clientFileMetadataUpload.saveClientFileMetadata(
      uploadFilesInfo.consignmentId,
      metadata
    )
    await this.s3Upload.uploadToS3(
      uploadFilesInfo.consignmentId,
      tdrFiles,
      this.s3ProgressCallback,
      stage
    )
  }
}
