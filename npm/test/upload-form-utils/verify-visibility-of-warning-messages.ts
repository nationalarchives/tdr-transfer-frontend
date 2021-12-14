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
    ).toStrictEqual(
      warningMessageThatShouldBeDisplayed?.expectedWarningMessageText
    )
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
