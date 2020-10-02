import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"
import { UploadForm } from "./upload-form"

export class UploadFiles {
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
    consignmentId: string
  ) => Promise<void> = async (files: TdrFile[], consignmentId: string) => {
    const pageUnloadAction: (e: BeforeUnloadEvent) => void = e => {
      e.preventDefault()
      e.returnValue = ""
    }
    window.addEventListener("beforeunload", pageUnloadAction)

    try {
      await this.clientFileProcessing.processClientFiles(
        consignmentId,
        files,
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

  upload(): void {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload-form"
    )

    const folderRetriever: HTMLInputElement | null = document.querySelector(
      "#file-selection"
    )

    if (uploadForm && folderRetriever) {
      const form = new UploadForm(uploadForm, folderRetriever)
      form.addFolderListener()
      form.addSubmitListener(this.uploadFiles)
    }
  }
}
