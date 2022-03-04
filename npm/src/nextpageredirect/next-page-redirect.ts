export const goToNextPage: () => void = () => {
  const uploadDataFormRedirect: HTMLFormElement | null =
    document.querySelector("#next-page-value")
  if (uploadDataFormRedirect) {
    window.location.href
    window.location.href = uploadDataFormRedirect.getAttribute("value")!
  }
}
