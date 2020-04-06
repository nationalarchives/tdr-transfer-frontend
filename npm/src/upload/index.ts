import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientprocessing"

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

interface InputElement {
  files?: TdrFile[]
}

export class UploadFiles {
  clientFileProcessing: ClientFileProcessing

  constructor(clientFileProcessing: ClientFileProcessing) {
    this.clientFileProcessing = clientFileProcessing
  }

  upload(): void {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload-form"
    )

    if (uploadForm) {
      uploadForm.addEventListener("submit", ev => {
        ev.preventDefault()
        const consignmentId: number = this.retrieveConsignmentId()

        if (!consignmentId) {
          throw Error("No consignment provided")
        }

        const target: HTMLInputTarget | null = ev.currentTarget

        try {
          const files: TdrFile[] = target!.files!.files!

          this.generateFileDetails(consignmentId, files.length).then(r => {
            this.uploadClientFileMetadata(r, files)
          })
        } catch (e) {
          //For now console log errors
          console.log("Upload failed: " + e.message)
        }
      })
    }
  }

  retrieveConsignmentId(): number {
    const pathName: string = window.location.pathname
    const paths: string[] = pathName.split("/", 3)

    return parseInt(paths[2]!, 10)
  }

  //Split to separate function to make testing easier
  async generateFileDetails(
    consignmentId: number,
    numberOfFiles: number
  ): Promise<number[]> {
    const result = await this.clientFileProcessing.processFiles(
      consignmentId,
      numberOfFiles
    )

    return result
  }

  //Split to separate function to make testing easier
  async uploadClientFileMetadata(
    fileIds: number[],
    files: TdrFile[]
  ): Promise<void> {
    await this.clientFileProcessing.processClientFileMetadata(files, fileIds)
  }
}
