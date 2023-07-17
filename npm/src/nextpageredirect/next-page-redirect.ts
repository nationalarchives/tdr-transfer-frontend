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
  isJudgmentUser: Boolean
): void => {
  if (isJudgmentUser) {
    location.assign(
      `/judgment/${consignmentId}/file-checks?uploadFailed=${uploadFailed.toString()}`
    )
  } else {
    location.assign(
      `/consignment/${consignmentId}/file-checks?uploadFailed=${uploadFailed.toString()}`
    )
  }
}
