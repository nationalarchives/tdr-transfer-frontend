import { TdrFile } from "@nationalarchives/file-information"

export interface InputElement extends EventTarget {
  files?: TdrFile[]
}

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

export class UploadForm {
  formElement: HTMLFormElement
  folderRetriever: HTMLInputElement
  dropzone: HTMLElement

  constructor(
    formElement: HTMLFormElement,
    folderRetriever: HTMLInputElement,
    dropzone: HTMLElement
  ) {
    this.formElement = formElement
    this.folderRetriever = folderRetriever
    this.dropzone = dropzone
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

  addButtonHighlighter() {
    this.folderRetriever.addEventListener("focus", () => {
      const folderRetrieverLabel: HTMLLabelElement = this.folderRetriever
        .labels![0]
      folderRetrieverLabel.classList.add("drag-and-drop__button--highlight")
    })

    this.folderRetriever.addEventListener("blur", () => {
      const folderRetrieverLabel: HTMLLabelElement = this.folderRetriever
        .labels![0]
      folderRetrieverLabel.classList.remove("drag-and-drop__button--highlight")
    })
  }

  addDropzoneHighlighter() {
    this.dropzone.addEventListener("dragover", (ev) => {
      ev.preventDefault()
      this.dropzone.classList.add("drag-and-drop__dropzone--dragover")
    })

    this.dropzone.addEventListener("dragleave", () => {
      this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
    })
  }

  addFolderListener() {
    this.dropzone.addEventListener("drop", (e) => {
      const droppedObject: File | null = e.dataTransfer!.files[0] // Since we do not allow multiple objects to be dropped, there is always only one file

      if (droppedObject?.type !== "") {
        const form: HTMLFormElement | null = this.formElement
        this.retrieveFiles(form) // if object has type send in empty form to invoke fail message
      }
    })

    this.folderRetriever.addEventListener("change", () => {
      const folderNameElement: HTMLElement | null = document.querySelector(
        "#folder-name"
      )
      const folderSizeElement: HTMLElement | null = document.querySelector(
        "#folder-size"
      )

      if (folderNameElement && folderSizeElement) {
        const form: HTMLFormElement | null = this.formElement
        const files = this.retrieveFiles(form)
        const folderName: string = this.getParentFolderName(files)
        const folderSize: number = files.length

        folderNameElement.textContent = folderName
        folderSizeElement.textContent =
          folderSize === 1 ? `${folderSize} file` : `${folderSize} files`
        const warningMessage: HTMLElement | null = document.querySelector(
          ".drag-and-drop__failure"
        )
        warningMessage?.classList.add("hide")
        const successMessage: HTMLElement | null = document.querySelector(
          ".drag-and-drop__success"
        )
        successMessage?.classList.remove("hide")
        this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
      }
    })
  }

  addSubmitListener(
    uploadFiles: (files: TdrFile[], consignmentId: string) => void
  ) {
    this.formElement.addEventListener("submit", (ev) => {
      ev.preventDefault()
      const target: HTMLInputTarget | null = ev.currentTarget
      const files = this.retrieveFiles(target)
      uploadFiles(files, this.consignmentId())
    })
  }

  private retrieveFiles(target: HTMLInputTarget | null): TdrFile[] {
    const files: TdrFile[] = target!.files!.files!
    if (files === null || files.length === 0) {
      this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
      const successMessage: HTMLElement | null = document.querySelector(
        ".drag-and-drop__success"
      )
      successMessage?.classList.add("hide")
      const warningMessage: HTMLElement | null = document.querySelector(
        ".drag-and-drop__failure"
      )
      warningMessage?.classList.remove("hide")
      throw Error("No files selected")
    }
    return files
  }

  private getParentFolderName(folder: TdrFile[]) {
    const relativePath = folder[0].webkitRelativePath
    const splitPath = relativePath.split("/")
    const parentFolder = splitPath[0]
    return parentFolder
  }
}
