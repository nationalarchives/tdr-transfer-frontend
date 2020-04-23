export function handleUploadError(
  error: Error,
  additionalLoggingInfo: string = "Upload failed"
) {
  const uploadFormError: HTMLDivElement | null = document.querySelector(
    ".govuk-error-summary"
  )

  if (uploadFormError) {
    uploadFormError.classList.remove("hide")
    renderErrorMessage(error.message)
  }

  throw Error(additionalLoggingInfo + ": " + error.message)
}

function renderErrorMessage(message: string) {
  const errorMessage: HTMLParagraphElement | null = document.querySelector(
    ".errorMessage"
  )
  if (errorMessage) {
    errorMessage.textContent = message
  }
}
