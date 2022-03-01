export const goToNextPage: (formId: string) => void = (formId: string) => {
  const uploadDataFormRedirect: HTMLFormElement | null =
    document.querySelector(formId)
  if (uploadDataFormRedirect) {
    uploadDataFormRedirect.submit()
  }
}
