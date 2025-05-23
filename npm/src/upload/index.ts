import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { FileUploadInfo, UploadForm } from "./form/upload-form"
import { IFrontEndInfo } from "../index"
import { isError } from "../errorhandling"
import Keycloak, { KeycloakTokenParsed } from "keycloak-js"
import { refreshOrReturnToken, scheduleTokenRefresh } from "../auth"
import { S3ClientConfig } from "@aws-sdk/client-s3/dist-types/S3Client"
import { TdrFetchHandler } from "../s3upload/tdr-fetch-handler"
import { S3Client } from "@aws-sdk/client-s3"
import { IEntryWithPath } from "./form/get-files-from-drag-event"

export interface IKeycloakInstance extends Keycloak {
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
  stage: string
  keycloak: IKeycloakInstance
  uploadUrl: string
  goToNextPage: (
    consignmentId: string,
    uploadFailed: String,
    isJudgmentUser: Boolean
  ) => void

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    frontendInfo: IFrontEndInfo,
    keycloak: Keycloak,
    goToNextPage: (
      consignmentId: string,
      uploadFailed: String,
      isJudgmentUser: Boolean
    ) => void
  ) {
    const requestTimeoutMs = 20 * 60 * 1000
    const config: S3ClientConfig = {
      region: "eu-west-2",
      credentials: {
        accessKeyId: "placeholder-id",
        secretAccessKey: "placeholder-secret"
      },
      requestHandler: new TdrFetchHandler({ requestTimeoutMs })
    }

    const client = new S3Client(config)
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileMetadataUpload,
      new S3Upload(client, frontendInfo.uploadUrl)
    )
    this.stage = frontendInfo.stage
    this.keycloak = keycloak as IKeycloakInstance
    this.uploadUrl = frontendInfo.uploadUrl
    this.goToNextPage = goToNextPage
  }

  uploadFiles: (
    files: IEntryWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: IEntryWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    window.addEventListener("beforeunload", pageUnloadAction)
    const refreshedToken = await refreshOrReturnToken(this.keycloak)

    const cookiesUrl = `${this.uploadUrl}/cookies`
    scheduleTokenRefresh(this.keycloak, cookiesUrl)
    const errors: Error[] = []
    const cookiesResponse = await fetch(cookiesUrl, {
      credentials: "include",
      headers: { Authorization: `Bearer ${refreshedToken}` }
    }).catch((err) => {
      return err
    })
    if (!isError(cookiesResponse)) {
      const processResult = await this.clientFileProcessing.processClientFiles(
        files,
        uploadFilesInfo,
        this.stage,
        this.keycloak.tokenParsed?.sub
      )

      if (isError(processResult)) {
        errors.push(processResult)
      }
    } else {
      errors.push(cookiesResponse)
    }

    const isJudgmentUser: boolean =
      this.keycloak.tokenParsed?.judgment_user === true
    const consignmentId = uploadFilesInfo.consignmentId
    const uploadFailed = errors.length > 0

    window.removeEventListener("beforeunload", pageUnloadAction)
    this.goToNextPage(consignmentId, uploadFailed.toString(), isJudgmentUser)
  }

  initialiseFormListeners(): void {
    const isJudgmentUser: boolean =
      this.keycloak.tokenParsed?.judgment_user === true

    const uploadForm: HTMLFormElement | null =
      document.querySelector("#file-upload-form")

    const itemRetriever: HTMLInputElement | null =
      document.querySelector("#file-selection")

    const dropzone: HTMLElement | null = document.querySelector(
      ".drag-and-drop__dropzone"
    )

    if (uploadForm && itemRetriever && dropzone) {
      const form = new UploadForm(
        isJudgmentUser,
        uploadForm,
        itemRetriever,
        dropzone,
        this.uploadFiles
      )
      form.addFolderListener()
      form.addSubmitListener()
      form.addButtonHighlighter()
      form.addDropzoneHighlighter()
      form.addRemoveSelectedItemListener()
    }
  }
}
