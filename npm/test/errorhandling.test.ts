import { handleUploadError } from "../src/errorhandling"

beforeEach(() => {
  jest.resetAllMocks()
  jest.resetModules()
})

test("handleUploadError function displays error message and throws error with additional information", () => {
  setupErrorHtml()

  const mockErrorMessage: string = "some error"
  const mockError = new Error(mockErrorMessage)

  expect(() => {
    handleUploadError(mockError, "Some additional information")
  }).toThrowError(new Error("Some additional information: some error"))

  checkExpectedErrorHtmlState(mockErrorMessage)
})

test("handleUploadError function displays error message and throws error without additional information", () => {
  setupErrorHtml()

  const mockErrorMessage: string = "some error"
  const mockError = new Error(mockErrorMessage)

  expect(() => {
    handleUploadError(mockError)
  }).toThrowError(new Error("Upload failed: some error"))

  checkExpectedErrorHtmlState(mockErrorMessage)
})

test("handleUploadError function throws error and does not display error message if error HTML not present", () => {
  setupNonErrorHtml()
  const mockError = new Error("some error")

  expect(() => {
    handleUploadError(mockError)
  }).toThrowError(new Error("Upload failed: some error"))
})

function checkExpectedErrorHtmlState(expectedRenderedErrorMessage: string) {
  const formElement: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const errorElement: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary"
  )
  const errorMessageElement: HTMLParagraphElement | null = document.querySelector(
    ".upload-error__message"
  )

  expect(formElement && formElement.classList.toString()).toEqual("hide")

  expect(errorElement && errorElement.classList.toString()).toEqual(
    "govuk-error-summary upload-error"
  )

  expect(errorMessageElement && errorMessageElement.textContent).toEqual(
    expectedRenderedErrorMessage
  )
}

function setupErrorHtml() {
  document.body.innerHTML =
    '<form id="file-upload-form">' +
    '<div class="govuk-error-summary upload-error hide">' +
    '<p class="upload-error__message">' +
    "</p>" +
    "</div>" +
    "</form>"
}

function setupNonErrorHtml() {
  document.body.innerHTML = '<div class="some-class">' + "</div>"
}
