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

const mockConsignmentData: (
  fileChecks: IFileCheckProcessed
) => void = fileChecks => {
  const { antivirusProcessed, checksumProcessed, totalFiles } = fileChecks
  mockFileCheckProcessing.getConsignmentData.mockImplementation((_, callback) =>
    callback({
      antivirusProcessed,
      checksumProcessed,
      totalFiles
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
    totalFiles: 2
  })
  fileChecks.updateFileCheckProgress()
  jest.runOnlyPendingTimers()
  expect(mockFileCheckProcessing.updateProgressBar).toHaveBeenNthCalledWith(
    1,
    1,
    1,
    "#av-metadata-progress-bar"
  )
  expect(mockFileCheckProcessing.updateProgressBar).toHaveBeenNthCalledWith(
    2,
    2,
    1,
    "#checksum-progress-bar"
  )
})

test("updateFileCheckProgress redirects if all checks are complete", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 2,
    checksumProcessed: 2,
    totalFiles: 2
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

test("updateFileCheckProgress does not redirect if the checks are in progress", () => {
  const consignmentId = "e25438db-4bfb-41c9-8fff-6f2e4cca6421"
  mockFileCheckProcessing.getConsignmentId.mockImplementation(
    () => consignmentId
  )
  mockConsignmentData({
    antivirusProcessed: 1,
    checksumProcessed: 2,
    totalFiles: 2
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
