import { pageUnloadAction } from "../upload"

export class LoggedOutError extends Error {
  loginUrl: string

  constructor(loginUrl: string, message?: string) {
    super(message)
    this.loginUrl = loginUrl
  }
}

export function handleUploadError(
  error: Error,
  additionalLoggingInfo: string = "Upload failed"
) {
  window.removeEventListener("beforeunload", pageUnloadAction)
  if (error instanceof LoggedOutError) {
    showLoggedOutError(error.loginUrl)
  } else {
    const uploadFormContainer: HTMLFormElement | null =
      document.querySelector("#file-upload")
    //User is still on upload form
    if (uploadFormContainer && !uploadFormContainer.hasAttribute("hidden")) {
      showErrorMessageOnUploadFormHalf(error)
    } else {
      //User is seeing progress bar
      hideBrowserCloseMessageAndProgressBar()
      showErrorMessageOnUploadInProgressHalf(error)
    }
  }
  throw Error(`${additionalLoggingInfo}: ${error.message}`)
}

function renderErrorMessage(message: string) {
  const errorMessage: HTMLParagraphElement | null = document.querySelector(
    ".upload-error__message"
  )
  if (errorMessage) {
    errorMessage.textContent = message
  }
}

function showErrorMessageOnUploadFormHalf(error: Error) {
  const uploadForm: HTMLDivElement | null =
    document.querySelector("#file-upload-form")

  if (uploadForm) {
    uploadForm.setAttribute("hidden", "true")
  }

  const uploadFormError: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary.upload-error"
  )

  if (uploadFormError) {
    uploadFormError.removeAttribute("hidden")
    renderErrorMessage(error.message)
  }
}

function showErrorMessageOnUploadInProgressHalf(error: Error) {
  const uploadProgressError: HTMLDivElement | null = document.querySelector(
    "#upload-progress-error"
  )
  if (uploadProgressError) {
    uploadProgressError.removeAttribute("hidden")
  }
  const getErrorMessageSuffix: (errorName: string) => string = (errorName) => {
    switch (errorName) {
      case "TimeoutError":
        return "timeout"
      case "AccessDenied":
        return "authentication"
      default:
        return "general"
    }
  }
  const uploadProgressErrorMessage: HTMLParagraphElement | null =
    document.querySelector(
      `.upload-progress-error-${getErrorMessageSuffix(error.name)}__message`
    )

  if (uploadProgressErrorMessage) {
    uploadProgressErrorMessage.removeAttribute("hidden")
  }
}

function hideBrowserCloseMessageAndProgressBar() {
  const browserCloseMessageAndProgressBar = document.querySelector(
    "#progress-bar-and-message"
  )
  browserCloseMessageAndProgressBar?.setAttribute("hidden", "true")
}

function showLoggedOutError(login: string) {
  const uploadForm: HTMLFormElement | null =
    document.querySelector("#file-upload-form")
  const progressBarAndMessage: Element | null = document.querySelector(
    "#progress-bar-and-message"
  )
  const loggedOutErrors = document.querySelectorAll(
    ".govuk-error-summary.logged-out-error"
  )
  // eslint-disable-next-line no-undef
  const loginLinks: NodeListOf<HTMLAnchorElement> = document.querySelectorAll(
    ".logged-out-error-link"
  )

  console.log(uploadForm, progressBarAndMessage)
  if (uploadForm) {
    uploadForm.classList.add("hide")
  }

  if (progressBarAndMessage) {
    progressBarAndMessage.classList.add("hide")
  }

  if (loggedOutErrors && loginLinks) {
    loggedOutErrors.forEach((loggedOutError) =>
      loggedOutError.removeAttribute("hidden")
    )
    loginLinks.forEach((loginLink) => (loginLink.href = login))
  }
}
