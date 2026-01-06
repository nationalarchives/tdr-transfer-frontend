import {ClientFileMetadataUpload} from "../clientfilemetadataupload"
import {ClientFileExtractMetadata} from "../clientfileextractmetadata"
import {IFileMetadata, IFileWithPath, IProgressInformation} from "@nationalarchives/file-information"
import {S3Upload} from "../s3upload"
import {FileUploadInfo} from "../upload/form/upload-form"
import {isError} from "../errorhandling"
import {ICompleteLoadInfo, ILoadErrors, TransferService} from "../transfer-service";
import {IEntryWithPath, isDirectory, isFile} from "../upload/form/get-files-from-drag-event"
import {ServiceOutputTypes} from "@aws-sdk/client-s3";

export interface IUploadResult {
  sendData: ServiceOutputTypes[]
  processedChunks: number
  totalChunks: number
}

export class ClientFileProcessing {
  clientFileMetadataUpload: ClientFileMetadataUpload
  clientFileExtractMetadata: ClientFileExtractMetadata
  s3Upload: S3Upload
  transferService: TransferService

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    s3Upload: S3Upload
  ) {
    this.clientFileMetadataUpload = clientFileMetadataUpload
    this.clientFileExtractMetadata = new ClientFileExtractMetadata()
    this.s3Upload = s3Upload
    this.transferService = new TransferService()
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
    userId: string | undefined,
    bearerAccessToken: string | Error
  ): Promise<void | Error> {
    const uploadResult =
      await this.clientFileMetadataUpload.startUpload(uploadFilesInfo)
    if (!isError(uploadResult)) {
      const emptyFolders = files
        .filter((f) => isDirectory(f))
        .map((f) => f.path)

      const metadata: IFileMetadata[] | Error =
        await this.clientFileExtractMetadata.extract(
          files.filter((f) => isFile(f)) as IFileWithPath[],
          this.metadataProgressCallback
        )

      if (!isError(metadata)) {
          const uploadResult = await this.upload(
              userId,
              uploadFilesInfo.consignmentId,
              metadata,
              stage
          )
          if (!isError(uploadResult)) {
            const loadInfo: ICompleteLoadInfo = {
              expectedNumberFiles: metadata.length,
              loadedNumberFiles: metadata.length,
              loadErrors: []
            }
            await this.transferService.completeLoad(uploadFilesInfo.consignmentId, bearerAccessToken, loadInfo)
          }
          else {
            const loadError: ILoadErrors = {
              message: uploadResult.message
            }
            const loadInfo: ICompleteLoadInfo = {
              expectedNumberFiles: metadata.length,
              loadedNumberFiles: metadata.length,
              loadErrors: [loadError]
            }
            await this.transferService.completeLoad(uploadFilesInfo.consignmentId, bearerAccessToken, loadInfo)
            return uploadResult
          }
      } else {
        return metadata
      }
    } else {
      return uploadResult
    }
  }

  private upload: (
      userId: string,
      consignmentId: string,
      assets: IFileMetadata[],
      stage: string
  ) => Promise<Error | IUploadResult > = async (
      userId,
      consignmentId,
      assets,
      stage
  ) => {
      return await this.s3Upload.uploadToS3(
          consignmentId,
          userId,
          assets,
          this.s3ProgressCallback,
          stage
      )
  }
}
