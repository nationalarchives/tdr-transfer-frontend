import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { UpdateConsignmentStatus } from "../updateconsignmentstatus"
import { FileUploadInfo, UploadForm } from "./form/upload-form"
import { IFrontEndInfo } from "../index"
import { handleUploadError, isError } from "../errorhandling"
import { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js"
import { refreshOrReturnToken, scheduleTokenRefresh } from "../auth"
import { S3ClientConfig } from "@aws-sdk/client-s3/dist-types/S3Client"
import { TdrFetchHandler } from "../s3upload/tdr-fetch-handler"
import { S3Client } from "@aws-sdk/client-s3"
import { IEntryWithPath } from "./form/get-files-from-drag-event"
import { TriggerBackendChecks } from "../triggerbackendchecks"

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
  goToNextPage: (formId: string) => void
  keycloak: IKeycloakInstance
  uploadUrl: string

  triggerBackendChecks: TriggerBackendChecks

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    updateConsignmentStatus: UpdateConsignmentStatus,
    frontendInfo: IFrontEndInfo,
    goToNextPage: (formId: string) => void,
    keycloak: KeycloakInstance,
    triggerBackendChecks: TriggerBackendChecks
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
    this.updateConsignmentStatus = updateConsignmentStatus
    this.stage = frontendInfo.stage
    this.goToNextPage = goToNextPage
    this.keycloak = keycloak as IKeycloakInstance
    this.uploadUrl = frontendInfo.uploadUrl
    this.triggerBackendChecks = triggerBackendChecks
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

      // const backendChecks =
      //   await this.triggerBackendChecks.triggerBackendChecks(
      //     uploadFilesInfo.consignmentId
      //   )

      // if (isError(backendChecks)) {
      //   errors.push(backendChecks)
      // }
      if (isError(processResult)) {
        errors.push(processResult)
      }
    } else {
      errors.push(cookiesResponse)
    }
    if (errors.length == 0) {
      window.removeEventListener("beforeunload", pageUnloadAction)
      // const result = await this.updateConsignmentStatus.updateConsignmentStatus(
      //   uploadFilesInfo,
      //   "Upload",
      //   "Completed"
      // )
      // if (isError(result)) {
      //   handleUploadError(result)
      // } else {
        this.goToNextPage("#upload-data-form")
      // }
    } else {
      const result = await this.updateConsignmentStatus.updateConsignmentStatus(
        uploadFilesInfo,
        "Upload",
        "CompletedWithIssues"
      )
      if (isError(result)) {
        errors.push(result)
      }
      errors.forEach((err) => handleUploadError(err))
    }
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
