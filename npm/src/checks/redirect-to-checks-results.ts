export const redirectToChecksResults: (
  navigate?: (url: string) => void
) => void | Error = (navigate = (url: string) => location.assign(url)) => {
  const resultsUrlElement: HTMLInputElement | null = document.querySelector(
    "#fileChecksResultsUrl"
  )
  if (!resultsUrlElement || !resultsUrlElement.value) {
    return Error("No checks results url provided")
  }
  navigate(resultsUrlElement.value)
}
