export const goToNextPage: () => void = () => {
  const hiddenRedirectLink: HTMLFormElement | null =
    document.querySelector("#next-page-value")
  if (hiddenRedirectLink)
    window.location.href = hiddenRedirectLink.getAttribute("value")!
}
