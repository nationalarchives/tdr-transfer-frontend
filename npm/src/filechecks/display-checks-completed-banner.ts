export const displayChecksCompletedBanner: () => void = () => {
  const checksCompletedBanner: HTMLDivElement | null = document.querySelector(
    "#file-checks-completed-banner"
  )
  if (checksCompletedBanner) {
    checksCompletedBanner.removeAttribute("hidden")
    checksCompletedBanner.focus()
  }
  const continueButton: HTMLDivElement | null = document.querySelector(
    "#file-checks-continue"
  )
  if (continueButton) {
    continueButton.classList.remove("govuk-button--disabled")
    continueButton.removeAttribute("disabled")
  }
}
