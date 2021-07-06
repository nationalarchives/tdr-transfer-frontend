import {
  displayChecksCompletedBanner,
  haveFileChecksCompleted
} from "../src/filechecks/verify-checks-completed-and-display-banner"

test("haveFileChecksCompleted returns true if file checks have completed", () => {
  const mockFileChecksResponse = {
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  }

  const checksCompleted: boolean = haveFileChecksCompleted(
    mockFileChecksResponse
  )
  expect(checksCompleted).toBe(true)
})

test("haveFileChecksCompleted returns false if file checks have not completed", () => {
  const mockFileChecksResponse = {
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  }
  const checksCompleted: boolean = haveFileChecksCompleted(
    mockFileChecksResponse
  )

  expect(checksCompleted).toBe(false)
})

test("haveFileChecksCompleted returns false if null was passed in", () => {
  const mockFileChecksResponse = null
  const checksCompleted: boolean = haveFileChecksCompleted(
    mockFileChecksResponse
  )

  expect(checksCompleted).toBe(false)
})

test("displayChecksCompletedBanner unhides the button and displays banner when called", () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  displayChecksCompletedBanner()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")

  if (notificationBanner && continueButton) {
    expect(notificationBanner.getAttribute("hidden")).toBeNull()
    expect(
      continueButton.classList.contains("govuk-button--disabled")
    ).toBeFalsy()
    expect(continueButton.hasAttribute("disabled")).toBeFalsy()
  }

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
  //Keep typescript happy
  if (notificationBanner) {
    //Hidden attribute evaluates to empty string in the tests if not removed.
    expect(notificationBanner.getAttribute("hidden")).toBeNull()
  }
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
  //Keep typescript happy
  if (continueButton) {
    expect(
      continueButton.classList.contains("govuk-button--disabled")
    ).toBeFalsy()
    expect(continueButton.hasAttribute("disabled")).toBeFalsy()
  }
})
