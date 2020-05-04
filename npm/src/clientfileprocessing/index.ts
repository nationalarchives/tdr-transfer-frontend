import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import { handleUploadError } from "../errorhandling"
import {
  IFileMetadata,
  TdrFile,
  TProgressFunction,
  IProgressInformation
} from "@nationalarchives/file-information"
import { ITdrFile, S3Upload } from "../s3upload"

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

  updateProgressBar(element: HTMLDivElement | null, progress: number): void {
    if (element && progress < 50) {
      element.setAttribute("value", progress.toString())
    }
  }

  async processClientFiles(
    consignmentId: string,
    files: TdrFile[],
    callback: TProgressFunction,
    stage: string
  ): Promise<void> {
    try {
      const fileIds: string[] = await this.clientFileMetadataUpload.saveFileInformation(
        consignmentId,
        files.length
      )
      const metadata: IFileMetadata[] = await this.clientFileExtractMetadata.extract(
        files,
        //Temporary function until s3 upload in place
        (progressInformation: IProgressInformation) => {
          console.log(
            "Percent of metadata extracted: " +
              progressInformation.percentageProcessed
          )

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

          this.updateProgressBar(
            progressBarElement,
            progressInformation.percentageProcessed
          )
        }
      )
      const tdrFiles = await this.clientFileMetadataUpload.saveClientFileMetadata(
        fileIds,
        metadata
      )
      this.s3Upload.uploadToS3(consignmentId, tdrFiles, callback, stage)
    } catch (e) {
      handleUploadError(e, "Processing client files failed")
    }
  }
}
