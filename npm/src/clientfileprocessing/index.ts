import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import {
  IFileMetadata,
  IFileWithPath,
  IProgressInformation
} from "@nationalarchives/file-information"
import { S3Upload } from "../s3upload"
import { FileUploadInfo } from "../upload/upload-form"
import {KeycloakInstance, KeycloakLoginOptions} from "keycloak-js";
import {getKeycloakInstance, isSessionAboutToExpire, refreshOrReturnToken} from "../auth";

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
    stage: string,
    keycloak: KeycloakInstance
  ): Promise<void> {
    //Periodically check that session idle timeout is not about to expire
    //Check every 20 secs to make sure catch before 30 secs left on the session idle timeout
    const intervalId = setInterval(function() {
      const sessionAboutToExpire = isSessionAboutToExpire(keycloak, 30)
      if (sessionAboutToExpire) {
        //Allow for refreshing the token before the refresh token expires
        //Keycloak documentation: https://www.keycloak.org/docs/latest/server_admin/#user-session-management
        /*
         SSO Session Idle: ... The idle timeout is reset by a client requesting authentication or by a refresh token request. ...
         */
        refreshOrReturnToken(keycloak)
      }
    }, 20000);

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
    //Once upload completed stop checking the session idle timeout
    clearInterval(intervalId)
  }
}
