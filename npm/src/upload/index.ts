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
    // Allowance for a request beyond the time its body needs to transfer. Anything
    // longer than this without the body being sent means the request has stalled.
    const requestTimeoutMs = 5 * 60 * 1000
    const config: S3ClientConfig = {
      region: "eu-west-2",
      credentials: {
        accessKeyId: "placeholder-id",
        secretAccessKey: "placeholder-secret"
      },
      // The SDK otherwise adds an x-amz-checksum-crc32 to every request, which means
      // reading each file a second time in JavaScript purely to hash it. TDR already
      // takes its own SHA-256 of every file before uploading and the backend checks it,
      // and the transfer is over TLS, so the extra checksum only costs time. It also
      // forces a File body down the SDK's aws-chunked encoding path, which sets
      // transfer-encoding headers a browser is not allowed to send.
      requestChecksumCalculation: "WHEN_REQUIRED",
      responseChecksumValidation: "WHEN_REQUIRED",
      // A consignment can be tens of thousands of files, so a per file failure rate
      // that would be unnoticeable on a small transfer becomes likely to be hit at
      // least once across the whole upload, and one file exhausting its attempts fails
      // the transfer. The extra attempts only cost time when a request is actually
      // failing.
      maxAttempts: 10,
      // Every file in a consignment is written under the same
      // {userId}/{consignmentId}/ prefix, and S3 only raises the request rate it
      // allows for a new prefix gradually. A consignment of small files is fast
      // enough per file to outrun that, at which point S3 starts rejecting requests
      // with 503 SlowDown. Large files never hit this because they are limited by
      // bandwidth rather than by round trips, so the request rate stays low.
      //
      // The standard retry mode has no client side rate limiting: it retries a
      // throttled request a few times over a handful of seconds and then gives up,
      // which is far shorter than S3 takes to scale the prefix up. Because every
      // worker is being throttled at once, the retry token bucket then drains and
      // retrying stops altogether. Adaptive mode adds a rate limiter that slows the
      // client down in response to throttling and speeds it back up as it recovers.
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
      // A file whose upload exhausts its retries rejects rather than returning an
      // error. Without this the rejection has nothing to handle it, so the redirect
      // below never runs and the user is left watching a progress bar that has
      // stopped part way through with no explanation.
      const error = e instanceof Error ? e : Error(getErrorMessage(e))
      if (error instanceof LoggedOutError) {
        // The user has already been shown the logged out message and a link back to
        // login, which redirecting would only replace.
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
