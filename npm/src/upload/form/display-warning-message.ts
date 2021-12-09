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

  for (const warningMessage of warningMessages) {
    if (warningMessage != warningMessageToReveal) {
      warningMessage?.setAttribute("hidden", "true")
    }
  }

  successMessageToHide?.setAttribute("hidden", "true")

  warningMessageToReveal?.removeAttribute("hidden")
  warningMessageToReveal?.focus()

  throw new Error(exceptionMessage)
}
