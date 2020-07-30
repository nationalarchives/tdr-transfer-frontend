import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { S3Upload } from "../s3upload"

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

interface InputElement {
  files?: TdrFile[]
}

export class UploadFiles {
  clientFileProcessing: ClientFileProcessing
  stage: string

  constructor(
    clientFileProcessing: ClientFileMetadataUpload,
    identityId: string,
    stage: string
  ) {
    this.clientFileProcessing = new ClientFileProcessing(
      clientFileProcessing,
      new S3Upload(identityId)
    )
    this.stage = stage
  }

  retrieveFiles(target: HTMLInputTarget | null): TdrFile[] {
    const files: TdrFile[] = target!.files!.files!
    if (files === null || files.length === 0) {
      throw Error("No files selected")
    }
    return files
  }

  uploadFilesSuccess(): void {
    // Should this be called redirect
    const uploadDataFormRedirect: HTMLFormElement | null = document.querySelector(
      "#upload-data-form"
    )
    if (uploadDataFormRedirect) {
      // There is no else clause; if there is no form, an error should be thrown
      uploadDataFormRedirect.submit()
    }
  }

  upload(): void {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload-form"
    )

    if (uploadForm) {
      // There is no "else", should there be?
      uploadForm.addEventListener("submit", async ev => {
        ev.preventDefault()
        const pageUnloadAction: (e: BeforeUnloadEvent) => void = function(
          e: BeforeUnloadEvent
        ) {
          e.preventDefault()
          e.returnValue = ""
        }
        window.addEventListener("beforeunload", pageUnloadAction)
        const consignmentId: string | null = uploadForm.getAttribute(
          "data-consignment-id"
        )

        const target: HTMLInputTarget | null = ev.currentTarget

        try {
          if (!consignmentId) {
            throw Error("No consignment provided")
          }
          const files = this.retrieveFiles(target)

          await this.clientFileProcessing.processClientFiles(
            consignmentId,
            files,
            this.stage
          )
          // In order to prevent exit confirmation when page redirects to Records page
          window.removeEventListener("beforeunload", pageUnloadAction)
          this.uploadFilesSuccess()
          // Rename to something like uploadAndRedirect or submitAndRedirect?
        } catch (e) {
          //For now console log errors
          console.error("Client file upload failed: " + e.message)
        }
      })
    }
  }
}
