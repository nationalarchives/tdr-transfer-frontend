export const verifyVisibilityOfWarningMessages = (
  warningMessageElementsAndText: {
    [warningName: string]: { [s: string]: HTMLElement | null }
  },
  warningMessageThatShouldBeDisplayed?: {
    warningMessageElements: { [elementName: string]: HTMLElement | null }
    expectedWarningMessageText: string
  }
): void => {
  const warningMessageThatShouldBeDisplayedElement =
    warningMessageThatShouldBeDisplayed
      ? warningMessageThatShouldBeDisplayed.warningMessageElements
          .messageElement
      : undefined

  if (warningMessageThatShouldBeDisplayedElement) {
    expect(warningMessageThatShouldBeDisplayedElement).not.toHaveAttribute(
      "hidden",
      "true"
    )

    expect(
      warningMessageThatShouldBeDisplayed?.warningMessageElements
        .messageElementText!.textContent
    ).toContain(warningMessageThatShouldBeDisplayed?.expectedWarningMessageText)

    const selectionArea = document.querySelector("#selection-area")
    expect(selectionArea!).toHaveClass("govuk-form-group--error")
  }

  const warningMessageElements: any = Object.values(
    warningMessageElementsAndText
  ).map(
    (warningMessageElementsAndText) =>
      warningMessageElementsAndText.messageElement
  )
  warningMessageElements.forEach(
    (warningMessageElement: HTMLElement | null) => {
      if (warningMessageElement! !== warningMessageThatShouldBeDisplayedElement)
        expect(warningMessageElement!).toHaveAttribute("hidden", "true")
    }
  )
}
