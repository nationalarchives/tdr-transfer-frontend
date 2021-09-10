export const displayChecksCompletedBanner: () => void = () => {
  const checksCompletedBanner: HTMLDivElement | null = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton: HTMLDivElement | null = document.querySelector(
    "#file-checks-continue"
  )

  if (checksCompletedBanner && continueButton) {
    checksCompletedBanner.removeAttribute("hidden")
    checksCompletedBanner.focus()
    continueButton.classList.remove("govuk-button--disabled")
    continueButton.removeAttribute("disabled")
  }
}
