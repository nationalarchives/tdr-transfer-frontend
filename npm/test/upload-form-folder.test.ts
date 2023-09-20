import fetchMock, { enableFetchMocks } from "jest-fetch-mock"

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
import { verifyVisibilityOfSuccessAndRemovalMessage } from "./upload-form-utils/verify-visibility-of-success-and-message"
import { displaySelectionSuccessMessage } from "../src/upload/form/update-and-display-success-message"

jest.mock("uuid", () => "eb7b7961-395d-4b4c-afc6-9ebcadaf0150")

beforeEach(() => {
  fetchMock.resetMocks()
  fetchMock.mockResponse(JSON.stringify({ updateConsignmentStatus: 1 }))
  document.body.innerHTML = htmlForFolderUploadForm
})

const verifyAllMessagesAreHidden = (mockDom: MockUploadFormDom) => {
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false,
    false
  )
}

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
  verifyAllMessagesAreHidden(mockDom)

  const submitEvent = mockDom.createSubmitEvent()
  await expect(mockDom.form.handleFormSubmission(submitEvent)).resolves.toEqual(
    Error("A submission was made without an item being selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.submissionWithoutSelection!,
    expectedWarningMessageText: "Select a folder to upload."
  })
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false
  )
})

test("input button updates the page with correct folder information if there are 1 or more files in folder", () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  mockDom.getFileUploader().initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
  mockDom.selectItemViaButton()

  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    true
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("Parent_Folder")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("1 file")
})

test("dropzone updates the page with correct folder information if there are 1 or more files in folder", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    true
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if there are no files in folder", async () => {
  const mockDom = new MockUploadFormDom(false, 0)
  mockDom.batchCount = mockDom.entries.length
  verifyAllMessagesAreHidden(mockDom)

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("The folder is empty")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.emptyItemSelected!,
    expectedWarningMessageText: "You cannot upload an empty folder."
  })
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false
  )

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("")
})

test("dropzone updates the page with correct folder information if there is a nested folder of files in the main folder", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dataTransferItemWithNestedFolder: DataTransferItem = {
    ...mockDom.dataTransferItemFields,
    webkitGetAsEntry: () => nestedDirectoryEntry as unknown as FileSystemEntry
  }

  const nestedDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => nestedDirectoryEntryReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry as unknown as FileSystemEntry
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
    dataTransferItemWithNestedFolder
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    true
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with correct folder information if there are valid files and an empty folder", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dataTransferItemWithNestedFolder: DataTransferItem = {
    ...mockDom.dataTransferItemFields,
    webkitGetAsEntry: () => nestedDirectoryEntry as unknown as FileSystemEntry
  }

  const nestedDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => nestedDirectoryEntryReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry as unknown as FileSystemEntry
  }

  const emptyDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => emptyNestedReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry as unknown as FileSystemEntry
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
  ] // have to create an entry here to act as top-level folder
  let nestedDirectoryBatchCount = mockDom.entries.length + 1
  const nestedDirectoryEntryReader: IReader = {
    readEntries: (cb) => {
      nestedDirectoryBatchCount = nestedDirectoryBatchCount - 1
      cb(mockNestedEntries[nestedDirectoryBatchCount])
    }
  }

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    dataTransferItemWithNestedFolder
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  expect(mockDom.successAndRemovalMessageContainer!).not.toHaveAttribute(
    "hidden",
    "true"
  )
  verifyVisibilityOfWarningMessages(mockDom.warningMessages)

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("Mock Folder")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("2 files")
})

test("dropzone updates the page with an error if there is an empty nested folder", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dataTransferItemWithNestedFolder: DataTransferItem = {
    ...mockDom.dataTransferItemFields,
    webkitGetAsEntry: () => emptyDirectoryEntry as unknown as FileSystemEntry
  }

  const emptyDirectoryEntry: IWebkitEntry = {
    ...mockDom.dataTransferItemFields,
    createReader: () => emptyNestedReader,
    isFile: false,
    isDirectory: true,
    name: "Mock Folder",
    webkitGetAsEntry: () => mockDom.fileEntry as unknown as FileSystemEntry
  }

  const emptyNestedReader: IReader = {
    readEntries: (cb) => {
      cb([])
    }
  }

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    dataTransferItemWithNestedFolder
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("The folder is empty")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.emptyItemSelected!,
    expectedWarningMessageText: "You cannot upload an empty folder."
  })
  expect(mockDom.successAndRemovalMessageContainer).toHaveClass(
    "govuk-visually-hidden"
  )

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if more than 1 item (2 folders) has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFolder()],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements:
      mockDom.warningMessages.multipleFolderSelectedMessage!,
    expectedWarningMessageText:
      "You can only upload one top-level folder per consignment. However, that folder can contain multiple files and sub folders."
  })
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false
  )

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if more than 1 item (folder and file) has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFile()],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).resolves.toEqual(
    Error("Only one folder is allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements:
      mockDom.warningMessages.multipleFolderSelectedMessage!,
    expectedWarningMessageText:
      "You can only upload one top-level folder per consignment. However, that folder can contain multiple files and sub folders."
  })
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false
  )

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("")
})

test("dropzone updates the page with an error if 1 file has been dropped", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

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
    expectedWarningMessageText: "You can only drop a single folder."
  })
  verifyVisibilityOfSuccessAndRemovalMessage(
    mockDom.successAndRemovalMessageContainer!,
    false
  )

  expect(mockDom.folderNameElement()!.textContent).toStrictEqual("")
  expect(mockDom.folderSizeElement()!.textContent).toStrictEqual("")
})

test("dropzone clears selected files if an invalid file is dropped after a valid one", async () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)

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
  verifyAllMessagesAreHidden(mockDom)

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
  verifyAllMessagesAreHidden(mockDom)

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
  verifyAllMessagesAreHidden(mockDom)

  mockDom.form.selectedFiles.push(dummyIFileWithPath)
  mockDom.form.removeSelectedItem(new Event("click"))

  expect(mockDom.form.selectedFiles).toHaveLength(0)
})

test("removeSelectedItem function should hide the success message row and display the folder removal message when 'Remove' button is clicked", () => {
  const mockDom = new MockUploadFormDom()
  verifyAllMessagesAreHidden(mockDom)
  mockDom.getFileUploader().initialiseFormListeners()

  mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
  mockDom.selectItemViaButton()

  expect(mockDom.successAndRemovalMessageContainer).not.toHaveAttribute(
    "hidden",
    "true"
  )

  mockDom.removeButton!.click()

  expect(mockDom.successMessageContainer).toHaveAttribute("hidden", "true")

  verifyVisibilityOfWarningMessages(
    mockDom.warningMessages,
    {
      warningMessageElements: mockDom.warningMessages.removedSelectionMessage!,
      expectedWarningMessageText: `The folder \"Parent_Folder\" (containing 1 file) has been removed. Select a folder.`
    },
    false
  )
})

test(
  "removeSelectedItem function should hide the folder removal message and display the success message " +
    "when a user reselects a folder after having removed one prior",
  () => {
    const mockDom = new MockUploadFormDom()
    verifyAllMessagesAreHidden(mockDom)
    mockDom.getFileUploader().initialiseFormListeners()

    mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
    mockDom.selectItemViaButton()

    expect(mockDom.successAndRemovalMessageContainer).not.toHaveAttribute(
      "hidden",
      "true"
    )

    mockDom.removeButton!.click()

    mockDom.uploadForm!.files = { files: [dummyIFileWithPath] }
    mockDom.selectItemViaButton()

    expect(mockDom.successMessageContainer).not.toHaveAttribute(
      "hidden",
      "true"
    )
    expect(
      mockDom.warningMessages.removedSelectionMessage.messageElement!
    ).toHaveAttribute("hidden", "true")
  }
)
