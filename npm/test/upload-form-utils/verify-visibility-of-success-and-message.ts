export const verifyVisibilityOfSuccessAndRemovalMessage = (
  messageElement: HTMLElement,
  shouldBeVisible: boolean,
  errorHasOccurred: boolean = true
): void => {
  const selectionArea = document.querySelector("#selection-area")
  shouldBeVisible || !errorHasOccurred
    ? expect(selectionArea!).not.toHaveClass("govuk-form-group--error")
    : expect(selectionArea!).toHaveClass("govuk-form-group--error")

  if (shouldBeVisible) {
    expect(messageElement!).not.toHaveAttribute("hidden", "true")
  } else {
    expect(messageElement!).toHaveAttribute("hidden", "true")
  }
}
