const mockGetFileCheckProgress = {
  getFileChecksProgress: jest.fn(),
  getConsignmentId: jest.fn()
}

const mockVerifyChecksHaveCompleted = {
  displayChecksCompletedBanner: jest.fn(),
  haveFileChecksCompleted: jest.fn()
}

const mockDisplayChecksCompletedBanner = {
  displayChecksCompletedBanner: jest.fn()
}

import { FileChecks } from "../src/filechecks"
import { GraphqlClient } from "../src/graphql"
import { mockKeycloakInstance } from "./utils"
import { IFileCheckProgress } from "../src/filechecks/get-file-check-progress"
import { haveFileChecksCompleted } from "../src/filechecks/verify-checks-have-completed"
import { displayChecksCompletedBanner } from "../src/filechecks/display-checks-completed-banner"

jest.mock(
  "../src/filechecks/get-file-check-progress",
  () => mockGetFileCheckProgress
)

jest.mock(
  "../src/filechecks/verify-checks-have-completed",
  () => mockVerifyChecksHaveCompleted
)

jest.mock(
  "../src/filechecks/display-checks-completed-banner",
  () => mockDisplayChecksCompletedBanner
)
const mockGoToNextPage = jest.fn()

jest.useFakeTimers()

window.location = {
  ...window.location,
  origin: "testorigin",
  href: "originalHref"
}

beforeEach(() => {
  jest.clearAllMocks()
  mockGoToNextPage.mockRestore()
})

const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
const fileChecks = new FileChecks(client)

const mockGetFileChecksProgress: (
  fileChecks: IFileCheckProgress | null
) => void = (fileChecks) =>
  mockGetFileCheckProgress.getFileChecksProgress.mockImplementation(
    (_) => fileChecks
  )

const mockDisplayChecksHaveCompletedBanner: () => void = () =>
  mockVerifyChecksHaveCompleted.displayChecksCompletedBanner.mockImplementation(
    () => {}
  )

test("updateFileCheckProgress calls setInterval correctly", async () => {
  jest.spyOn(global, "setInterval")
  await fileChecks.updateFileCheckProgress(false, mockGoToNextPage)
  jest.runOnlyPendingTimers()
  expect(setInterval).toBeCalledTimes(1)
})

test("updateFileCheckProgress shows a standard user, the notification banner and an enabled continue button if all checks are complete", async () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )

  mockGetFileChecksProgress({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  })

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => true
  )

  mockDisplayChecksHaveCompletedBanner()

  fileChecks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).toBeCalled()
})

test("updateFileCheckProgress shows a standard user, no banner and a disabled continue button if the checks are in progress", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockGetFileChecksProgress({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  })

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  fileChecks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("updateFileCheckProgress shows a standard user, no banner and a disabled continue button if no file checks information is returned", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockGetFileChecksProgress(null)

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  fileChecks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("updateFileCheckProgress calls goToNextPage for a judgment user, if all checks are complete", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )

  mockGetFileChecksProgress({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  })

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => true
  )

  fileChecks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(mockGoToNextPage).toHaveBeenCalled()
})

test("updateFileCheckProgress does not call goToNextPage for a judgment user if the checks are in progress", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockGetFileChecksProgress({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  })

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => false
  )

  fileChecks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(mockGoToNextPage).not.toHaveBeenCalled()
})

test("updateFileCheckProgress does not call goToNextPage for a judgment user if no file checks information is returned", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetFileCheckProgress.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockGetFileChecksProgress(null)

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
    () => false
  )

  fileChecks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(mockGoToNextPage).not.toHaveBeenCalled()
})
