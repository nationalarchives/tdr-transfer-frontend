import { displayChecksCompletedBanner } from "../src/filechecks/display-checks-completed-banner"

test("displayChecksCompletedBanner unhides the button and displays banner when called", () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  expect(notificationBanner!.getAttribute("hidden")).toBeNull()
  expect(
    continueButton!.classList.contains("govuk-button--disabled")
  ).toBeFalsy()
  expect(continueButton!.hasAttribute("disabled")).toBeFalsy()

})

test("displayChecksCompletedBanner displays banner but can't unhide the button", () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>`  // no button exists in the HTML
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  expect(continueButton).toBeNull()
  expect(notificationBanner).not.toBeNull()
  //Hidden attribute evaluates to empty string in the tests if not removed.
  expect(notificationBanner!.getAttribute("hidden")).toBeNull()
})

test("displayChecksCompletedBanner displays banner but can't unhide the button", () => {
  document.body.innerHTML = `<a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`  // no banner exists in the HTML
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  expect(continueButton).not.toBeNull()
  expect(notificationBanner).toBeNull()
  expect(
    continueButton!.classList.contains("govuk-button--disabled")
  ).toBeFalsy()
  expect(continueButton!.hasAttribute("disabled")).toBeFalsy()

})
