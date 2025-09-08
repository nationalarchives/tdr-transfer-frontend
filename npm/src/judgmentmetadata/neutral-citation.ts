// Minimal TS to toggle the NCN input when the "no NCN" checkbox is checked
export interface ToggleOptions {
  checkboxSelector?: string
  inputSelector?: string
  detailsInputSelector?: string
}

/**
 * Initialise the neutral citation number (NCN) input enable/disable toggle.
 */
export function initialiseNeutralCitationToggle(
  options: ToggleOptions = {}
): void {
  const {
    checkboxSelector = "#no-ncn",
    inputSelector = "#neutral-citation",
    detailsInputSelector = "#no-ncnReference" // updated to match template field id
  } = options

  const checkbox = document.querySelector<HTMLInputElement>(checkboxSelector)
  const textInput = document.querySelector<HTMLInputElement>(inputSelector)
  const detailsInput =
    document.querySelector<HTMLInputElement>(detailsInputSelector)

  if (!checkbox || !textInput) {
    return
  }

  if (checkbox.dataset.ncnToggleInitialised === "true") {
    return
  }
  checkbox.dataset.ncnToggleInitialised = "true"

  const DISABLED_CLASS = "govuk-input--disabled"

  const disableInput = (): void => {
    textInput.value = ""
    textInput.disabled = true
    textInput.setAttribute("aria-disabled", "true")
    textInput.classList.add(DISABLED_CLASS)
  }

  const enableInput = (): void => {
    textInput.disabled = false
    textInput.removeAttribute("aria-disabled")
    textInput.classList.remove(DISABLED_CLASS)
    textInput.focus()
  }

  const toggle = (): void => {
    if (checkbox.checked) {
      disableInput()
    } else {
      enableInput()
      if (detailsInput) {
        detailsInput.value = ""
      }
    }
  }

  toggle()
  checkbox.addEventListener("change", toggle)
}
