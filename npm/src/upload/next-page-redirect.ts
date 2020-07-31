export const goToNextPage: () => void = () => {
  const uploadDataFormRedirect: HTMLFormElement | null = document.querySelector(
    "#upload-data-form"
  )
  if (uploadDataFormRedirect) {
    uploadDataFormRedirect.submit()
  }
}
