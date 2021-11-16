import "@testing-library/jest-dom"

import {
  dummyIFileWithPath,
  dummyFile,
  dummyFolder
} from "./upload-form-utils/mock-files-and-folders"
import { MockUploadFormDom } from "./upload-form-utils/mockUploadFormDom"
import { htmlForFileUploadForm } from "./upload-form-utils/html-for-file-upload-form"

beforeEach(() => {
  document.body.innerHTML = htmlForFileUploadForm
})

test("clicking the submit button, without selecting a file, doesn't reveal the progress bar & disables the buttons on the page", async () => {
  const mockDom = new MockUploadFormDom(true)

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")
  expect(mockDom.submitButton).not.toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).not.toHaveAttribute("disabled", "true")
})

test("clicking the submit button, without selecting a file, displays a warning message to the user", async () => {
  const mockDom = new MockUploadFormDom(true)

  const submitEvent = mockDom.createSubmitEvent()
  await mockDom.form.handleFormSubmission(submitEvent)

  expect(
    mockDom.warningMessages.submissionWithoutSelectionMessage
  ).not.toHaveAttribute("hidden", "true")

  expect(mockDom.warningMessages.incorrectItemSelectedMessage).toHaveAttribute(
    "hidden",
    "true"
  )
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("input button updates the page with the file that has been selected", () => {
  const mockDom = new MockUploadFormDom(true)
  mockDom.getFileUploader().initialiseFormListeners()
  mockDom.uploadForm!.files = { files: [dummyFile] }
  mockDom.selectFolderViaButton()

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
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
  const mockDom = new MockUploadFormDom(true)
  const dragEventClass = mockDom.addFilesToDragEvent(
    [dummyFile],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
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
  const mockDom = new MockUploadFormDom(true)
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
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone updates the page with an error if a file and a folder has been dropped", async () => {
  const mockDom = new MockUploadFormDom(true)
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
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone updates the page with an error if 1 non-file has been dropped", async () => {
  const mockDom = new MockUploadFormDom(true)
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
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")

  expect(
    mockDom.warningMessagesText.incorrectItemSelectedMessageText!.textContent
  ).toStrictEqual("You can only drop a single file")
})

test("dropzone clears selected file if an invalid object is dropped after a valid one", async () => {
  const mockDom = new MockUploadFormDom(true)
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
  const mockDom = new MockUploadFormDom(true)
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
  const mockDom = new MockUploadFormDom(true)
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
