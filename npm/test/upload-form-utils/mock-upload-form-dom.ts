import { UploadForm } from "../../src/upload/form/upload-form"
import {
  getDummyFile,
  mockDataTransferItemList,
  mockFileList
} from "./mock-files-and-folders"
import { FileUploader } from "../../src/upload"
import { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js"
import { createMockKeycloakInstance } from "../utils"
import { IFrontEndInfo } from "../../src"
import { ClientFileMetadataUpload } from "../../src/clientfilemetadataupload"
import {
  IReader,
  IWebkitEntry
} from "../../src/upload/form/get-files-from-drag-event"

interface SubmitEvent extends Event {
  submitter: HTMLElement
}

export class MockUploadFormDom {
  isJudgmentUser: boolean
  entries: IWebkitEntry[][]
  batchCount: number
  reader: IReader
  form: UploadForm

  constructor(
    isJudgmentUser: boolean = false,
    numberOfFiles: number = 2,
    additionalWarningMessages: {
      [warningMessage: string]: { [warningMessage: string]: HTMLElement | null }
    } = {}
  ) {
    this.isJudgmentUser = isJudgmentUser
    this.entries =
      numberOfFiles === 0
        ? [[]]
        : [[], Array(numberOfFiles).fill(this.fileEntry)]

    this.batchCount = this.entries.length
    this.reader = {
      readEntries: (cb) => {
        this.batchCount = this.batchCount - 1
        cb(this.entries[this.batchCount])
      }
    }
    this.form = this.createForm(isJudgmentUser)
    Object.assign(this.warningMessages, additionalWarningMessages)
  }

  createForm: (isJudgmentUser: boolean) => UploadForm = (isJudgmentUser) => {
    return new UploadForm(
      isJudgmentUser,
      this.uploadForm!,
      this.itemRetriever!,
      this.dropzone!,
      this.setUpFileUploader(isJudgmentUser).uploadFiles
    )
  }

  triggerInputEvent: (element: HTMLElement, domEvent: string) => void = (
    element: HTMLElement,
    domEvent: string
  ) => {
    const event = new CustomEvent(domEvent)
    element.dispatchEvent(event)
  }

  dataTransferItemFields = {
    fullPath: "something", // add this to the fileEntry and directoryEntry object
    file: (success: any) => success(getDummyFile()),
    kind: "",
    type: "",
    getAsFile: jest.fn(),
    getAsString: jest.fn()
  }

  fileEntry: IWebkitEntry = {
    ...this.dataTransferItemFields,
    createReader: () => this.reader,
    type: "pdf", // overwrite default "type" value "" as files must have a non-empty value
    isFile: true,
    isDirectory: false,
    webkitGetAsEntry: () => ({
        isFile: true
    }) as FileSystemEntry
  }

  directoryEntry: IWebkitEntry = {
    ...this.dataTransferItemFields,
    createReader: () => this.reader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => this.fileEntry as unknown as FileSystemEntry
  }

  dataTransferItem: DataTransferItem = {
    ...this.dataTransferItemFields,
    webkitGetAsEntry: () => this.directoryEntry as unknown as FileSystemEntry
  }

  addFilesToDragEvent = (
    filesToDrop: File[],
    itemsToDropEntryType: DataTransferItem
  ) => {
    return class MockDragEvent extends MouseEvent {
      constructor() {
        super("drag")
      }

      dataTransfer: DataTransfer = {
        files: mockFileList(filesToDrop),
        dropEffect: "none" as const,
        effectAllowed: "none" as const,
        items: mockDataTransferItemList(
          itemsToDropEntryType,
          filesToDrop.length
        ),
        types: [],
        clearData: jest.fn(),
        getData: jest.fn(),
        setData: jest.fn(),
        setDragImage: jest.fn()
      }
    }
  }

  createSubmitEvent = () => {
    const submitButton = this.submitButton

    class MockSubmitEvent implements SubmitEvent {
      readonly AT_TARGET = 2
      readonly BUBBLING_PHASE = 3
      readonly CAPTURING_PHASE = 1
      readonly NONE = 0
      readonly bubbles: boolean = true
      cancelBubble: boolean = true
      readonly cancelable: boolean = true
      readonly composed: boolean = true
      readonly currentTarget: EventTarget | null = null
      readonly defaultPrevented: boolean = true
      readonly eventPhase: number = 0
      readonly isTrusted: boolean = true
      returnValue: boolean = true
      readonly srcElement: EventTarget | null = null
      readonly target: EventTarget | null = null
      readonly timeStamp: number = 2147483647
      readonly type: string = "submit"
      submitter: HTMLElement = submitButton!

      composedPath(): EventTarget[] {
        return []
      }

      initEvent(type: string, bubbles?: boolean, cancelable?: boolean): void {}

      preventDefault(): void {}

      stopImmediatePropagation(): void {}

      stopPropagation(): void {}
    }

    return new MockSubmitEvent()
  }

  selectItemViaButton: () => void = () => {
    return this.triggerInputEvent(this.itemRetriever!, "change")
  }

  setUpFileUploader(isJudgmentUser: boolean): FileUploader {
    const mockUpdateToken = jest.fn().mockImplementation((number: number) => {
      return new Promise((res, _) => res(true))
    })
    const isTokenExpired = true
    const refreshTokenParsed: KeycloakTokenParsed = {
      exp: Math.round(new Date().getTime() / 1000) + 60
    }

    const mockKeycloakInstance: KeycloakInstance = createMockKeycloakInstance(
      mockUpdateToken,
      isTokenExpired,
      refreshTokenParsed,
      isJudgmentUser
    )

    const frontendInfo: IFrontEndInfo = {
      apiUrl: "",
      region: "",
      stage: "test",
      uploadUrl: ""
    }
    const uploadMetadata = new ClientFileMetadataUpload()
    return new FileUploader(
      uploadMetadata,
      frontendInfo,
      mockKeycloakInstance,
      jest.fn()
    )
  }

  uploadYourRecordsSection: HTMLElement | null =
    document.querySelector("#file-upload")

  dropzone: HTMLElement | null = document.querySelector(
    ".drag-and-drop__dropzone"
  )
  uploadForm: HTMLFormElement | null =
    document.querySelector("#file-upload-form")

  itemRetriever: HTMLInputElement | null =
    document.querySelector("#file-selection")

  fileNameElement: HTMLElement | null = document.querySelector(".file-name")

  folderNameElement: () => HTMLElement | null = () => document.querySelector(".folder-name")
  folderSizeElement: () => HTMLElement | null = () => document.querySelector(".folder-size")

  warningMessages: {
    [warningName: string]: { [s: string]: HTMLElement | null }
  } = {
    incorrectItemSelected: {
      messageElement: document.querySelector("#item-selection-failure"),
      messageElementText: document.querySelector(
        "#wrong-object-type-selected-message-text"
      )
    },
    multipleItemSelected: {
      messageElement: document.querySelector("#multiple-selection-failure"),
      messageElementText: document.querySelector(
        "#multiple-object-type-selected-message-text"
      )
    },
    multipleFolderSelectedMessage: {
      messageElement: document.querySelector("#multiple-folder-selection-failure"),
      messageElementText: document.querySelector(
        "#multiple-folder-selected-message-text"
      )
    },
    submissionWithoutSelection: {
      messageElement: document.querySelector(
        "#nothing-selected-submission-message"
      ),
      messageElementText: document.querySelector(
        "#submission-without-anything-selected-text"
      )
    },
    removedSelectionMessage: {
      messageElement: document.querySelector(
        "#removed-selection-container"
      ),
      messageElementText: document.querySelector(
        "#removed-selection-message-text"
      )
    }
  }

  hiddenInputButton: HTMLElement | null =
    document.querySelector("#file-selection")

  submitButton: HTMLElement | null = document.querySelector(
    "#start-upload-button"
  )

  getFileUploader: () => FileUploader = () =>
    this.setUpFileUploader(this.isJudgmentUser)

  uploadingRecordsSection = document.querySelector("#upload-progress")

  successAndRemovalMessageContainer: HTMLElement | null = document.querySelector(
    "#success-and-removal-message-container"
  )

  successMessageContainer: HTMLElement | null = document.querySelector(
    "#item-selection-success-container"
  )

  removeButton: HTMLElement | null = document.querySelector("#remove-file-btn")
}
