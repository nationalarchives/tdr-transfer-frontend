import { initNeutralCitationToggle } from "../../src/judgmentmetadata/neutral-citation"

describe("initNeutralCitationToggle", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <input type="checkbox" id="no-ncn">
      <input type="text" id="neutral-citation">
      <input type="text" id="no-ncnHtml" value="some details">
    `
  })

  test("disables and clears input when checkbox is checked", () => {
    const checkbox = document.querySelector<HTMLInputElement>("#no-ncn")!
    const input = document.querySelector<HTMLInputElement>("#neutral-citation")!

    input.value = "[2025] EWHC 123"
    checkbox.checked = true

    initNeutralCitationToggle()

    expect(input.value).toBe("")
    expect(input.disabled).toBe(true)
    expect(input.getAttribute("aria-disabled")).toBe("true")
  })

  test("enables input when checkbox is unchecked and clears details field", () => {
    const checkbox = document.querySelector<HTMLInputElement>("#no-ncn")!
    const input = document.querySelector<HTMLInputElement>("#neutral-citation")!
    const details = document.querySelector<HTMLInputElement>("#no-ncnHtml")!

    checkbox.checked = false
    details.value = "previous details"

    initNeutralCitationToggle()

    expect(input.disabled).toBe(false)
    expect(input.getAttribute("aria-disabled")).toBeNull()
    expect(details.value).toBe("")
  })

  test("responds to change events", () => {
    const checkbox = document.querySelector<HTMLInputElement>("#no-ncn")!
    const input = document.querySelector<HTMLInputElement>("#neutral-citation")!
    const details = document.querySelector<HTMLInputElement>("#no-ncnHtml")!

    initNeutralCitationToggle()

    checkbox.checked = true
    checkbox.dispatchEvent(new Event("change"))
    expect(input.disabled).toBe(true)

    details.value = "something"
    checkbox.checked = false
    checkbox.dispatchEvent(new Event("change"))
    expect(input.disabled).toBe(false)
    expect(details.value).toBe("")
  })
})
