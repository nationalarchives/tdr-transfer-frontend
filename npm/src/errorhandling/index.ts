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
  if (error instanceof LoggedOutError) {
    showLoggedOutError(error.loginUrl)
  } else {
    const uploadForm: HTMLFormElement | null = document.querySelector(
      "#file-upload"
    )
    //User is still on upload form
    if (uploadForm && !uploadForm.hasAttribute("hidden")) {
      const uploadFormError: HTMLDivElement | null = document.querySelector(
        ".govuk-error-summary.upload-error"
      )

      if (uploadForm) {
        uploadForm.classList.add("hide")
      }

      if (uploadFormError) {
        uploadFormError.removeAttribute("hidden")
        renderErrorMessage(error.message)
      }
    } else {
      //User is seeing progress bar
      const uploadProgressError: HTMLDivElement | null = document.querySelector(
        "#upload-progress-error"
      )
      if (uploadProgressError) {
        uploadProgressError.removeAttribute("hidden")
      }
      const getErrorMessageSuffix: (errorName: string) => string = (
        errorName
      ) => {
        if (errorName === "TimeoutError") {
          return "timeout"
        } else if (errorName === "AccessDenied") {
          return "authentication"
        } else {
          return "general"
        }
      }
      const uploadProgressErrorMessage: HTMLParagraphElement | null = document.querySelector(
        `.upload-progress-error-${getErrorMessageSuffix(error.name)}__message`
      )
      if (uploadProgressErrorMessage) {
        uploadProgressErrorMessage.removeAttribute("hidden")
      }
    }
  }
  throw Error(additionalLoggingInfo + ": " + error.message)
}

function renderErrorMessage(message: string) {
  const errorMessage: HTMLParagraphElement | null = document.querySelector(
    ".upload-error__message"
  )
  if (errorMessage) {
    errorMessage.textContent = message
  }
}

function showLoggedOutError(login: string) {
  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const loggedOutError: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary.logged-out-error"
  )
  const loginLink: HTMLAnchorElement | null = document.querySelector(
    ".logged-out-error-link"
  )
  if (uploadForm) {
    uploadForm.classList.add("hide")
  }

  if (loggedOutError && loginLink) {
    loggedOutError.setAttribute("hidden", "true")
    loginLink.href = login
  }
}
