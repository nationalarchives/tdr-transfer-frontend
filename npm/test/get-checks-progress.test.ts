import {
  getConsignmentId,
  getDraftMetadataValidationProgress,
  getFileChecksProgress,
  getTransferProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "../src/checks/get-checks-progress"
import { ConsignmentStatus, GetFileCheckProgressQuery } from "@nationalarchives/tdr-generated-graphql"
import { isError } from "../src/errorhandling"
import fetchMock, { enableFetchMocks } from "jest-fetch-mock"

enableFetchMocks()

jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

beforeEach(() => fetchMock.resetMocks())

const data: GetFileCheckProgressQuery = {
  getConsignment: {
    files: [],
    parentFolder: "parentFolder",
    totalFiles: 10,
    allChecksSucceeded: false,
    fileChecks: {
      antivirusProgress: { filesProcessed: 2 },
      ffidProgress: { filesProcessed: 4 },
      checksumProgress: { filesProcessed: 3 }
    }
  }
}

const consignmentStatusData: ConsignmentStatus[] = [
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "StatusType",
    value: "StatusValue",
    createdDatetime: "CreatedDateTime"
  },
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "DraftMetadata",
    value: "InProgress",
    createdDatetime: "CreatedDateTime"
  }
]

const consignmentStatusNoDraftMetadataData: ConsignmentStatus[] = [
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "StatusType",
    value: "StatusValue",
    createdDatetime: "CreatedDateTime"
  }
]

const completedConsignmentStatuses: ConsignmentStatus[] = [
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "Checksum",
    value: "Completed",
    createdDatetime: "CreatedDateTime"
  },
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "Antivirus",
    value: "CompletedWithIssues",
    createdDatetime: "CreatedDateTime"
  }
]

const mixedConsignmentStatuses: ConsignmentStatus[] = [
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "Checksum",
    value: "Completed",
    createdDatetime: "CreatedDateTime"
  },
  {
    consignmentStatusId: "eb7b7961-395d-4b4c-afc6-9ebcadaf0150",
    consignmentId: "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c",
    statusType: "FFID",
    value: "InProgress",
    createdDatetime: "CreatedDateTime"
  }
]

test("'getFileChecksProgress' returns the correct consignment data with a successful api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(JSON.stringify(data.getConsignment))

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()
  expect(isError(fileChecksProgress)).toBe(false)
  if(!isError(fileChecksProgress)) {
    expect(fileChecksProgress!.antivirusProcessed).toBe(2)
    expect(fileChecksProgress!.checksumProcessed).toBe(3)
    expect(fileChecksProgress!.ffidProcessed).toBe(4)
    expect(fileChecksProgress!.totalFiles).toBe(10)
    expect(fileChecksProgress!.backendChecksFailed).toBe(false)
  }
})

test("'getFileChecksProgress' returns an error rather than throwing when the page has no csrfToken", async () => {
  // fileChecksProgressAlreadyConfirmed starts the poller but renders no csrfToken field.
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    `
  fetchMock.mockResponse(JSON.stringify(data.getConsignment))

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()
  expect(isError(fileChecksProgress)).toBe(true)
  expect(fetchMock).not.toHaveBeenCalled()
})

test("'getFileChecksProgress' returns an error rather than throwing when the body is not valid JSON", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse("<html>not json</html>")

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()
  expect(isError(fileChecksProgress)).toBe(true)
})

test("'getFileChecksProgress' returns an error rather than throwing when fileChecks is missing", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(JSON.stringify({ totalFiles: 10 }))

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()
  expect(isError(fileChecksProgress)).toBe(true)
})

test("'getTransferProgress' returns a result rather than throwing when the page has no transferProgress input", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(
    JSON.stringify({ fileChecksStatus: "Completed", exportStatus: "InProgress" })
  )

  const transferProgress: Boolean | Error = await getTransferProgress()
  expect(isError(transferProgress)).toBe(false)
  expect(transferProgress).toBe(true)
})

test("'getFileChecksProgress' surfaces backendChecksFailed when the api flags it", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(
    JSON.stringify({ ...data.getConsignment, backendChecksFailed: true })
  )

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()
  expect(isError(fileChecksProgress)).toBe(false)
  if(!isError(fileChecksProgress)) {
    expect(fileChecksProgress!.backendChecksFailed).toBe(true)
  }
})

test("'getFileChecksProgress' sets isBackendChecksCompleted true when all consignment statuses are completed", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  fetchMock.mockResponse(
      JSON.stringify({
        ...data.getConsignment,
        consignmentStatuses: completedConsignmentStatuses
      })
  )

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()

  expect(isError(fileChecksProgress)).toBe(false)
  if(!isError(fileChecksProgress)) {
    expect(fileChecksProgress.isBackendChecksCompleted).toBe(true)
  }
})

test("'getFileChecksProgress' sets isBackendChecksCompleted false when any consignment status is not completed", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  fetchMock.mockResponse(
      JSON.stringify({
        ...data.getConsignment,
        consignmentStatuses: mixedConsignmentStatuses
      })
  )

  const fileChecksProgress: IFileCheckProgress | Error =
      await getFileChecksProgress()

  expect(isError(fileChecksProgress)).toBe(false)
  if(!isError(fileChecksProgress)) {
    expect(fileChecksProgress.isBackendChecksCompleted).toBe(false)
  }
})

test("'getDraftMetadataValidationProgress' returns the correct consignment data with a successful api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(JSON.stringify(consignmentStatusData))

  const validationProgress: IDraftMetadataValidationProgress | Error =
      await getDraftMetadataValidationProgress()
  expect(isError(validationProgress)).toBe(false)
  if(!isError(validationProgress)) {
    expect(validationProgress!.progressStatus).toBe("InProgress")
  }
})

test("'getDraftMetadataValidationProgress' throws an exception when no 'DraftMetadata' status present", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `
  fetchMock.mockResponse(JSON.stringify(consignmentStatusNoDraftMetadataData))

  const validationProgress: IDraftMetadataValidationProgress | Error =
      await getDraftMetadataValidationProgress()
  expect(isError(validationProgress)).toBe(true)

  await expect(getDraftMetadataValidationProgress()).resolves.toEqual(Error("No 'DraftMetadata' status set"))
})

test("getFileChecksProgress throws an exception with a failed api call", async () => {
  fetchMock.mockReject(Error("Error from API"))
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getFileChecksProgress()).resolves.toEqual(Error("Failed to retrieve progress for file checks: Error: Error from API"))
})

test("'getDraftMetadataValidationProgress' throws an exception with a failed api call", async () => {
  fetchMock.mockReject(Error("Error from API"))
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getDraftMetadataValidationProgress()).resolves.toEqual(Error("Failed to retrieve progress for draft metadata validation: Error: Error from API"))
})

test("getFileChecksProgress throws a exception after errors from a successful api call", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getFileChecksProgress()).resolves.toEqual(Error("Failed to retrieve progress for file checks: Retrieving progress failed: There was an error"))
})

test("'getDraftMetadataValidationProgress' throws a exception after errors from a successful api call", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getDraftMetadataValidationProgress()).resolves.toEqual(Error("Failed to retrieve progress for draft metadata validation: Retrieving progress failed: There was an error"))
})

test("getFileChecksProgress returns an error when the request does not settle before the timeout", async () => {
  jest.useFakeTimers()
  fetchMock.mockImplementation(
    (_input, init) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => {
          const abortError = Error("Aborted")
          abortError.name = "AbortError"
          reject(abortError)
        })
      })
  )
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="7d4ae1dd-caeb-496d-b503-ab0e8d82a12c">
    <input name="csrfToken" value="abcde">
    `

  const progress = getFileChecksProgress()
  await jest.advanceTimersByTimeAsync(30000)

  await expect(progress).resolves.toEqual(
    Error(
      "Failed to retrieve progress for file checks: Retrieving progress timed out after 30000ms"
    )
  )
  jest.useRealTimers()
})

test("getConsignmentId returns the correct id when the hidden input is present", () => {
  const consignmentId = "91847ae4-3bfd-4fac-b448-68948dd95820"
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`
  expect(getConsignmentId()).toBe(consignmentId)
})

test("getConsignmentId throws an error when the hidden input is absent", () => {
  document.body.innerHTML = ""
  expect(getConsignmentId()).toEqual(Error("No consignment provided"))
})
