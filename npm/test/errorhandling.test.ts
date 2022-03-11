import { handleUploadError, LoggedOutError } from "../src/errorhandling"

beforeEach(() => {
  jest.resetAllMocks()
  jest.resetModules()
})

test("handleUploadError function displays error message", () => {
  setupErrorHtml()

  const mockErrorMessage: string = "some error"
  const mockError = new Error(mockErrorMessage)
  handleUploadError(mockError)
  checkExpectedErrorHtmlState(mockErrorMessage)
})

function checkExpectedErrorHtmlState(expectedRenderedErrorMessage: string) {
  const formElement: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const errorElement: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary.upload-error"
  )
  const errorMessageElement: HTMLParagraphElement | null = document.querySelector(
    ".upload-error__message"
  )

  expect(formElement && formElement.hasAttribute("hidden")).toEqual(true)

  expect(errorElement && errorElement.hasAttribute("hidden")).toEqual(false)

  expect(errorMessageElement && errorMessageElement.textContent).toEqual(
    expectedRenderedErrorMessage
  )
}

function checkExpectedLoginErrorHtmlState(expectedLoginUrl: string) {
  const formElement: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const errorLinkElement: HTMLAnchorElement | null = document.querySelector(
    ".logged-out-error-link"
  )

  expect(formElement && formElement.classList.toString()).toEqual("hide")

  expect(errorLinkElement && errorLinkElement.href).toEqual(expectedLoginUrl)
}

test("handleUploadError function displays error message and throws error with additional information for logged out errors", () => {
  setupErrorHtml()

  const mockError = new LoggedOutError(
    "http://localhost/loginUrl",
    "logged out error"
  )

  handleUploadError(mockError)

  checkExpectedLoginErrorHtmlState("http://localhost/loginUrl")
})

test("handleUploadError function displays timeout error message for an error during upload", () => {
  setupProgressBarErrorHtml()
  const mockErr = new Error("Timeout")
  mockErr.name = "TimeoutError"
  handleUploadError(mockErr)
  checkExpectedUploadProgressErrorState("timeout")
})

test("handleUploadError function displays access denied error message for an error during upload", () => {
  setupProgressBarErrorHtml()
  const mockErr = new Error("Access Denied")
  mockErr.name = "AccessDenied"
  handleUploadError(mockErr)
  checkExpectedUploadProgressErrorState("authentication")
})

test("handleUploadError function displays general error message for an error during upload", async () => {
  setupProgressBarErrorHtml()
  const mockErr = new Error("Unexpected")
  mockErr.name = "UnexpectedError"
  handleUploadError(mockErr)
  checkDefaultMessageAndProgressBarAreHidden()
  checkExpectedUploadProgressErrorState("general")
})

function checkExpectedUploadProgressErrorState(errorSuffix: string) {
  const uploadProgressError: HTMLDivElement | null = document.querySelector(
    "#upload-progress-error"
  )
  const errorElement: HTMLDivElement | null = document.querySelector(
    `upload-progress-error-${errorSuffix}__message`
  )
  expect(errorElement && errorElement.hasAttribute("hidden")).toBeNull()
}

function checkDefaultMessageAndProgressBarAreHidden() {
  const browserMessageAndProgressBar: HTMLDivElement | null = document.querySelector(
    "#progress-bar-and-message"
  )
  expect(browserMessageAndProgressBar?.hasAttribute("hidden")).toEqual(true)
}

function setupErrorHtml() {
  document.body.innerHTML =
    `<div id="file-upload">
      <form id="file-upload-form">
        <div class="govuk-error-summary upload-error hide">
          <p class="upload-error__message">
          </p>
        </div>
        <div class="govuk-error-summary logged-out-error hide">
          <a class="logged-out-error-link"></a>
        </div>
      </form>
    </div>`
}

function setupProgressBarErrorHtml() {
  document.body.innerHTML = `
  <div id="progress-bar" hidden></div>
    <div id="upload-progress-error">
      <div class="govuk-error-summary__body">
          <p class="upload-progress-error-timeout__message" hidden>Timeout error</p>
          <p class="upload-progress-error-authentication__message" hidden>Auth error</p>
          <p class="upload-progress-error-general__message" hidden>General error</p>
      </div>
    </div>
    <div id="progress-bar-and-message">
    <p class="govuk-body">Browser window message</p>
    <div>
        <span id="upload-status-screen-reader"><label for="upload-records-progress-bar" class="govuk-label progress-label"></label></span>
        <div class="progress-bar">
           <div class="progress-display" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div>
        </div>
    </div>
    </div>
  </div>
  `
}

function setupNonErrorHtml() {
  document.body.innerHTML = '<div class="some-class">' + "</div>"
}
