const mockFileCheckProcessing = {
  getFileChecksInfo: jest.fn(),
  getConsignmentId: jest.fn()
}

const mockVerifyChecksCompletedAndDisplayBanner = {
  // displayChecksCompletedBanner: jest.fn(),
  haveFileChecksCompleted: jest.fn()
}

import { FileChecks } from "../src/filechecks"
import { GraphqlClient } from "../src/graphql"
import { mockKeycloakInstance } from "./utils"
import { IFileCheckProgress } from "../src/filechecks/file-check-processing"
import { displayChecksCompletedBanner, haveFileChecksCompleted } from "../src/filechecks/verify-checks-completed-and-display-banner"

jest.mock(
  "../src/filechecks/file-check-processing",
  () => mockFileCheckProcessing
)

jest.mock(
  "../src/filechecks/verify-checks-completed-and-display-banner",
  () => mockVerifyChecksCompletedAndDisplayBanner
)
jest.useFakeTimers()
const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
const fileChecks = new FileChecks(client)

const mockConsignmentData: (fileChecks: IFileCheckProgress) => void = (
  fileChecks
) =>
  mockFileCheckProcessing.getFileChecksInfo.mockImplementation(
    (_) => fileChecks
  )

test("updateFileCheckProgress calls setTimeout correctly", async () => {
  jest.spyOn(global, "setInterval")
  await fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(setInterval).toBeCalledTimes(1)
})

test("updateFileCheckProgress shows the notification banner and an enabled continue button if all checks are complete", async () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div><a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )

  mockConsignmentData({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  })

  delete window.location
  window.location = {
    ...window.location,
    origin: "testorigin",
    href: "originalHref"
  }
  mockVerifyChecksCompletedAndDisplayBanner.haveFileChecksCompleted.mockImplementation(
    () => true
  )

  // mockVerifyChecksCompletedAndDisplayBanner.displayChecksCompletedBanner.mockImplementation(
  //   () => {console.log("AHHHHHH")}
  // )
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  // expect(displayChecksCompletedBanner).toBeCalled()

  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")
  expect(continueButton).not.toBeNull()
  expect(notificationBanner).not.toBeNull()
  //Keep typescript happy
  if (notificationBanner && continueButton) {
    expect(notificationBanner.getAttribute("hidden")).toBeNull()
    expect(
      continueButton.classList.contains("govuk-button--disabled")
    ).toBeFalsy()
    expect(continueButton.hasAttribute("disabled")).toBeFalsy()
  }
})

test("updateFileCheckProgress shows no banner and a disabled continue button if the checks are in progress", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div><a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  })
  delete window.location
  window.location = {
    ...window.location,
    origin: "testorigin",
    href: "originalHref"
  }
  // fileChecks.updateFileCheckProgress()
  // jest.spyOn(mockVerifyChecksCompletedAndDisplayBanner, "displayChecksCompletedBanner")
  // expect(displayChecksCompletedBanner).toHaveBeenCalledTimes(1);
  expect(displayChecksCompletedBanner).toBeCalled()
  jest.runOnlyPendingTimers()
  const notificationBanner = document.querySelector(
    "#file-checks-completed-banner"
  )
  const continueButton = document.querySelector("#file-checks-continue")
  expect(continueButton).not.toBeNull()
  expect(notificationBanner).not.toBeNull()
  //Keep typescript happy
  if (notificationBanner && continueButton) {
    //Hidden attribute evaluates to empty string in the tests if not removed.
    expect(notificationBanner.getAttribute("hidden")).toBe("")
    expect(
      continueButton.classList.contains("govuk-button--disabled")
    ).toBeTruthy()
    expect(continueButton.hasAttribute("disabled")).toBeTruthy()
  }
})
