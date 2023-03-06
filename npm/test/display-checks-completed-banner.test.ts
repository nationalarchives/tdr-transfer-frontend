import { displayChecksCompletedBanner } from "../src/filechecks/display-checks-completed-banner"

test("displayChecksCompletedBanner unhides the button removes the disabled reason and displays banner when called", () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" aria-describedby="reason-disabled" aria-disabled="true"></a>
                            <p id="reason-disabled"></p>
                            `
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")
  const disabledReason = document.querySelector("#reason-disabled")

  expect(notificationBanner!.getAttribute("hidden")).toBeNull()
  expect(
    continueButton!.classList.contains("govuk-button--disabled")
  ).toBeFalsy()
  expect(continueButton!.getAttribute("aria-disabled")).toBe("false")
  expect(continueButton!.getAttribute("aria-described-by")).toBeNull()
  expect(disabledReason).toBeNull()
})

test("displayChecksCompletedBanner doesn't display banner if 'continue' button is missing", () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>`  // no button exists in the HTML
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  expect(continueButton).toBeNull()
  expect(notificationBanner).not.toBeNull()
  //Hidden attribute evaluates to empty string in the tests if not removed.
  expect(notificationBanner!.getAttribute("hidden")).not.toBeNull()
})

test("displayChecksCompletedBanner doesn't enable 'continue' button if display banner is missing", () => {
  document.body.innerHTML = `<a id="file-checks-continue" class="govuk-button--disabled" aria-disabled="true"></a>`  // no banner exists in the HTML
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  expect(continueButton).not.toBeNull()
  expect(notificationBanner).toBeNull()
  expect(
    continueButton!.classList.contains("govuk-button--disabled")
  ).toBeTruthy()
  expect(continueButton!.getAttribute("aria-disabled")).toBeTruthy()
})
