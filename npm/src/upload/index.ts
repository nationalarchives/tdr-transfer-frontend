import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { FileUploadInfo, UploadForm } from "./upload-form"

export class FileUploader {
  clientFileProcessing: ClientFileProcessing
  stage: string
  goToNextPage: () => void

  constructor(
    clientFileProcessing: ClientFileMetadataUpload,
    identityId: string,
    stage: string,
    goToNextPage: () => void
  ) {
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileProcessing,
      new S3Upload(identityId)
    )
    this.stage = stage
    this.goToNextPage = goToNextPage
  }

  uploadFiles: (
    files: TdrFile[],
    uploadFilesInfo: FileUploadInfo
  ) => Promise<void> = async (
    files: TdrFile[],
    uploadFilesInfo: FileUploadInfo
  ) => {
    const pageUnloadAction: (e: BeforeUnloadEvent) => void = (e) => {
      e.preventDefault()
      e.returnValue = ""
    }
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
      console.error("Client file upload failed: " + e.message)
    }
  }

  initialiseFormListeners(): void {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload-form"
    )

    const folderRetriever: HTMLInputElement | null = document.querySelector(
      "#file-selection"
    )

    const dropzone: HTMLElement | null = document.querySelector(
      ".drag-and-drop__dropzone"
    )

    if (uploadForm && folderRetriever && dropzone) {
      const form = new UploadForm(uploadForm, folderRetriever, dropzone)
      form.addFolderListener()
      form.addSubmitListener(this.uploadFiles)
      form.addButtonHighlighter()
      form.addDropzoneHighlighter()
    }
  }
}
