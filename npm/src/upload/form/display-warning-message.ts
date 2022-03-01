export const rejectUserItemSelection = (
  warningMessageToReveal: HTMLElement | null,
  warningMessagesToHide: {
    [s: string]: HTMLElement | null
  },
  successMessageToHide: HTMLElement | null,
  exceptionMessage: string
) => {
  const warningMessages: (HTMLElement | null)[] = Object.values(
    warningMessagesToHide
  )
  const selectionArea = document.querySelector("#selection-area")

  for (const warningMessage of warningMessages) {
    if (warningMessage != warningMessageToReveal) {
      warningMessage?.setAttribute("hidden", "true")
    }
  }

  successMessageToHide?.setAttribute("hidden", "true")

  warningMessageToReveal?.removeAttribute("hidden")
  warningMessageToReveal?.focus()
  selectionArea?.classList.add("govuk-form-group--error")

  throw new Error(exceptionMessage)
}
