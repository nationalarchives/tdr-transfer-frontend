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

const getEntriesFromReader: (reader: IReader) => Promise<IWebkitEntry[]> =
  async (reader) => {
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
  isJudgmentUser: boolean
  formElement: HTMLFormElement
  folderRetriever: HTMLInputElement
  dropzone: HTMLElement
  selectedFiles: IFileWithPath[]
  folderUploader: (
    files: IFileWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => void

  constructor(
    isJudgmentUser: boolean,
    formElement: HTMLFormElement,
    folderRetriever: HTMLInputElement,
    dropzone: HTMLElement,
    folderUploader: (
      files: IFileWithPath[],
      uploadFilesInfo: FileUploadInfo
    ) => void
  ) {
    this.isJudgmentUser = isJudgmentUser
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
      const folderRetrieverLabel: HTMLLabelElement =
        this.folderRetriever.labels![0]
      folderRetrieverLabel.classList.add("drag-and-drop__button--highlight")
    })

    this.folderRetriever.addEventListener("blur", () => {
      const folderRetrieverLabel: HTMLLabelElement =
        this.folderRetriever.labels![0]
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
    if (this.isJudgmentUser) {
      const fileList: FileList = ev.dataTransfer?.files!
      this.checkNumberOfObjectsDropped(
        fileList,
        "You are only allowed to drop one file."
      )
      const files: File[] = this.convertFileListToArray(fileList)
      this.selectedFiles = this.convertFilesToIfilesWithPath(files)
      this.addFileSelectionSuccessMessage(this.selectedFiles[0].file.name)
    } else {
      const items: DataTransferItemList = ev.dataTransfer?.items!
      this.checkNumberOfObjectsDropped(
        items,
        "Only one folder is allowed to be selected"
      )
      const droppedItem: DataTransferItem | null = items[0]
      const webkitEntry = droppedItem.webkitGetAsEntry()
      this.checkIfDroppedItemIsFolder(webkitEntry)

      const files: IFileWithPath[] = await getAllFiles(webkitEntry, [])
      this.checkIfFolderHasFiles(files)

      this.selectedFiles = files
      this.addFolderSelectionSuccessMessage(
        webkitEntry.name,
        this.selectedFiles.length
      )
    }
    this.displaySelectionSuccessMessage()
    this.removeDragover()
  }

  addFolderListener() {
    this.dropzone.addEventListener("drop", this.handleDroppedItems)

    this.folderRetriever.addEventListener("change", () => {
      const form: HTMLFormElement | null = this.formElement
      this.selectedFiles = this.convertFilesToIfilesWithPath(
        form!.files!.files!
      )

      if (this.isJudgmentUser) {
        this.addFileSelectionSuccessMessage(this.selectedFiles[0].file.name)
        this.displaySelectionSuccessMessage()
      } else {
        const parentFolder = this.getParentFolderName(this.selectedFiles)
        this.addFolderSelectionSuccessMessage(
          parentFolder,
          this.selectedFiles.length
        )
        this.displaySelectionSuccessMessage()
      }
    })
  }

  handleFormSubmission: (ev: Event) => void = (ev: Event) => {
    ev.preventDefault()
    const folderSelected: IFileWithPath | undefined = this.selectedFiles[0]

    if (folderSelected) {
      this.formElement.addEventListener("submit", (ev) => ev.preventDefault()) // adding new event listener, in order to prevent default submit button behaviour
      this.disableSubmitButtonAndDropzone()

      const parentFolder = this.getParentFolderName(this.selectedFiles)
      const uploadFilesInfo: FileUploadInfo = {
        consignmentId: this.consignmentId(),
        parentFolder: parentFolder
      }

      this.showUploadingRecordsPage()
      this.folderUploader(this.selectedFiles, uploadFilesInfo)
    } else {
      this.successMessage?.setAttribute("hidden", "true")
      this.warningMessages.incorrectItemSelectedMessage?.setAttribute(
        "hidden",
        "true"
      )
      this.warningMessages.submissionWithoutSelectionMessage?.removeAttribute(
        "hidden"
      )

      this.warningMessages.submissionWithoutSelectionMessage?.focus()
      this.addSubmitListener() // Readd submit listener as we've set it to be removed after one form submission
    }
  }

  addSubmitListener() {
    this.formElement.addEventListener("submit", this.handleFormSubmission, {
      once: true
    })
  }

  readonly warningMessages: {
    [s: string]: HTMLElement | null
  } = {
    incorrectItemSelectedMessage: document.querySelector(
      "#item-selection-failure"
    ),
    submissionWithoutSelectionMessage: document.querySelector(
      "#nothing-selected-submission-message"
    )
  }

  readonly successMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__success"
  )

  private getParentFolderName(folder: IFileWithPath[]) {
    const firstItem: FileWithRelativePath = folder[0]
      .file as FileWithRelativePath
    const relativePath: string = firstItem.webkitRelativePath
    const splitPath: string[] = relativePath.split("/")
    const parentFolder: string = splitPath[0]
    return parentFolder
  }

  private showUploadingRecordsPage() {
    const fileUpload: HTMLDivElement | null =
      document.querySelector("#file-upload")
    const uploadProgressPage: HTMLDivElement | null =
      document.querySelector("#upload-progress")

    if (fileUpload && uploadProgressPage) {
      fileUpload.setAttribute("hidden", "true")
      uploadProgressPage.removeAttribute("hidden")
    }
  }

  private checkIfFolderHasFiles(files: File[] | IFileWithPath[]): void {
    if (files === null || files.length === 0) {
      this.rejectUserItemSelection(
        this.warningMessages?.incorrectItemSelectedMessage,
        "The folder is empty"
      )
    }
  }

  private disableSubmitButtonAndDropzone() {
    const submitButton = document.querySelector("#start-upload-button")
    submitButton?.setAttribute("disabled", "true")

    const hiddenInputButton = document.querySelector("#file-selection")
    hiddenInputButton?.setAttribute("disabled", "true")

    this.dropzone.removeEventListener("drop", this.handleDroppedItems)
  }

  private convertFilesToIfilesWithPath(files: File[]): IFileWithPath[] {
    this.checkIfFolderHasFiles(files)

    return [...files].map((file) => ({
      file,
      path: (file as FileWithRelativePath).webkitRelativePath
    }))
  }

  private removeDragover(): void {
    this.dropzone.classList.remove("drag-and-drop__dropzone--dragover")
  }

  private rejectUserItemSelection(
    warningMessageToReveal: HTMLElement | null,
    exceptionMessage: string
  ) {
    this.selectedFiles = []
    this.removeDragover()

    const warningMessages: (HTMLElement | null)[] = Object.values(
      this.warningMessages
    )

    for (const warningMessage of warningMessages) {
      if (warningMessage != warningMessageToReveal) {
        warningMessage?.setAttribute("hidden", "true")
      }
    }

    this.successMessage?.setAttribute("hidden", "true")

    warningMessageToReveal?.removeAttribute("hidden")
    warningMessageToReveal?.focus()

    throw new Error(exceptionMessage)
  }

  private addFolderSelectionSuccessMessage(
    folderName: string,
    folderSize: number
  ) {
    const folderNameElement: HTMLElement | null =
      document.querySelector("#folder-name")
    const folderSizeElement: HTMLElement | null =
      document.querySelector("#folder-size")

    if (folderNameElement && folderSizeElement) {
      folderNameElement.textContent = folderName
      folderSizeElement.textContent = `${folderSize} ${
        folderSize === 1 ? "file" : "files"
      }`
    }
  }

  private addFileSelectionSuccessMessage(fileName: string) {
    const fileNameElement: HTMLElement | null =
      document.querySelector("#file-name")
    if (fileNameElement) {
      fileNameElement.textContent = fileName
    }
  }

  private displaySelectionSuccessMessage() {
    Object.values(this.warningMessages).forEach(
      (warningMessageElement: HTMLElement | null) => {
        warningMessageElement?.setAttribute("hidden", "true")
      }
    )

    this.successMessage?.removeAttribute("hidden")
    this.successMessage?.focus()
  }

  private checkNumberOfObjectsDropped(
    droppedObjects: DataTransferItemList | FileList,
    exceptionMessage: string
  ) {
    if (droppedObjects.length > 1) {
      this.rejectUserItemSelection(
        this.warningMessages?.incorrectItemSelectedMessage,
        exceptionMessage
      )
    }
  }

  private convertFileListToArray(fileList: FileList): File[] {
    const fileListIndexes = [...Array(fileList.length).keys()]
    return fileListIndexes.map((i) => {
      const file: File = fileList[i]
      if (!file.type) {
        this.rejectUserItemSelection(
          this.warningMessages?.incorrectItemSelectedMessage,
          "Only files are allowed to be selected"
        )
      }

      return file
    })
  }

  private checkIfDroppedItemIsFolder(webkitEntry: any) {
    if (webkitEntry!.isFile) {
      this.rejectUserItemSelection(
        this.warningMessages?.incorrectItemSelectedMessage,
        "Only folders are allowed to be selected"
      )
    }
  }
}
