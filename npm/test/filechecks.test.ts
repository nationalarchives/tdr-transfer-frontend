const mockFileCheckProcessing = {
  updateProgressBar: jest.fn(),
  getConsignmentData: jest.fn(),
  getConsignmentId: jest.fn()
}

import { FileChecks } from "../src/filechecks"
import { GraphqlClient } from "../src/graphql"
import { mockKeycloakInstance } from "./utils"
import { IFileCheckProcessed } from "../src/filechecks/file-check-processing"

jest.mock(
  "../src/filechecks/file-check-processing",
  () => mockFileCheckProcessing
)
jest.useFakeTimers()
const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
const fileChecks = new FileChecks(client)

const mockConsignmentData: (fileChecks: IFileCheckProcessed) => void = (
  fileChecks
) => {
  const {
    antivirusProcessed,
    checksumProcessed,
    ffidProcessed,
    totalFiles,
    allChecksSucceeded
  } = fileChecks
  mockFileCheckProcessing.getConsignmentData.mockImplementation((_, callback) =>
    callback({
      antivirusProcessed,
      checksumProcessed,
      ffidProcessed,
      totalFiles,
      allChecksSucceeded
    })
  )
}

test("updateFileCheckProgress calls setTimeout correctly", async () => {
  jest.spyOn(global, "setInterval")
  await fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(setInterval).toBeCalledTimes(1)
})

test("updateFileCheckProgress updates the progress bars correctly", () => {
  mockConsignmentData({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2,
    allChecksSucceeded: true
  })
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(mockFileCheckProcessing.updateProgressBar).toHaveBeenNthCalledWith(
    1,
    1,
    2,
    "#av-metadata-progress-bar"
  )
  expect(mockFileCheckProcessing.updateProgressBar).toHaveBeenNthCalledWith(
    2,
    2,
    2,
    "#checksum-progress-bar"
  )
  expect(mockFileCheckProcessing.updateProgressBar).toHaveBeenNthCalledWith(
    3,
    1,
    2,
    "#ffid-progress-bar"
  )
})

test("updateFileCheckProgress redirects if all checks are complete and successful", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2,
    allChecksSucceeded: true
  })
  delete window.location
  window.location = {
    ...window.location,
    origin: "testorigin",
    href: "originalHref"
  }
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(window.location.href).toBe(
    `testorigin/consignment/${consignmentId}/records-results`
  )
})

test("updateFileCheckProgress redirects if all checks are complete but some have failed", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2,
    allChecksSucceeded: false
  })
  delete window.location
  window.location = {
    ...window.location,
    origin: "testorigin",
    href: "originalHref"
  }
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(window.location.href).toBe(
    `testorigin/consignment/${consignmentId}/checks-failed`
  )
})

test("updateFileCheckProgress does not redirect if the checks are in progress", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2,
    allChecksSucceeded: true
  })
  delete window.location
  window.location = {
    ...window.location,
    origin: "testorigin",
    href: "originalHref"
  }
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(window.location.href).toBe("originalHref")
})
