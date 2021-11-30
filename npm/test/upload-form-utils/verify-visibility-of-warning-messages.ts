export const verifyVisibilityOfWarningMessages = (
  warningMessagesThatShouldBeHidden: {
    [warningMessage: string]: HTMLElement | null
  },
  warningMessageThatShouldBeDisplayed: HTMLElement[] = []
) => {
  Object.values(warningMessagesThatShouldBeHidden).forEach(
    (warningMessageElement: HTMLElement | null) => {
      if (!warningMessageThatShouldBeDisplayed.includes(warningMessageElement!))
        expect(warningMessageElement!).toHaveAttribute("hidden", "true")
    }
  )
  warningMessageThatShouldBeDisplayed.forEach(
    (warningMessageElement: HTMLElement | null) =>
      expect(warningMessageElement!).not.toHaveAttribute("hidden", "true")
  )
}