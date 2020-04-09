import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientfileprocessing"
import { ClientFileMetadataUpload } from "../clientfilemetadataupload"

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

interface InputElement {
  files?: TdrFile[]
}

export class UploadFiles {
  clientFileProcessing: ClientFileProcessing

  constructor(clientFileProcessing: ClientFileMetadataUpload) {
    this.clientFileProcessing = new ClientFileProcessing(clientFileProcessing)
  }

  upload(): void {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload-form"
    )

    if (uploadForm) {
      uploadForm.addEventListener("submit", ev => {
        ev.preventDefault()
        const consignmentId: string | null = uploadForm.getAttribute(
          "data-consignment-id"
        )

        const target: HTMLInputTarget | null = ev.currentTarget

        try {
          if (!consignmentId) {
            throw Error("No consignment provided")
          }
          const files: TdrFile[] = target!.files!.files!
          this.clientFileProcessing.processClientFiles(consignmentId, files)
        } catch (e) {
          //For now console log errors
          console.error("Client file upload failed: " + e.message)
        }
      })
    }
  }
}
