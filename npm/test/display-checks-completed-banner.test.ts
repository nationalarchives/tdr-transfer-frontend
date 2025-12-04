import { displayChecksCompletedBanner } from "../src/checks/display-checks-completed-banner"

const prefixId = "prefix-id"

test("displayChecksCompletedBanner unhides the button removes the disabled reason and displays banner when called", () => {
  document.body.innerHTML = `<div id="${prefixId}-completed-banner" hidden></div>
                            <a id="prefix-id-continue" aria-describedby="reason-disabled" aria-disabled="true" disabled></a>
                            <p id="reason-disabled"></p>
                            `
  displayChecksCompletedBanner(prefixId)

  const notificationBanner = document.querySelector(
      `#${prefixId}-completed-banner`
  )
  const continueButton = document.querySelector(`#${prefixId}-continue`)
  const disabledReason = document.querySelector("#reason-disabled")

  expect(notificationBanner!.getAttribute("hidden")).toBeNull()
  expect(continueButton!.hasAttribute("disabled")).toBe(false)
  expect(continueButton!.getAttribute("aria-disabled")).toBe("false")
  expect(continueButton!.getAttribute("aria-described-by")).toBeNull()
  expect(disabledReason).toBeNull()
})

test("displayChecksCompletedBanner doesn't display banner if 'continue' button is missing", () => {
  document.body.innerHTML = `<div id="${prefixId}-completed-banner" hidden></div>`  // no button exists in the HTML
  displayChecksCompletedBanner(prefixId)

  const notificationBanner = document.querySelector(
      `#${prefixId}-completed-banner`
  )
  const continueButton = document.querySelector(`#${prefixId}-continue`)

  expect(continueButton).toBeNull()
  expect(notificationBanner).not.toBeNull()
  //Hidden attribute evaluates to empty string in the tests if not removed.
  expect(notificationBanner!.getAttribute("hidden")).not.toBeNull()
})

test("displayChecksCompletedBanner doesn't enable 'continue' button if display banner is missing", () => {
  document.body.innerHTML = `<a id="${prefixId}-continue" aria-disabled="true" disabled></a>`  // no banner exists in the HTML
  displayChecksCompletedBanner(prefixId)

  const notificationBanner = document.querySelector(
      `#${prefixId}-completed-banner`
  )
  const continueButton = document.querySelector(`#${prefixId}-continue`)

  expect(continueButton).not.toBeNull()
  expect(notificationBanner).toBeNull()
  expect(continueButton!.hasAttribute("disabled")).toBe(true)
  expect(continueButton!.getAttribute("aria-disabled")).toBeTruthy()
})
