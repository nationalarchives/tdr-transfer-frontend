import "@testing-library/jest-dom"
import { IFileWithPath } from "@nationalarchives/file-information"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { FileUploader } from "../src/upload"
import { UploadForm, IReader, IWebkitEntry } from "../src/upload/upload-form"
import { mockKeycloakInstance } from "./utils"

const mockFileList: (file: File[]) => FileList = (file: File[]) => {
  return {
    length: file.length,
    item: (index: number) => {
      return file[index]
    }
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

class MockDom {
  constructor(numberOfFiles: number = 2) {
    if (numberOfFiles === 0) {
      this.entries = [[]]
    } else {
      this.entries = [[], Array(numberOfFiles).fill(this.fileEntry)]
    }
  }

  html = (document.body.innerHTML = `
  <form id="file-upload-form" data-consignment-id="@consignmentId">
            <div class="govuk-form-group">
                <div class="drag-and-drop">
                    <div class="govuk-summary-list">
                        <div class="govuk-summary-list__row">
                            <dd class="govuk-summary-list__value drag-and-drop__success hide">
                                @greenTickMark()
                                The folder "<span id="folder-name"></span>" (containing <span id="folder-size"></span>) has been selected
                            </dd>
                            <dd class="govuk-summary-list__value drag-and-drop__failure hide">
                                @redWarningSign()
                                @Messages("upload.dragAndDropErrorMessage")
                            </dd>
                        </div>
                    </div>
                    <div>
                        <div class="govuk-form-group">
                            <div class="drag-and-drop__dropzone">
                                <input type="file" id="file-selection" name="files" class="govuk-file-upload drag-and-drop__input" webkitdirectory>
                                <p class="govuk-body drag-and-drop__hint-text">@Messages("upload.dragAndDropHintText")</p>
                                <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">@Messages("upload.chooseFolderLink")</label>
                            </div>
                            <div class="govuk-warning-text">
                                <span class="govuk-warning-text__icon" aria-hidden="true">!</span>
                                <strong class="govuk-warning-text__text">
                                    <span class="govuk-warning-text__assistive">Warning</span>
                                    @Messages("upload.fileExtensionWarning")
                                </strong>
                            </div>
                            <details class="govuk-details" data-module="govuk-details">
                                <summary class="govuk-details__summary">
                                    <span class="govuk-details__summary-text">
                                        @Messages("upload.fileExtensionTitle")
                                    </span>
                                </summary>
                                <div class="govuk-details__text">
                                    @Messages("upload.fileExtensionSummary")
                                </div>
                            </details>
                            <button class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                @Messages("upload.continueLink")
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </form>`)

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

  mockFileList: (file: File[]) => FileList = (file: File[]) => {
    return {
      length: file.length,
      item: (index: number) => {
        return file[index]
      }
    } as FileList
  }

  mockDataTransferItemList: (
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

  addFilesToDragEvent = (
    folderToDrop: File[],
    itemsToDropEntryType: DataTransferItem
  ) => {
    return class MockDragEvent extends MouseEvent {
      constructor() {
        super("drag")
      }
      dataTransfer = {
        files: mockFileList(folderToDrop),
        dropEffect: "",
        effectAllowed: "",
        items: mockDataTransferItemList(
          itemsToDropEntryType,
          folderToDrop.length
        ),
        types: [],
        clearData: jest.fn(),
        getData: jest.fn(),
        setData: jest.fn(),
        setDragImage: jest.fn()
      }
    }
  }

  dropzone: HTMLElement | null = document.querySelector(
    ".drag-and-drop__dropzone"
  )
  uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  folderRetriever: HTMLInputElement | null = document.querySelector(
    "#file-selection"
  )

  folderRetrievalSuccessMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__success"
  )

  folderRetrievalfailureMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__failure"
  )
  folderNameElement: HTMLElement | null = document.querySelector("#folder-name")
  folderSizeElement: HTMLElement | null = document.querySelector("#folder-size")

  form = new UploadForm(this.uploadForm!, this.folderRetriever!, this.dropzone!)
  fileUploader = this.setUpFileUploader()

  setUpFileUploader(): FileUploader {
    const client = new GraphqlClient(
      "https://example.com",
      mockKeycloakInstance
    )
    const uploadMetadata = new ClientFileMetadataUpload(client)
    return new FileUploader(
      uploadMetadata,
      "identityId",
      "test",
      mockGoToNextPage
    )
  }
  selectFolderViaButton: () => void = () => {
    triggerInputEvent(this.folderRetriever!, "change")
  }
}

const mockGoToNextPage = jest.fn()

const triggerInputEvent: (element: HTMLElement, domEvent: string) => boolean = (
  element: HTMLElement,
  domEvent: string
) => {
  const event = new CustomEvent(domEvent)
  return element.dispatchEvent(event)
}

const dummyFolder = {
  lastModified: 2147483647,
  name: "Mock Folder",
  size: 0,
  type: ""
} as File

const dummyFile = {
  lastModified: 2147483647,
  name: "Mock File",
  size: 3008,
  type: "pdf"
} as File

const dummyIFileWithPath = {
  file: dummyFile,
  path: "Parent_Folder",
  webkitRelativePath: "Parent_Folder"
} as IFileWithPath

test("Input button updates the page with correct folder information if there are 1 or more files in folder", () => {
  const mockDom = new MockDom()
  mockDom.fileUploader.initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
  mockDom.selectFolderViaButton()

  expect(mockDom.folderRetrievalSuccessMessage!).not.toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Parent_Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("1 file")
})

test("drop event is triggerable", () => {
  const mockDom = new MockDom()
  expect(triggerInputEvent(mockDom.dropzone!, "drop")).toBe(true)
})

test("dropzone updates the page with correct folder information if there are 1 or more files in folder", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDropppedItems(dragEvent)

  expect(mockDom.folderRetrievalSuccessMessage!).not.toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if there are no files in folder", async () => {
  const mockDom = new MockDom(0)
  mockDom.batchCount = mockDom.entries.length
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDropppedItems(dragEvent)).rejects.toEqual(
    Error("No files selected")
  )

  expect(mockDom.folderRetrievalSuccessMessage!).toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).not.toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with correct folder information if there is a nested folder of files in the main folder", async () => {
  const mockDom = new MockDom()

  const dataTransferItemWithNestedDirectory: DataTransferItem = {
    ...mockDom.dataTransferItemFields,
    webkitGetAsEntry: () => nestedDirectoryEntry
  }

  const nestedDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => nestedDirectoryEntryReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry
  }

  const mockNestedEntries: IWebkitEntry[][] = [[], [mockDom.directoryEntry]] // have to create an entry here to act as top-level directory
  let nestedDirectoryBatchCount = mockDom.entries.length
  const nestedDirectoryEntryReader: IReader = {
    readEntries: (cb) => {
      nestedDirectoryBatchCount = nestedDirectoryBatchCount - 1
      cb(mockNestedEntries[nestedDirectoryBatchCount])
    }
  }

  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder],
    dataTransferItemWithNestedDirectory
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDropppedItems(dragEvent)

  expect(mockDom.folderRetrievalSuccessMessage!).not.toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if more than 1 item (2 folders) has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder, dummyFolder],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDropppedItems(dragEvent)).rejects.toEqual(
    Error("No files selected")
  )

  expect(mockDom.folderRetrievalSuccessMessage!).toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).not.toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if more than 1 item (folder and file) has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFolder, dummyFile],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDropppedItems(dragEvent)).rejects.toEqual(
    Error("No files selected")
  )

  expect(mockDom.folderRetrievalSuccessMessage!).toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).not.toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if 1 non-folder has been dropped", async () => {
  const mockDom = new MockDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDropppedItems(dragEvent)).rejects.toEqual(
    Error("No files selected")
  )

  expect(mockDom.folderRetrievalSuccessMessage!).toHaveClass("hide")
  expect(mockDom.folderRetrievalfailureMessage!).not.toHaveClass("hide")
  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})
