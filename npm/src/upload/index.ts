import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { FileUploadInfo, UploadForm } from "./upload-form"
import { IFileWithPath } from "@nationalarchives/file-information"
import { IFrontEndInfo } from "../index"

export const pageUnloadAction: (e: BeforeUnloadEvent) => void = (e) => {
  e.preventDefault()
  e.returnValue = ""
}

export class FileUploader {
  clientFileProcessing: ClientFileProcessing
  stage: string
  goToNextPage: () => void

  constructor(
    clientFileMetadataUpload: ClientFileMetadataUpload,
    identityId: string,
    frontendInfo: IFrontEndInfo,
    goToNextPage: () => void
  ) {
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileMetadataUpload,
      new S3Upload(identityId, frontendInfo.region)
    )
    this.stage = frontendInfo.stage
    this.goToNextPage = goToNextPage
  }

  uploadFiles: (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    window.addEventListener("beforeunload", pageUnloadAction)

    try {
      await this.clientFileProcessing.processClientFiles(
        files,
        uploadFilesInfo,
        this.stage
      )
      // In order to prevent exit confirmation when page redirects to Records page
      window.removeEventListener("beforeunload", pageUnloadAction)
      this.goToNextPage()
    } catch (e) {
      //For now console log errors
      console.error(`Client file upload failed: ${e.message}`)
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
