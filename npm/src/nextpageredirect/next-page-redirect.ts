export const goToNextPage: (formId: string) => void = (formId: string) => {
  const uploadDataFormRedirect: HTMLFormElement | null =
    document.querySelector(formId)
  if (uploadDataFormRedirect) {
    uploadDataFormRedirect.submit()
  }
}

export const goToFileChecksPage = (
  consignmentId: string,
  uploadFailed: String,
  isJudgmentUser: Boolean,
  errorMessage?: string
): void => {
  const pathPrefix = isJudgmentUser ? "judgment" : "consignment"
  const errorParam = errorMessage
    ? `&error=${encodeURIComponent(errorMessage)}`
    : ""
  location.assign(
    `/${pathPrefix}/${consignmentId}/file-checks?uploadFailed=${uploadFailed.toString()}${errorParam}`
  )
}
