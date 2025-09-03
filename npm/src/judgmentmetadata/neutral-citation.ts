// Minimal TS to toggle the NCN input when the "no NCN" checkbox is checked
export interface ToggleOptions {
  checkboxSelector?: string
  inputSelector?: string
  detailsInputSelector?: string
}

export function initNeutralCitationToggle(options: ToggleOptions = {}): void {
  const {
    checkboxSelector = "#no-ncn",
    inputSelector = "#neutral-citation",
    detailsInputSelector = "#no-ncnHtml"
  } = options

  const checkbox = document.querySelector<HTMLInputElement>(checkboxSelector)
  const textInput = document.querySelector<HTMLInputElement>(inputSelector)
  const detailsInput = document.querySelector<HTMLInputElement>(detailsInputSelector)

  if (!checkbox || !textInput) {
    // Elements not present on this page; nothing to initialise.
    return
  }

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
    // Optionally focus for UX
    textInput.focus()
  }

  const toggle = (): void => {
    if (checkbox.checked) {
      disableInput()
    } else {
      enableInput()
      // Clear any previously entered details in the conditional field when unchecked
      if (detailsInput) {
        detailsInput.value = ""
      }
    }
  }

  // Set initial state based on current checkbox state (covers back/forward cache, server-rendered state, etc.)
  toggle()

  // Listen for changes
  checkbox.addEventListener("change", toggle)
}
