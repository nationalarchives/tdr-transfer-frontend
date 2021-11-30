import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { UpdateConsignmentStatus } from "../updateconsignmentstatus"
import { FileUploadInfo, UploadForm } from "./upload-form"
import { IFileWithPath } from "@nationalarchives/file-information"
import { IFrontEndInfo } from "../index"
import { handleUploadError } from "../errorhandling"
import { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js"
import { refreshOrReturnToken, scheduleTokenRefresh } from "../auth"

export interface IKeycloakInstance extends KeycloakInstance {
  tokenParsed: IKeycloakTokenParsed
}

export interface IKeycloakTokenParsed extends KeycloakTokenParsed {
  judgment_user?: boolean
}

export const pageUnloadAction: (e: BeforeUnloadEvent) => void = (e) => {
  e.preventDefault()
  e.returnValue = ""
}

export class FileUploader {
  clientFileProcessing: ClientFileProcessing
  updateConsignmentStatus: UpdateConsignmentStatus
  stage: string
  goToNextPage: () => void
  keycloak: IKeycloakInstance
  uploadUrl: string

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    updateConsignmentStatus: UpdateConsignmentStatus,
    frontendInfo: IFrontEndInfo,
    goToNextPage: () => void,
    keycloak: KeycloakInstance
  ) {
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileMetadataUpload,
      new S3Upload()
    )
    this.updateConsignmentStatus = updateConsignmentStatus
    this.stage = frontendInfo.stage
    this.goToNextPage = goToNextPage
    this.keycloak = keycloak as IKeycloakInstance
    this.uploadUrl = frontendInfo.uploadUrl
  }

  uploadFiles: (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    window.addEventListener("beforeunload", pageUnloadAction)
    const refreshedToken = await refreshOrReturnToken(this.keycloak)

    const cookiesUrl = `${this.uploadUrl}/cookies`
    scheduleTokenRefresh(this.keycloak, cookiesUrl)
    await fetch(cookiesUrl, {
      credentials: "include",
      headers: { Authorization: `Bearer ${refreshedToken}` }
    })
    try {
      await this.clientFileProcessing.processClientFiles(
        files,
        uploadFilesInfo,
        this.stage,
        this.keycloak.tokenParsed?.sub
      )
      await this.updateConsignmentStatus.markConsignmentStatusAsCompleted(
        uploadFilesInfo
      )

      // In order to prevent exit confirmation when page redirects to Records page
      window.removeEventListener("beforeunload", pageUnloadAction)
      this.goToNextPage()
    } catch (e) {
      handleUploadError(e, "Processing client files failed")
    }
  }

  initialiseFormListeners(): void {
    const isJudgmentUser: boolean =
      this.keycloak.tokenParsed?.judgment_user === true

    const uploadForm: HTMLFormElement | null =
      document.querySelector("#file-upload-form")

    const folderRetriever: HTMLInputElement | null =
      document.querySelector("#file-selection")

    const dropzone: HTMLElement | null = document.querySelector(
      ".drag-and-drop__dropzone"
    )

    if (uploadForm && folderRetriever && dropzone) {
      const form = new UploadForm(
        isJudgmentUser,
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
