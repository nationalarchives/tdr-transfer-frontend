import { IFileWithPath } from "@nationalarchives/file-information"
import {
  getAllFiles,
  IDirectoryWithPath,
  IEntryWithPath,
  isFile,
  IWebkitEntry
} from "./get-files-from-drag-event"
import { rejectUserItemSelection } from "./display-warning-message"
import {
  addFileSelectionSuccessMessage,
  addFolderSelectionSuccessMessage,
  displaySelectionSuccessMessage
} from "./update-and-display-success-message"
import { isError } from "../../errorhandling"

interface FileWithRelativePath extends File {
  webkitRelativePath: string
}

export interface FileUploadInfo {
  consignmentId: string
  parentFolder: string
}

export class UploadForm {
  isJudgmentUser: boolean
  formElement: HTMLFormElement
  itemRetriever: HTMLInputElement
  dropzone: HTMLElement
  selectedFiles: IEntryWithPath[]
  folderUploader: (
    files: IEntryWithPath[],
    uploadFilesInfo: FileUploadInfo
  ) => void

  constructor(
    isJudgmentUser: boolean,
    formElement: HTMLFormElement,
    itemRetriever: HTMLInputElement,
    dropzone: HTMLElement,
    folderUploader: (
      files: IEntryWithPath[],
      uploadFilesInfo: FileUploadInfo
    ) => void
  ) {
    this.isJudgmentUser = isJudgmentUser
    this.formElement = formElement
    this.itemRetriever = itemRetriever
    this.dropzone = dropzone
    this.selectedFiles = []
    this.folderUploader = folderUploader
  }

  consignmentId: () => string | Error = () => {
    const value: string | null = this.formElement.getAttribute(
      "data-consignment-id"
    )

    if (!value) {
      return Error("No consignment provided")
    }
    return value
  }

  addButtonHighlighter() {
    const itemRetrieverLabel: HTMLLabelElement = this.itemRetriever.labels![0]
    this.itemRetriever.addEventListener("focus", () => {
      itemRetrieverLabel.classList.add("drag-and-drop__button--highlight")
    })

    this.itemRetriever.addEventListener("blur", () => {
      itemRetrieverLabel.classList.remove("drag-and-drop__button--highlight")
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
      const resultOrError = this.checkNumberOfObjectsDropped(
        fileList,
        "You are only allowed to drop one file."
      )
      if (!isError(resultOrError)) {
        const files: File[] | Error = this.convertFileListToArray(fileList)
        if (!isError(files)) {
          const fileName: string = fileList.item(0)?.name!
          /* checkForCorrectJudgmentFileExtension must be called after the dropped item's type is checked
          (in convertFileListToArray), otherwise the extension error message will display when folder is dropped */
          const extensionError =
            this.checkForCorrectJudgmentFileExtension(fileName)
          if (!isError(extensionError)) {
            this.selectedFiles = this.convertFilesToIfilesWithPath(files)
            addFileSelectionSuccessMessage(fileName)
          } else {
            return extensionError
          }
        } else {
          return files
        }
      } else {
        return resultOrError
      }
    } else {
      const items: DataTransferItemList = ev.dataTransfer?.items!
      const resultOrError = this.checkNumberOfFoldersOrFilesDropped(
        items,
        "Only one folder is allowed to be selected"
      )
      if (!isError(resultOrError)) {
        const droppedItem: DataTransferItem | null = items[0]
        const webkitEntry = droppedItem.webkitGetAsEntry()
        const resultOrError = this.checkIfDroppedItemIsFolder(webkitEntry)
        if (!isError(resultOrError)) {
          const filesAndFolders: (IFileWithPath | IDirectoryWithPath)[] =
            await getAllFiles(webkitEntry as unknown as IWebkitEntry, [])
          const files = filesAndFolders.filter((f) =>
            isFile(f)
          ) as IFileWithPath[]
          const folderCheck = this.checkIfFolderHasFiles(files)
          if (!isError(folderCheck)) {
            this.selectedFiles = filesAndFolders
            addFolderSelectionSuccessMessage(
              webkitEntry!.name,
              this.selectedFiles.filter((f) => isFile(f)).length
            )
          } else {
            return folderCheck
          }
        } else {
          return resultOrError
        }
      } else {
        return resultOrError
      }
    }
    displaySelectionSuccessMessage(
      this.successAndRemovalMessageContainer,
      this.warningMessages
    )
    this.removeDragover()
  }

  handleSelectedItems: () => any = async () => {
    const form: HTMLFormElement | null = this.formElement
    this.selectedFiles = this.convertFilesToIfilesWithPath(form!.files!.files!)

    if (this.isJudgmentUser) {
      const fileWithPath = this.selectedFiles[0]
      if (isFile(fileWithPath)) {
        const fileName = fileWithPath.file.name
        const checkOrError = this.checkForCorrectJudgmentFileExtension(fileName)
        if (!isError(checkOrError)) {
          addFileSelectionSuccessMessage(fileName)
        } else {
          return checkOrError
        }
      }
    } else {
      const parentFolder = this.getParentFolderName(this.selectedFiles)
      addFolderSelectionSuccessMessage(parentFolder, this.selectedFiles.length)
    }
    displaySelectionSuccessMessage(
      this.successAndRemovalMessageContainer,
      this.warningMessages
    )
  }

  removeSelectedItem = (ev: Event): void => {
    ev.preventDefault()
    const folderSelectionMessage: HTMLElement | null = document.querySelector(
      "#item-selection-success-container"
    )

    this.selectedFiles = []
    folderSelectionMessage?.setAttribute("hidden", "true")
    this.warningMessages.removedSelectionMessage?.removeAttribute("hidden")
    this.successAndRemovalMessageContainer?.focus()

    this.formElement.reset()
  }

  addRemoveSelectedItemListener() {
    const removeSelectionBtn: HTMLElement | null =
      document.querySelector("#remove-file-btn")
    removeSelectionBtn?.addEventListener("click", this.removeSelectedItem)
  }

  addFolderListener() {
    this.dropzone.addEventListener("drop", this.handleDroppedItems)
    this.itemRetriever.addEventListener("change", this.handleSelectedItems)
  }

  handleFormSubmission: (ev: Event) => Promise<void | Error> = async (
    ev: Event
  ) => {
    ev.preventDefault()
    const itemSelected: IEntryWithPath = this.selectedFiles[0]

    if (itemSelected) {
      this.formElement.addEventListener("submit", (ev) => ev.preventDefault()) // adding new event listener, in order to prevent default submit button behaviour
      this.disableSubmitButtonAndDropzone()

      const parentFolder = this.getParentFolderName(this.selectedFiles)
      const consignmentIdOrError = this.consignmentId()
      if (!isError(consignmentIdOrError)) {
        const consignmentId = consignmentIdOrError
        const uploadFilesInfo: FileUploadInfo = {
          consignmentId,
          parentFolder: parentFolder
        }
        UploadForm.showUploadingRecordsPage()
        this.folderUploader(this.selectedFiles, uploadFilesInfo)
      } else {
        return consignmentIdOrError
      }
    } else {
      this.addSubmitListener() // Add submit listener back as we've set it to be removed after one form submission
      this.removeFilesAndDragOver()
      return rejectUserItemSelection(
        this.warningMessages?.submissionWithoutSelectionMessage,
        this.warningMessages,
        this.successAndRemovalMessageContainer,
        "A submission was made without an item being selected"
      )
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
    incorrectFileExtensionMessage: document.querySelector(
      "#incorrect-file-extension"
    ),
    incorrectItemSelectedMessage: document.querySelector(
      "#item-selection-failure"
    ),
    multipleItemSelectedMessage: document.querySelector(
      "#multiple-selection-failure"
    ),
    multipleFolderSelectedMessage: document.querySelector(
      "#multiple-folder-selection-failure"
    ),
    submissionWithoutSelectionMessage: document.querySelector(
      "#nothing-selected-submission-message"
    ),
    removedSelectionMessage: document.querySelector(
      "#removed-selection-container"
    )
  }

  readonly successAndRemovalMessageContainer: HTMLElement | null =
    document.querySelector("#success-and-removal-message-container")

  private getParentFolderName(folder: IEntryWithPath[]) {
    const firstItem: IEntryWithPath = folder.filter((f) => isFile(f))[0]
    const relativePath: string = firstItem.path
    const splitPath: string[] = relativePath.split("/")
    return splitPath[0]
  }

  private static showUploadingRecordsPage() {
    const fileUploadPage: HTMLDivElement | null =
      document.querySelector("#file-upload")
    const uploadProgressPage: HTMLDivElement | null =
      document.querySelector("#upload-progress")

    if (fileUploadPage && uploadProgressPage) {
      fileUploadPage.setAttribute("hidden", "true")
      uploadProgressPage.removeAttribute("hidden")
    }
  }

  private checkIfFolderHasFiles(files: File[] | IFileWithPath[]): void | Error {
    if (files === null || files.length === 0) {
      this.removeFilesAndDragOver()
      return rejectUserItemSelection(
        this.warningMessages?.incorrectItemSelectedMessage,
        this.warningMessages,
        this.successAndRemovalMessageContainer,
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

  private checkNumberOfObjectsDropped(
    droppedObjects: DataTransferItemList | FileList,
    exceptionMessage: string
  ): Error | void {
    if (droppedObjects.length > 1) {
      this.removeFilesAndDragOver()
      return rejectUserItemSelection(
        this.warningMessages?.multipleItemSelectedMessage,
        this.warningMessages,
        this.successAndRemovalMessageContainer,
        exceptionMessage
      )
    }
  }

  private checkNumberOfFoldersOrFilesDropped(
    droppedObjects: DataTransferItemList | FileList,
    exceptionMessage: string
  ): Error | void {
    if (droppedObjects.length > 1) {
      this.removeFilesAndDragOver()
      // Check if the item is folder
      if (droppedObjects[0].type === "") {
        return rejectUserItemSelection(
          this.warningMessages?.multipleFolderSelectedMessage,
          this.warningMessages,
          this.successAndRemovalMessageContainer,
          exceptionMessage
        )
      } else {
        return rejectUserItemSelection(
          this.warningMessages?.multipleItemSelectedMessage,
          this.warningMessages,
          this.successAndRemovalMessageContainer,
          exceptionMessage
        )
      }
    }
  }

  private noErrors(arr: (void | File | Error)[]): arr is File[] {
    const filterFunction = function (el: void | File | Error) {
      return isError(el)
    }
    return arr.filter(filterFunction).length == 0
  }

  private convertFileListToArray(fileList: FileList): File[] | Error {
    const fileListIndexes = [...Array(fileList.length).keys()]
    const fileArr: (void | File | Error)[] = fileListIndexes.map((i) => {
      const file: File = fileList[i]
      if (!file.type) {
        this.removeFilesAndDragOver()
        return rejectUserItemSelection(
          this.warningMessages?.incorrectItemSelectedMessage,
          this.warningMessages,
          this.successAndRemovalMessageContainer,
          "Only files are allowed to be selected"
        )
      }
      return file
    })
    if (this.noErrors(fileArr)) {
      return fileArr
    }
    return fileArr.find((a) => isError(a)) as Error
  }

  private checkIfDroppedItemIsFolder(webkitEntry: any) {
    if (webkitEntry!.isFile) {
      this.removeFilesAndDragOver()
      return rejectUserItemSelection(
        this.warningMessages?.incorrectItemSelectedMessage,
        this.warningMessages,
        this.successAndRemovalMessageContainer,
        "Only folders are allowed to be selected"
      )
    }
  }

  private removeFilesAndDragOver() {
    this.selectedFiles = []
    this.removeDragover()
  }

  private checkForCorrectJudgmentFileExtension(fileName: string) {
    const judgmentFileExtensionsAllowList = [".docx"]

    if (fileName) {
      const indexOfLastDot = fileName.lastIndexOf(".")
      const fileExtension = fileName.slice(indexOfLastDot)
      if (!judgmentFileExtensionsAllowList.includes(fileExtension)) {
        this.removeFilesAndDragOver()
        return rejectUserItemSelection(
          this.warningMessages?.incorrectFileExtensionMessage,
          this.warningMessages,
          this.successAndRemovalMessageContainer,
          "Only MS Word docs are allowed to be selected"
        )
      }
    } else {
      return Error("The file does not have a file name!")
    }
  }
}
