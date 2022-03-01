export const verifyVisibilityOfSuccessMessage = (
  messageElement: HTMLElement,
  shouldBeVisible: boolean
): void => {
  const selectionArea = document.querySelector("#selection-area")

  if (shouldBeVisible) {
    expect(messageElement!).not.toHaveAttribute("hidden", "true")
    expect(selectionArea!).not.toHaveClass("govuk-form-group--error")
  } else {
    expect(messageElement!).toHaveAttribute("hidden", "true")
    expect(selectionArea!).toHaveClass("govuk-form-group--error")
  }
}
