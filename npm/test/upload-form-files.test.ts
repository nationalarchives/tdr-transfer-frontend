import "@testing-library/jest-dom"
import { IFileWithPath } from "@nationalarchives/file-information"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { FileUploader } from "../src/upload"
import { UploadForm, IReader, IWebkitEntry } from "../src/upload/upload-form"
import { createMockKeycloakInstance } from "./utils"
import { IFrontEndInfo } from "../src"
import { UpdateConsignmentStatus } from "../src/updateconsignmentstatus"
import { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js"

interface SubmitEvent extends Event {
  submitter: HTMLElement
}

const mockFileList: (file: File[]) => FileList = (file: File[]) => {
  return {
    length: file.length,
    item: (index: number) => file[index],
    0: file[0],
    1: file[1]
  } as FileList
}

const mockDataTransferItemList: (
  entry: DataTransferItem,
  itemLength: number
) => DataTransferItemList = (entry: DataTransferItem, itemLength: number) => {
  return {
    item: jest.fn(),
    [Symbol.iterator]: jest.fn(),
    add: jest.fn(),
    length: itemLength,
    clear: jest.fn(),
    0: entry,
    remove: jest.fn()
  } as DataTransferItemList
}

const mockGoToNextPage = jest.fn()

const triggerInputEvent: (element: HTMLElement, domEvent: string) => void = (
  element: HTMLElement,
  domEvent: string
) => {
  const event = new CustomEvent(domEvent)
  element.dispatchEvent(event)
}

const dummyFolder = {
  lastModified: 2147483647,
  name: "Mock Folder",
  size: 0,
  type: "",
  webkitRelativePath: ""
} as unknown as File

const dummyFile = {
  lastModified: 2147483647,
  name: "Mock File",
  size: 3008,
  type: "pdf",
  webkitRelativePath: "Parent_Folder"
} as unknown as File

const dummyIFileWithPath = {
  file: dummyFile,
  path: "Parent_Folder",
  webkitRelativePath: "Parent_Folder"
} as IFileWithPath

class MockDom {
  constructor(numberOfFiles: number = 2) {
    if (numberOfFiles === 0) {
      this.entries = [[]]
    } else {
      this.entries = [[], Array(numberOfFiles).fill(this.fileEntry)]
    }
  }

  html = (document.body.innerHTML = `
      <div id="file-upload" class="govuk-grid-row">
          <div class="govuk-grid-column-two-thirds">
              <form id="file-upload-form" data-consignment-id="ee948bcd-ebe3-4dfd-8928-2b2c9c586b40">
                  <div class="govuk-form-group">
                      <div class="drag-and-drop">
                          <div class="govuk-summary-list govuk-file-upload">
                              <div class="govuk-summary-list__row">
                                  <dd id="folder-selection-success" class="govuk-summary-list__value drag-and-drop__success" hidden
                                      tabindex="-1" role="alert" aria-describedby="success-message-text">
                                      <div>
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                              <path d="M25,6.2L8.7,23.2L0,14.1l4-4.2l4.7,4.9L21,2L25,6.2z"></path>
                                          </svg>
                                          <p id="success-message-text">The file "<span id="file-name"></span>" has been selected</p>
                                      </div>
                                  </dd>
                                  <dd id="item-selection-failure" class="govuk-summary-list__value drag-and-drop__failure" hidden
                                      tabindex="-1" role="alert" aria-describedby="non-file-selected-message-text">
                                      <div>
                                          <span class="drag-and-drop__error">
                                              <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                                 <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                              </svg>
                                         </span>
                                          <p id="non-file-selected-message-text">You can only drop a single file</p>
                                      </div>
                                  </dd>
                                  <dd id="nothing-selected-submission-message" class="govuk-summary-list__value drag-and-drop__failure" hidden
                                      tabindex="-1" role="alert" aria-describedby="submission-without-a-file-message-text">
                                      <div>
                                          <span class="drag-and-drop__error">
                                              <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                                 <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                              </svg>
                                          </span>
                                          <p id="submission-without-a-file-message-text">Select a file to upload.</p>
                                      </div>
                                  </dd>
                              </div>
                          </div>
                          <div>
                              <div class="govuk-form-group">
                                  <div class="drag-and-drop__dropzone">
                                      <input type="file" id="file-selection" name="files"
                                          class="govuk-file-upload drag-and-drop__input" multiple
                                      >
                                      <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single file here or</p>
                                      <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
                                          Choose file
                                      </label>
                                  </div>
      
                                  <div class="govuk-button-group">
                                      <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                          Start upload
                                      </button>
      
                                      <a class="govuk-link" href="/">Cancel</a>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>
              </form>
              <!--        Form to redirect user once upload has completed. It sends consignmentId to record processing placeholder page -->
              @form(routes.FileChecksController.recordProcessingPage(consignmentId), Symbol("id") -> "upload-data-form") { }
          </div>
      </div>
      <div id="upload-progress" class="govuk-grid-row" hidden>`)

  dataTransferItemFields = {
    fullPath: "something", // add this to the fileEntry and directoryEntry object
    file: (success: any) => success(dummyFile),
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
    })
  }
  directoryEntry: IWebkitEntry = {
    ...this.dataTransferItemFields,
    createReader: () => this.reader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => this.fileEntry
  }

  dataTransferItem: DataTransferItem = {
    ...this.dataTransferItemFields,
    webkitGetAsEntry: () => this.directoryEntry
  }

  entries: IWebkitEntry[][] = [[], [this.fileEntry, this.fileEntry]]
  batchCount = this.entries.length
  reader: IReader = {
    readEntries: (cb) => {
      this.batchCount = this.batchCount - 1
      cb(this.entries[this.batchCount])
    }
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
      readonly AT_TARGET: number = 0
      readonly BUBBLING_PHASE: number = 0
      readonly CAPTURING_PHASE: number = 0
      readonly NONE: number = 0
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

  selectFolderViaButton: () => void = () => {
    triggerInputEvent(this.fileRetriever!, "change")
  }

  setUpFileUploader(): FileUploader {
    const mockUpdateToken = jest.fn()
    const isTokenExpired = true
    const refreshTokenParsed: KeycloakTokenParsed = {
      exp: Math.round(new Date().getTime() / 1000) + 60
    }
    const isJudgmentUser: boolean = true
    const mockKeycloakInstance: KeycloakInstance = createMockKeycloakInstance(
      mockUpdateToken,
      isTokenExpired,
      refreshTokenParsed,
      isJudgmentUser
    )
    const client = new GraphqlClient(
      "https://example.com",
      mockKeycloakInstance
    )
    const frontendInfo: IFrontEndInfo = {
      apiUrl: "",
      region: "",
      stage: "test",
      uploadUrl: ""
    }
    const uploadMetadata = new ClientFileMetadataUpload(client)
    const updateConsignmentStatus = new UpdateConsignmentStatus(client)
    return new FileUploader(
      uploadMetadata,
      updateConsignmentStatus,
      frontendInfo,
      mockGoToNextPage,
      mockKeycloakInstance
    )
  }

  uploadYourRecordsSection: HTMLElement | null =
    document.querySelector("#file-upload")
  dropzone: HTMLElement | null = document.querySelector(
    ".drag-and-drop__dropzone"
  )
  uploadForm: HTMLFormElement | null =
    document.querySelector("#file-upload-form")
  fileRetriever: HTMLInputElement | null =
    document.querySelector("#file-selection")

  fileRetrievalSuccessMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__success"
  )

  fileNameElement: HTMLElement | null = document.querySelector("#file-name")

  warningMessages: {
    [s: string]: HTMLElement | null
  } = {
    incorrectItemSelectedMessage: document.querySelector(
      "#item-selection-failure"
    ),
    submissionWithoutSelectionMessage: document.querySelector(
      "#nothing-selected-submission-message"
    )
  }

  warningMessagesText: {
    [s: string]: HTMLElement | null
  } = {
    incorrectItemSelectedMessageText: document.querySelector(
      "#non-file-selected-message-text"
    )
  }

  hiddenInputButton: HTMLElement | null =
    document.querySelector("#file-selection")

  submitButton: HTMLElement | null = document.querySelector(
    "#start-upload-button"
  )

  fileUploader = this.setUpFileUploader()

  form = new UploadForm(
    true,
    this.uploadForm!,
    this.fileRetriever!,
    this.dropzone!,
    this.setUpFileUploader
  )

  uploadingRecordsSection = document.querySelector("#upload-progress")
}

test("clicking the submit button, without selecting a file, doesn't reveal the progress bar & disables the buttons on the page", async () => {
  const mockDom = new MockDom()

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")
  expect(mockDom.submitButton).not.toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).not.toHaveAttribute("disabled", "true")
})

test("clicking the submit button, without selecting a file, displays a warning message to the user", async () => {
  const mockDom = new MockDom()

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(
    mockDom.warningMessages.submissionWithoutSelectionMessage
  ).not.toHaveAttribute("hidden", "true")

  expect(mockDom.warningMessages.incorrectItemSelectedMessage).toHaveAttribute(
    "hidden",
    "true"
  )
  expect(mockDom.fileRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("input button updates the page with correct number of files if only 1 file has been selected", () => {
  const mockDom = new MockDom()
  mockDom.fileUploader.initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyFile] }
  mockDom.selectFolderViaButton()

  expect(mockDom.fileRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

  Object.values(mockDom.warningMessages!).forEach(
    (warningMessageElement: HTMLElement | null) => {
      expect(warningMessageElement!).toHaveAttribute("hidden", "true")
    }
  )

  expect(mockDom.fileNameElement!.textContent).toStrictEqual(dummyFile.name)
})

test("dropzone updates the page with correct number of files if only 1 file has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  expect(mockDom.fileRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

  Object.values(mockDom.warningMessages!).forEach(
    (warningMessageElement: HTMLElement | null) =>
      expect(warningMessageElement!).toHaveAttribute("hidden", "true")
  )

  expect(mockDom.fileNameElement!.textContent).toStrictEqual(
    dummyIFileWithPath.file.name
  )
})

test("dropzone updates the page with an error if more than 1 file has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile, dummyFile],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("You are only allowed to drop one file.")
  )

  expect(
    mockDom.warningMessages.incorrectItemSelectedMessage
  ).not.toHaveAttribute("hidden", "true")
  expect(
    mockDom.warningMessages.submissionWithoutSelectionMessage
  ).toHaveAttribute("hidden", "true")
  expect(mockDom.fileRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone updates the page with an error if a file and a folder has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder, dummyFile],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("You are only allowed to drop one file.")
  )

  expect(
    mockDom.warningMessages.incorrectItemSelectedMessage
  ).not.toHaveAttribute("hidden", "true")
  expect(
    mockDom.warningMessages.submissionWithoutSelectionMessage
  ).toHaveAttribute("hidden", "true")
  expect(mockDom.fileRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone updates the page with an error if 1 non-file has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only files are allowed to be selected")
  )

  expect(
    mockDom.warningMessages.incorrectItemSelectedMessage
  ).not.toHaveAttribute("hidden", "true")
  expect(
    mockDom.warningMessages.submissionWithoutSelectionMessage
  ).toHaveAttribute("hidden", "true")
  expect(mockDom.fileRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone clears selected file if an invalid object is dropped after a valid one", async () => {
  const mockDom = new MockDom()
  const validDragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.fileEntry
  )
  const validDragEvent = new validDragEventClass()
  await mockDom.form.handleDroppedItems(validDragEvent)

  const invalidDragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder],
    mockDom.directoryEntry
  )

  expect(mockDom.form.selectedFiles).toHaveLength(1)

  const invalidDragEvent = new invalidDragEventClass()

  try {
    await mockDom.form.handleDroppedItems(invalidDragEvent)
  } catch {}
  expect(mockDom.form.selectedFiles).toHaveLength(0)
})

test("clicking the submit button, after selecting the file, disables the buttons on the page", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.submitButton).toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).toHaveAttribute("disabled", "true")

  /*There is currently no way in Javascript to check if an event has been removed from an element,
   therefore it is not possible to see if the submission code removed the drop event from the dropzone
   */
})

test("clicking the submit button, after selecting a file, hides 'upload file' section & reveals progress bar", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.fileEntry
  )
  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")

  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.uploadYourRecordsSection).toHaveAttribute("hidden", "true")
  expect(mockDom.uploadingRecordsSection).not.toHaveAttribute("hidden")
})
