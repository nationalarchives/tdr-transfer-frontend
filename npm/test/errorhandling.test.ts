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
  const mockErrorMessageElement: HTMLParagraphElement | null = document.querySelector(
    ".errorMessage"
  )

  expect(() => {
    handleUploadError(mockError)
  }).toThrowError(new Error("Upload failed: some error"))
})

function checkExpectedErrorHtmlState(expectedRenderedErrorMessage: string) {
  const mockErrorElement: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary"
  )
  const mockErrorMessageElement: HTMLParagraphElement | null = document.querySelector(
    ".errorMessage"
  )

  if (mockErrorElement) {
    expect(mockErrorElement.classList.toString()).toEqual("govuk-error-summary")
  }

  if (mockErrorMessageElement) {
    expect(mockErrorMessageElement.textContent).toEqual(
      expectedRenderedErrorMessage
    )
  }
}

function setupErrorHtml() {
  document.body.innerHTML =
    '<div class="govuk-error-summary hide">' +
    '<p class="errorMessage">' +
    "</p>" +
    "</div>"
}

function setupNonErrorHtml() {
  document.body.innerHTML = 'div class="some-class">' + "</div>"
}
