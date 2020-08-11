import { TdrFile } from "@nationalarchives/file-information"

interface InputElement {
  files?: TdrFile[]
}

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

export class UploadForm {
  formElement: HTMLFormElement

  constructor(formElement: HTMLFormElement) {
    this.formElement = formElement
  }

  consignmentId: () => string = () => {
    const value: string | null = this.formElement.getAttribute(
      "data-consignment-id"
    )
    if (!value) {
      throw Error("No consignment provided")
    }
    return value
  }

  addSubmitListener(
    uploadFiles: (files: TdrFile[], consignmentId: string) => void
  ) {
    this.formElement.addEventListener("submit", ev => {
      ev.preventDefault()
      const target: HTMLInputTarget | null = ev.currentTarget
      const files = this.retrieveFiles(target)
      uploadFiles(files, this.consignmentId())
    })
  }

  private retrieveFiles(target: HTMLInputTarget | null): TdrFile[] {
    const files: TdrFile[] = target!.files!.files!
    if (files === null || files.length === 0) {
      throw Error("No files selected")
    }
    return files
  }
}
