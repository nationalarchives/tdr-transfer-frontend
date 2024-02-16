export const displayChecksCompletedBanner: () => void = () => {
  const checksCompletedBanner: HTMLDivElement | null = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton: HTMLDivElement | null = document.querySelector(
    "#file-checks-continue"
  )

  const reasonDisabled: HTMLParagraphElement | null =
    document.querySelector("#reason-disabled")

  if (checksCompletedBanner && continueButton && reasonDisabled) {
    checksCompletedBanner.removeAttribute("hidden")
    checksCompletedBanner.focus()
    continueButton.classList.remove("govuk-button--disabled")
    continueButton.setAttribute("aria-disabled", "false")
    continueButton.removeAttribute("aria-describedby")
    reasonDisabled.remove()
  }
}
