export const goToNextPage: (formId: string) => void = (formId: string) => {
  const uploadDataFormRedirect: HTMLFormElement | null =
    document.querySelector(formId)
  if (uploadDataFormRedirect) {
    //do this instead of location redirect?
    // const action = `/consignment/${consignmentId}/file-checks?uploadFailed=${uploadFailed}`
    // uploadDataFormRedirect.action = action
    uploadDataFormRedirect.submit()
  }
}
