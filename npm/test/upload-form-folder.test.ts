import "@testing-library/jest-dom"

import {
  dummyIFileWithPath,
  getDummyFile,
  getDummyFolder
} from "./upload-form-utils/mock-files-and-folders"
import { MockUploadFormDom } from "./upload-form-utils/mock-upload-form-dom"
import { htmlForFolderUploadForm } from "./upload-form-utils/html-for-file-upload-form"
import { IReader, IWebkitEntry } from "../src/upload/upload-form"
import { verifyVisibilityOfWarningMessages } from "./upload-form-utils/verify-visibility-of-warning-messages"

beforeEach(() => {
  document.body.innerHTML = htmlForFolderUploadForm
})

test("clicking the submit button, without selecting a folder, doesn't reveal the progress bar and disable the buttons on the page", async () => {
  const mockDom = new MockUploadFormDom()

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")
  expect(mockDom.submitButton).not.toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).not.toHaveAttribute("disabled", "true")
})

test("clicking the submit button, without selecting a folder, displays a warning message to the user", async () => {
  const mockDom = new MockUploadFormDom()

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.submissionWithoutSelection!,
    expectedWarningMessageText: "Select a folder to upload."
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("input button updates the page with correct folder information if there are 1 or more files in folder", () => {
  const mockDom = new MockUploadFormDom()
  mockDom.getFileUploader().initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
  mockDom.selectFolderViaButton()

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

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

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

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
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
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

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if more than 1 item (2 folders) has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFolder()],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

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
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(mockDom.folderNameElement!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if 1 non-folder has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile()],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only folders are allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single folder"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

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
