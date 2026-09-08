import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { FileUploadInfo, UploadForm } from "./form/upload-form"
import { IFrontEndInfo } from "../index"
import { isError, getErrorMessage, LoggedOutError } from "../errorhandling"
import Keycloak, { KeycloakTokenParsed } from "keycloak-js"
import { refreshOrReturnToken, scheduleTokenRefresh } from "../auth"
import { S3ClientConfig } from "@aws-sdk/client-s3/dist-types/S3Client"
import { TdrFetchHandler } from "../s3upload/tdr-fetch-handler"
import { S3Client } from "@aws-sdk/client-s3"
import { IEntry } from "./form/file-types"

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
    // Allowance for a request beyond the time its body needs to transfer.
    const requestTimeoutMs = 5 * 60 * 1000
    const config: S3ClientConfig = {
      region: "eu-west-2",
      credentials: {
        accessKeyId: "placeholder-id",
        secretAccessKey: "placeholder-secret"
      },
      // Avoids the SDK hashing every file a second time for a CRC32 checksum, on top of
      // the SHA-256 TDR already takes, and its aws-chunked encoding of File bodies.
      requestChecksumCalculation: "WHEN_REQUIRED",
      responseChecksumValidation: "WHEN_REQUIRED",
      // A single file exhausting its attempts fails the whole transfer, which is likely
      // across a consignment of tens of thousands of files.
      maxAttempts: 10,
      // Uploading many small files under one prefix outruns the request rate S3 allows
      // for it, and only adaptive mode rate limits the client in response to the 503
      // SlowDown responses that follow.
      retryMode: "adaptive",
      requestHandler: new TdrFetchHandler({ requestTimeoutMs })
    }

    const client = new S3Client(config)
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileMetadataUpload,
      new S3Upload(
        client,
        frontendInfo.uploadUrl,
        frontendInfo.ifNoneMatchHeaderValue,
        frontendInfo.aclHeaderValue
      )
    )
    this.stage = frontendInfo.stage
    this.keycloak = keycloak as IKeycloakInstance
    this.uploadUrl = frontendInfo.uploadUrl
    this.goToNextPage = goToNextPage
  }

  uploadFiles: (
    files: IEntry[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: IEntry[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    window.addEventListener("beforeunload", pageUnloadAction)
    const errors: Error[] = []

    try {
      const refreshedToken = await refreshOrReturnToken(this.keycloak)
      if (isError(refreshedToken)) {
        throw refreshedToken
      }

      const cookiesUrl = `${this.uploadUrl}/cookies`
      scheduleTokenRefresh(this.keycloak, cookiesUrl)
      await fetch(cookiesUrl, {
        credentials: "include",
        headers: { Authorization: `Bearer ${refreshedToken}` }
      })

      const processResult = await this.clientFileProcessing.processClientFiles(
        files,
        uploadFilesInfo,
        this.stage,
        this.keycloak.tokenParsed?.sub
      )

      if (isError(processResult)) {
        errors.push(processResult)
      }
    } catch (e) {
      // A file whose upload exhausts its retries rejects rather than returning an error.
      const error = e instanceof Error ? e : Error(getErrorMessage(e))
      if (error instanceof LoggedOutError) {
        // The user has already been shown the logged out message and a login link.
        window.removeEventListener("beforeunload", pageUnloadAction)
        return
      }
      errors.push(error)
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
