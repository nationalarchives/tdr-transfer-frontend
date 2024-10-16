const mockGetCheckProgress = {
  getFileChecksProgress: jest.fn(),
  getTransferProgress: jest.fn(),
  getDraftMetadataValidationProgress: jest.fn(),
  getConsignmentId: jest.fn(),
  continueTransfer: jest.fn()
}

const mockVerifyChecksHaveCompleted = {
  displayChecksCompletedBanner: jest.fn(),
  haveFileChecksCompleted: jest.fn(),
  hasDraftMetadataValidationCompleted: jest.fn()
}

const mockDisplayChecksCompletedBanner = {
  displayChecksCompletedBanner: jest.fn()
}

import { Checks } from "../src/checks"
import {hasDraftMetadataValidationCompleted, haveFileChecksCompleted} from "../src/checks/verify-checks-have-completed"
import { displayChecksCompletedBanner } from "../src/checks/display-checks-completed-banner"

//stop console errors from window.location.reload in these tests that arise as described here: https://remarkablemark.org/blog/2018/11/17/mock-window-location/
const original = window.location;

beforeAll(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: { reload: jest.fn() },
  });
});

afterAll(() => {
  Object.defineProperty(window, 'location', { configurable: true, value: original });
});

jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

jest.mock(
    "../src/checks/get-checks-progress",
    () => mockGetCheckProgress
)

jest.mock(
    "../src/checks/verify-checks-have-completed",
    () => mockVerifyChecksHaveCompleted
)

jest.mock(
    "../src/checks/display-checks-completed-banner",
    () => mockDisplayChecksCompletedBanner
)
const mockGoToNextPage = jest.fn()

jest.useFakeTimers()

beforeEach(() => {
  jest.clearAllMocks()
  mockGoToNextPage.mockRestore()
})

const typesOfValidationProgress: {
  [key: string]: {} | null
} = {
  noData: null,
  inProgress: {
    progressStatus: "InProgress"
  },
  completed: {
    progressStatus: "Completed"
  },
  completedWithIssues: {
    progressStatus: "CompletedWithIssues"
  },
  failed: {
    progressStatus: "Failed"
  }
}

const typesOfProgress: {
  [key: string]: {} | null
} = {
  noData: null,
  inProgress: {
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  },
  complete: {
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  }
}
const typesOfTransferProgress: {
  [key: string]: {} | null
} = {
  noData: null,
  inProgress: {
    fileChecksStatus: "InProgress",
    exportStatus: ""
  },
  complete: {
    fileChecksStatus: "Completed",
    exportStatus: "InProgress"
  }
}
const checks = new Checks()

const mockGetDraftMetadataValidationProgress: (progressType: string) => void = (progressType) => {
  mockGetCheckProgress.getDraftMetadataValidationProgress.mockImplementation(
      (_) => typesOfValidationProgress[progressType]
  )
}

const mockGetFileChecksProgress: (progressType: string) => void = (
    progressType: string
) => {
  mockGetCheckProgress.getFileChecksProgress.mockImplementation(
      (_) => typesOfProgress[progressType]
  )
}

const mockTransferProgress: (progressType: string) => void = (
    progressType: string
) => {
  mockGetCheckProgress.continueTransfer.mockImplementation()
  mockGetCheckProgress.getTransferProgress.mockImplementation(
      (_) => typesOfTransferProgress[progressType]
  )
}

const mockDisplayChecksHaveCompletedBanner: () => void = () =>
    mockVerifyChecksHaveCompleted.displayChecksCompletedBanner.mockImplementation(
        () => {}
    )

test("'updateFileCheckProgress' calls setInterval correctly", async () => {
  jest.spyOn(global, "setInterval")
  await checks.updateFileCheckProgress(false, mockGoToNextPage)
  jest.runOnlyPendingTimers()
  expect(setInterval).toBeCalledTimes(2)
})

test("'updateDraftMetadataValidationProgress' calls setInterval correctly", async () => {
  jest.spyOn(global, "setInterval")
  await checks.updateDraftMetadataValidationProgress()
  jest.runOnlyPendingTimers()
  expect(setInterval).toBeCalledTimes(1)
})

test("'updateFileCheckProgress' shows a standard user, the notification banner and an enabled continue button if all checks are complete", async () => {
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )

  mockGetFileChecksProgress("complete")

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
      () => true
  )

  mockDisplayChecksHaveCompletedBanner()

  checks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).toBeCalled()
})

test("'updateDraftMetadataValidationProgress' shows a standard user, the notification banner and an enabled continue button if all checks are 'completed'", async () => {
  document.body.innerHTML = `<div id="draft-metadata-checks-completed-banner" hidden></div>
                            <a id="draft-metadata-checks-continue" class="govuk-button--disabled" disabled></a>`
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )

  mockGetDraftMetadataValidationProgress("completed")

  mockVerifyChecksHaveCompleted.hasDraftMetadataValidationCompleted.mockImplementation(
      () => true
  )

  mockDisplayChecksHaveCompletedBanner()

  checks.updateDraftMetadataValidationProgress()
  await jest.runOnlyPendingTimers()

  expect(hasDraftMetadataValidationCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).toBeCalled()
})

test("'updateDraftMetadataValidationProgress' shows a standard user, the notification banner and an enabled continue button if all checks are 'completedWithIssues'", async () => {
  document.body.innerHTML = `<div id="draft-metadata-checks-completed-banner" hidden></div>
                            <a id="draft-metadata-checks-continue" class="govuk-button--disabled" disabled></a>`
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )

  mockGetDraftMetadataValidationProgress("completedWithIssues")

  mockVerifyChecksHaveCompleted.hasDraftMetadataValidationCompleted.mockImplementation(
      () => true
  )

  mockDisplayChecksHaveCompletedBanner()

  checks.updateDraftMetadataValidationProgress()
  await jest.runOnlyPendingTimers()

  expect(hasDraftMetadataValidationCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).toBeCalled()
})

test("'updateFileCheckProgress' shows a standard user, no banner and a disabled continue button if the checks are in progress", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockGetFileChecksProgress("inProgress")

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
      () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  checks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("'updateDraftMetadataValidationProgress' shows a standard user, no banner and a disabled continue button if the checks are in progress", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="draft-metadata-completed-banner" hidden></div>
                            <a id="draft-metadata-continue" class="govuk-button--disabled" disabled></a>`
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockGetDraftMetadataValidationProgress("inProgress")

  mockVerifyChecksHaveCompleted.hasDraftMetadataValidationCompleted.mockImplementation(
      () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  checks.updateDraftMetadataValidationProgress()
  await jest.runOnlyPendingTimers()

  expect(hasDraftMetadataValidationCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("'updateFileCheckProgress' shows a standard user, no banner and a disabled continue button if no file checks information is returned", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="file-checks-completed-banner" hidden></div>
                            <a id="file-checks-continue" class="govuk-button--disabled" disabled></a>`
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockGetFileChecksProgress("noData")

  mockVerifyChecksHaveCompleted.haveFileChecksCompleted.mockImplementation(
      () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  checks.updateFileCheckProgress(false, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(haveFileChecksCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("'updateDraftMetadataValidationProgress' shows a standard user, no banner and a disabled continue button if no file checks information is returned", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  document.body.innerHTML = `<div id="draft-metadata-completed-banner" hidden></div>
                            <a id="draft-metadata-continue" class="govuk-button--disabled" disabled></a>`
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockGetDraftMetadataValidationProgress("noData")

  mockVerifyChecksHaveCompleted.hasDraftMetadataValidationCompleted.mockImplementation(
      () => false
  )
  mockDisplayChecksHaveCompletedBanner()

  checks.updateDraftMetadataValidationProgress()
  await jest.runOnlyPendingTimers()

  expect(hasDraftMetadataValidationCompleted).toBeCalled()
  expect(displayChecksCompletedBanner).not.toBeCalled()
})

test("'updateFileCheckProgress' calls goToNextPage for a judgment user, if all checks are complete", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )

  mockTransferProgress("complete")

  checks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(mockGoToNextPage).toHaveBeenCalled()
})

test("'updateFileCheckProgress' does not call goToNextPage for a judgment user if the checks are in progress", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockTransferProgress("inProgress")

  checks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(mockGoToNextPage).toHaveBeenCalled()
})

test("'updateFileCheckProgress' does not call goToNextPage for a judgment user if no file checks information is returned", async () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockGetCheckProgress.getConsignmentId.mockImplementation(
      () => consignmentId
  )
  mockTransferProgress("noData")

  checks.updateFileCheckProgress(true, mockGoToNextPage)
  await jest.runOnlyPendingTimers()

  expect(mockGoToNextPage).not.toHaveBeenCalled()
})
