import { IFileWithPath } from "@nationalarchives/file-information"

interface FileWithRelativePath extends File {
  webkitRelativePath: string
}

export interface FileUploadInfo {
  consignmentId: string
  parentFolder: string
}

export interface InputElement extends EventTarget {
  files?: File[]
}

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

export interface IReader {
  readEntries: (callbackFunction: (entry: IWebkitEntry[]) => void) => void
}

export interface IWebkitEntry extends DataTransferItem {
  createReader: () => IReader
  isFile: boolean
  isDirectory: boolean
  fullPath: string
  name?: string
  file: (success: (file: File) => void) => void
}

const getAllFiles: (
  entry: IWebkitEntry,
  fileInfoInput: IFileWithPath[]
) => Promise<IFileWithPath[]> = async (entry, fileInfoInput) => {
  const reader: IReader = entry.createReader()
  const entries: IWebkitEntry[] = await getEntriesFromReader(reader)

  for (const entry of entries) {
    if (entry.isDirectory) {
      await getAllFiles(entry, fileInfoInput)
    } else {
      const file: IFileWithPath = await getFileFromEntry(entry)
      fileInfoInput.push(file)
    }
  }
  return fileInfoInput
}

const getEntriesFromReader: (
  reader: IReader
) => Promise<IWebkitEntry[]> = async (reader) => {
  let allEntries: IWebkitEntry[] = []

  let nextBatch = await getEntryBatch(reader)

  while (nextBatch.length > 0) {
    allEntries = allEntries.concat(nextBatch)
    nextBatch = await getEntryBatch(reader)
  }

  return allEntries
}

const getFileFromEntry: (entry: IWebkitEntry) => Promise<IFileWithPath> = (
  entry
) => {
  return new Promise<IFileWithPath>((resolve) => {
    entry.file((file) =>
      resolve({
        file,
        path: entry.fullPath
      })
    )
  })
}

const getEntryBatch: (reader: IReader) => Promise<IWebkitEntry[]> = (
  reader
) => {
  return new Promise<IWebkitEntry[]>((resolve) => {
    reader.readEntries((entries) => resolve(entries))
  })
}

export class UploadForm {
  formElement: HTMLFormElement
  folderRetriever: HTMLInputElement
  dropzone: HTMLElement
  selectedFiles: IFileWithPath[]
  folderUploader: (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => void

  constructor(
    formElement: HTMLFormElement,
    folderRetriever: HTMLInputElement,
    dropzone: HTMLElement,
    folderUploader: (
      files: IFileWithPath[],
      uploadFilesInfo: FileUploadInfo
    ) => void
  ) {
    this.formElement = formElement
    this.folderRetriever = folderRetriever
    this.dropzone = dropzone
    this.selectedFiles = []
    this.folderUploader = folderUploader
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
      this.removeDragover()
    })
  }

  handleDroppedItems: (ev: DragEvent) => any = async (ev) => {
    ev.preventDefault()
    const items: DataTransferItemList = ev.dataTransfer?.items!
    if (items.length > 1) {
      this.rejectUserItemSelection()
    }
    const droppedItem: DataTransferItem | null = items[0]
    const webkitEntry = droppedItem.webkitGetAsEntry()
    if (webkitEntry!.isFile) {
      this.rejectUserItemSelection()
    }
    const files = await getAllFiles(webkitEntry, [])
    this.selectedFiles = files
    this.checkIfFolderHasFiles(files)
    const folderSize = files.length
    const folderName = webkitEntry.name
    this.updateFolderSelectionStatus(folderName, folderSize)
  }

  addFolderListener() {
    this.dropzone.addEventListener("drop", this.handleDroppedItems)

    this.folderRetriever.addEventListener("change", () => {
      const form: HTMLFormElement | null = this.formElement
      const files = this.retrieveFiles(form)
      this.selectedFiles = files
      const parentFolder = this.getParentFolderName(this.selectedFiles)
      this.updateFolderSelectionStatus(parentFolder, files.length)
    })
  }

  handleFormSubmission: (ev: Event) => void = (ev: Event) => {
    ev.preventDefault()

    this.formElement.addEventListener("submit", (ev) => ev.preventDefault()) // adding new event listener, in order to prevent default submit button behaviour
    this.disableButtonsAndDropzone()
    const parentFolder = this.getParentFolderName(this.selectedFiles)
    const uploadFilesInfo: FileUploadInfo = {
      consignmentId: this.consignmentId(),
      parentFolder: parentFolder
    }
    this.folderUploader(this.selectedFiles, uploadFilesInfo)
  }

  addSubmitListener() {
    this.formElement.addEventListener("submit", this.handleFormSubmission, {
      once: true
    })
  }

  private getParentFolderName(folder: IFileWithPath[]) {
    const firstItem: FileWithRelativePath = folder[0]
      .file as FileWithRelativePath
    const relativePath: string = firstItem.webkitRelativePath
    const splitPath: string[] = relativePath.split("/")
    const parentFolder: string = splitPath[0]
    return parentFolder
  }

  private checkIfFolderHasFiles(files: File[] | IFileWithPath[]): void {
    if (files === null || files.length === 0) {
      this.rejectUserItemSelection()
    }
  }

  private disableButtonsAndDropzone() {
    const submitAndLabelButtons = document.querySelectorAll(".govuk-button")
    submitAndLabelButtons.forEach((button) =>
      button.setAttribute("disabled", "true")
    )

    const hiddenInputButton = document.querySelector("#file-selection")
    hiddenInputButton?.setAttribute("disabled", "true")

    this.dropzone.removeEventListener("drop", this.handleDroppedItems)
  }

  private retrieveFiles(target: HTMLInputTarget | null): IFileWithPath[] {
    const files: File[] = target!.files!.files!
    this.checkIfFolderHasFiles(files)

    return [...files].map((file) => ({
      file,
      path: (file as FileWithRelativePath).webkitRelativePath
    }))
  }

  private removeDragover(): void {
    this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
  }

  private rejectUserItemSelection() {
    this.selectedFiles = []
    this.removeDragover()
    const successMessage: HTMLElement | null = document.querySelector(
      ".drag-and-drop__success"
    )
    successMessage?.setAttribute("hidden", "true")
    const warningMessage: HTMLElement | null = document.querySelector(
      ".drag-and-drop__failure"
    )
    warningMessage?.removeAttribute("hidden")
    warningMessage?.focus()
    throw new Error("No files selected")
  }

  private updateFolderSelectionStatus(folderName: string, folderSize: number) {
    const folderNameElement: HTMLElement | null = document.querySelector(
      "#folder-name"
    )
    const folderSizeElement: HTMLElement | null = document.querySelector(
      "#folder-size"
    )

    if (folderNameElement && folderSizeElement) {
      folderNameElement.textContent = folderName
      folderSizeElement.textContent = `${folderSize} ${
        folderSize === 1 ? "file" : "files"
      }`
      const warningMessage: HTMLElement | null = document.querySelector(
        ".drag-and-drop__failure"
      )
      warningMessage?.setAttribute("hidden", "true")
      const successMessage: HTMLElement | null = document.querySelector(
        ".drag-and-drop__success"
      )
      successMessage?.removeAttribute("hidden")
      successMessage?.focus()
      this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
    }
  }
}
