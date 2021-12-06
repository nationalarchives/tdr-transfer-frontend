import "@testing-library/jest-dom"

import {
  getDummyFile,
  getDummyFolder
} from "./upload-form-utils/mock-files-and-folders"
import { MockUploadFormDom } from "./upload-form-utils/mock-upload-form-dom"
import { htmlForFileUploadForm } from "./upload-form-utils/html-for-file-upload-form"
import { verifyVisibilityOfWarningMessages } from "./upload-form-utils/verify-visibility-of-warning-messages"

beforeEach(() => {
  document.body.innerHTML = htmlForFileUploadForm
})

const judgmentUploadPageSpecificWarningMessages: () => {
  [warningMessage: string]: { [warningMessage: string]: HTMLElement | null }
} = () => {
  return {
    incorrectFileExtension: {
      messageElement: document.querySelector("#incorrect-file-extension"),
      messageElementText: document.querySelector(
        "#non-word-doc-selected-message-text"
      )
    }
  }
}

test("clicking the submit button, without selecting a file, doesn't reveal the progress bar and disables the buttons on the page", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )

  const submitEvent = mockDom.createSubmitEvent()
  await expect(mockDom.form.handleFormSubmission(submitEvent)).rejects.toEqual(
    Error("A submission was made without an item being selected")
  )

  expect(mockDom.uploadingRecordsSection).toHaveAttribute("hidden")
  expect(mockDom.submitButton).not.toHaveAttribute("disabled", "true")
  expect(mockDom.hiddenInputButton).not.toHaveAttribute("disabled", "true")
})

test("clicking the submit button, without selecting a file, displays a warning message to the user", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )

  const submitEvent = mockDom.createSubmitEvent()
  await expect(mockDom.form.handleFormSubmission(submitEvent)).rejects.toEqual(
    Error("A submission was made without an item being selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.submissionWithoutSelection!,
    expectedWarningMessageText: "Select a file to upload."
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("input button updates the page with the file that has been selected, if that file is an MS Word file", () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  mockDom.getFileUploader().initialiseFormListeners()

  const dummyFile = getDummyFile("Mock File.docx", "docx")
  mockDom.uploadForm!.files = {
    files: [dummyFile]
  }
  mockDom.selectFolderViaButton()

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages)
  expect(mockDom.fileNameElement!.textContent).toStrictEqual(dummyFile.name)
})

test("input button updates the page with an error if the file that has been selected is an non-MS Word file", async () => {
  /* Currently with the accept attribute in place, this should not be possible, but the test is added in case it is removed*/

  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  mockDom.getFileUploader().initialiseFormListeners()

  const dummyFile = getDummyFile("Mock File.txt", "txt")
  mockDom.uploadForm!.files = {
    files: [dummyFile]
  }
  await expect(mockDom.form.handleSelectedItems()).rejects.toEqual(
    Error("Only MS Word docs are allowed to be selected")
  )

  expect(mockDom.itemRetrievalSuccessMessage!).toHaveAttribute("hidden", "true")

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectFileExtension!,
    expectedWarningMessageText:
      "You can only upload a Microsoft Word file. Please try again or contact us"
  })
})

test("dropzone updates the page with correct number of files if an MS Word file has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const fileName = "Mock File.docx"
  const fileType = "docx"
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile(fileName, fileType)],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await mockDom.form.handleDroppedItems(dragEvent)

  expect(mockDom.itemRetrievalSuccessMessage!).not.toHaveAttribute(
    "hidden",
    "true"
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages)
  expect(mockDom.fileNameElement!.textContent).toStrictEqual(fileName)
})

test("dropzone updates the page with an error if a non MS Word file has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const fileName = "Mock File.pdf"
  const fileType = "pdf"
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile(fileName, fileType)],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only MS Word docs are allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectFileExtension!,
    expectedWarningMessageText:
      "You can only upload a Microsoft Word file. Please try again or contact us"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("dropzone updates the page with an error if more than 1 file has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [
      getDummyFile("Mock File.docx", "docx"),
      getDummyFile("Mock File.docx", "docx")
    ],
    mockDom.dataTransferItem
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("You are only allowed to drop one file.")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single file"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("dropzone updates the page with an error if a file and a folder has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder(), getDummyFile("Mock File.docx", "docx")],
    mockDom.directoryEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("You are only allowed to drop one file.")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single file"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("dropzone updates the page with 'Only MS Word' error if 1 non-file has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only MS Word docs are allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectFileExtension!,
    expectedWarningMessageText:
      "You can only upload a Microsoft Word file. Please try again or contact us"
  })
  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("dropzone updates the page with 'Only files are allowed' error if 1 non-file with a name ending with acceptable extension has been dropped", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder("Mock Folder.docx")],
    mockDom.fileEntry
  )
  const dragEvent = new dragEventClass()
  await expect(mockDom.form.handleDroppedItems(dragEvent)).rejects.toEqual(
    Error("Only files are allowed to be selected")
  )

  verifyVisibilityOfWarningMessages(mockDom.warningMessages, {
    warningMessageElements: mockDom.warningMessages.incorrectItemSelected!,
    expectedWarningMessageText: "You can only drop a single file"
  })

  expect(mockDom.itemRetrievalSuccessMessage).toHaveAttribute("hidden", "true")
})

test("dropzone clears selected file if an invalid object is dropped after a valid one", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const validDragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile("Mock File.docx", "docx")],
    mockDom.fileEntry
  )
  const validDragEvent = new validDragEventClass()
  await mockDom.form.handleDroppedItems(validDragEvent)

  const invalidDragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFolder()],
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
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile("Mock File.docx", "docx")],
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

test("clicking the submit button, after selecting a file, hides 'upload file' section and reveals progress bar", async () => {
  const mockDom = new MockUploadFormDom(
    true,
    1,
    judgmentUploadPageSpecificWarningMessages()
  )
  const dragEventClass = mockDom.addFilesToDragEvent(
    [getDummyFile("Mock File.docx", "docx")],
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
