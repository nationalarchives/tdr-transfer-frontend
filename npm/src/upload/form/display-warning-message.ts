export const rejectUserItemSelection: (
  warningMessageToReveal: HTMLElement | null,
  warningMessagesToHide: {
    [s: string]: HTMLElement | null
  },
  successMessageToHide: HTMLElement | null,
  exceptionMessage: string
) => void | Error = (
  warningMessageToReveal,
  warningMessagesToHide,
  successMessageToHide,
  exceptionMessage
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

  successMessageToHide?.classList.add("govuk-visually-hidden")

  warningMessageToReveal?.removeAttribute("hidden")
  warningMessageToReveal?.focus()
  selectionArea?.classList.add("govuk-form-group--error")

  return Error(exceptionMessage)
}
