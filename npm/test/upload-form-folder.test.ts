import { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import "@testing-library/jest-dom"

import {
  dummyIFileWithPath,
  getDummyFile,
  getDummyFolder
} from "./upload-form-utils/mock-files-and-folders"
import { MockUploadFormDom } from "./upload-form-utils/mock-upload-form-dom"
import { htmlForFolderUploadForm } from "./upload-form-utils/html-for-upload-forms"
import { verifyVisibilityOfWarningMessages } from "./upload-form-utils/verify-visibility-of-warning-messages"
import {
  IReader,
  IWebkitEntry
} from "../src/upload/form/get-files-from-drag-event"
import { verifyVisibilityOfSuccessMessage } from "./upload-form-utils/verify-visibility-of-success-message"
import { displaySelectionSuccessMessage } from "../src/upload/form/update-and-display-success-message"

beforeEach(() => {
  document.body.innerHTML = htmlForFolderUploadForm
})

test("clicking the submit button, without selecting a folder, doesn't reveal the progress bar and disable the buttons on the page", async () => {
  const mockDom = new MockUploadFormDom()

  const submitEvent = mockDom.createSubmitEvent()
  await expect(mockDom.form.handleFormSubmission(submitEvent)).resolves.toEqual(
    Error("A submission was made without an item being selected")
  )

  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")
  expect(mockDom.submitButton).not.toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).not.toHaveAttribute("disabled", "true")
})

test("clicking the submit button, without selecting a folder, displays a warning message to the user", async () => {
  const mockDom = new MockUploadFormDom()

  const submitEvent = mockDom.createSubmitEvent()
  await expect(mockDom.form.handleFormSubmission(submitEvent)).resolves.toEqual(
    Error("A submission was made without an item being selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.submissionWithoutSelection!,
    expectedWarningMessageText: "Select a folder to upload."
  })
  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, false)
})

test("input button updates the page with correct folder information if there are 1 or more files in folder", () => {
  const mockDom = new MockUploadFormDom()
  mockDom.getFileUploader().initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
  mockDom.selectFolderViaButton()

  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, true)
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Parent_Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("1 file")
})

test("dropzone updates the page with correct folder information if there are 1 or more files in folder", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, true)
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if there are no files in folder", async () => {
  const mockDom = new MockUploadFormDom(false, 0)
  mockDom.batchCount = mockDom.entries.length
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("The folder is empty")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, false)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with correct folder information if there is a nested folder of files in the main folder", async () => {
  const mockDom = new MockUploadFormDom()

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
    [getDummyFolder()],
    dataTransferItemWithNestedDirectory
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, true)
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with correct folder information if there are valid files and an empty directory", async () => {
  const mockDom = new MockUploadFormDom()

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

  const emptyDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => emptyNestedReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry
  }

  const emptyNestedReader: IReader = {
    readEntries: (cb) => {
      cb([])
    }
  }

  const mockNestedEntries: IWebkitEntry[][] = [
    [],
    [emptyDirectoryEntry],
    [mockDom.directoryEntry]
  ] // have to create an entry here to act as top-level directory
  let nestedDirectoryBatchCount = mockDom.entries.length + 1
  const nestedDirectoryEntryReader: IReader = {
    readEntries: (cb) => {
      nestedDirectoryBatchCount = nestedDirectoryBatchCount - 1
      cb(mockNestedEntries[nestedDirectoryBatchCount])
    }
  }

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    dataTransferItemWithNestedDirectory
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if there is an empty nested folder", async () => {
  const mockDom = new MockUploadFormDom()

  const dataTransferItemWithNestedDirectory: DataTransferItem = {
    ...mockDom.dataTransferItemFields,
    webkitGetAsEntry: () => emptyDirectoryEntry
  }

  const emptyDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => emptyNestedReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry
  }

  const emptyNestedReader: IReader = {
    readEntries: (cb) => {
      cb([])
    }
  }

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    dataTransferItemWithNestedDirectory
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("The folder is empty")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if more than 1 item (2 folders) has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFolder()],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.multipleItemSelected!,
    expectedWarningMessageText: "You must upload a single folder"
  })
  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, false)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if more than 1 item (folder and file) has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFile()],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.multipleItemSelected!,
    expectedWarningMessageText: "You must upload a single folder"
  })
  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, false)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if 1 file has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile()],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("Only folders are allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  verifyVisibilityOfSuccessMessage(mockDom.itemRetrievalSuccessMessage!, false)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone clears selected files if an invalid file is dropped after a valid one", async () => {
  const mockDom = new MockUploadFormDom()
  const validDragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  const validDragEvent = new validDragEventClass()
  await mockDom.form.handleDroppedItems(validDragEvent)

  expect(mockDom.form.selectedFiles).toHaveLength(2)

  const invalidDragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.fileEntry
  )
  const invalidDragEvent = new invalidDragEventClass()

  try {
    await mockDom.form.handleDroppedItems(invalidDragEvent)
  } catch {}
  expect(mockDom.form.selectedFiles).toHaveLength(0)
})

test("clicking the submit button, after selecting a folder, disables the buttons on the page", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
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

test("clicking the submit button, after selecting a folder, hides 'upload folder' section and reveals progress bar", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")

  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.uploadYourRecordsSection).toHaveAttribute("hidden", "true")
  expect(mockDom.uploadingRecordsSection).not.toHaveAttribute("hidden")
})

test("removeSelectedItem function should remove the selected folder", () => {
  const mockDom = new MockUploadFormDom()

  mockDom.form.selectedFiles.push(dummyIFileWithPath)
  mockDom.form.removeSelectedItem(new Event("click"))

  expect(mockDom.form.selectedFiles).toHaveLength(0)
})

test("removeSelectedItem function should hide the success message row", () => {
  const mockDom = new MockUploadFormDom()
  mockDom.getFileUploader().initialiseFormListeners()

  expect(mockDom.successMessageRow).not.toHaveAttribute("hidden", "true")

  displaySelectionSuccessMessage(
    mockDom.form.successMessage,
    mockDom.form.warningMessages
  )
  mockDom.removeButton!.click()
  expect(mockDom.successMessageRow).toHaveAttribute("hidden", "true")
})
