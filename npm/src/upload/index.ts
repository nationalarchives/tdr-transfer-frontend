import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { UpdateConsignmentStatus } from "../updateconsignmentstatus"
import { FileUploadInfo, UploadForm } from "./upload-form"
import { IFileWithPath } from "@nationalarchives/file-information"
import { IFrontEndInfo } from "../index"
import { handleUploadError } from "../errorhandling"
import { KeycloakInstance } from "keycloak-js";
import { idleSessionTimeoutAboutToExpire, refreshOrReturnToken } from "../auth";

export const pageUnloadAction: (e: BeforeUnloadEvent) => void = (e) => {
  e.preventDefault()
  e.returnValue = ""
}

const idleSessionCheckInMilliSecs = 20000

export class FileUploader {
  clientFileProcessing: ClientFileProcessing
  updateConsignmentStatus: UpdateConsignmentStatus
  stage: string
  goToNextPage: () => void
  keycloak: KeycloakInstance

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    updateConsignmentStatus: UpdateConsignmentStatus,
    identityId: string,
    frontendInfo: IFrontEndInfo,
    goToNextPage: () => void,
    keycloak: KeycloakInstance
  ) {
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileMetadataUpload,
      new S3Upload(identityId, frontendInfo.region)
    )
    this.updateConsignmentStatus = updateConsignmentStatus
    this.stage = frontendInfo.stage
    this.goToNextPage = goToNextPage
    this.keycloak = keycloak
  }

  uploadFiles: (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    window.addEventListener("beforeunload", pageUnloadAction)

    const intervalId = setInterval(() => {
      if (idleSessionTimeoutAboutToExpire(this.keycloak)) {
        //Allow for refreshing the token before the refresh token expires
        //Keycloak documentation: https://www.keycloak.org/docs/latest/server_admin/#user-session-management
        /*
         SSO Session Idle: ... The idle timeout is reset by a client requesting authentication or by a refresh token request. ...
        */
        refreshOrReturnToken(this.keycloak)
      }
    }, idleSessionCheckInMilliSecs);

    try {
      await this.clientFileProcessing.processClientFiles(
        files,
        uploadFilesInfo,
        this.stage
      )
      await this.updateConsignmentStatus.markConsignmentStatusAsCompleted(
        uploadFilesInfo
      )

      clearInterval(intervalId)

      // In order to prevent exit confirmation when page redirects to Records page
      window.removeEventListener("beforeunload", pageUnloadAction)
      this.goToNextPage()
    } catch (e) {
      handleUploadError(e, "Processing client files failed")
    }
  }

  initialiseFormListeners(): void {
    const uploadForm: HTMLFormElement | null =
      document.querySelector("#file-upload-form")

    const folderRetriever: HTMLInputElement | null =
      document.querySelector("#file-selection")

    const dropzone: HTMLElement | null = document.querySelector(
      ".drag-and-drop__dropzone"
    )

    if (uploadForm && folderRetriever && dropzone) {
      const form = new UploadForm(
        uploadForm,
        folderRetriever,
        dropzone,
        this.uploadFiles
      )
      form.addFolderListener()
      form.addSubmitListener()
      form.addButtonHighlighter()
      form.addDropzoneHighlighter()
    }
  }
}
