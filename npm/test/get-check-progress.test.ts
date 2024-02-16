import {
  getConsignmentId,
  getFileChecksProgress,
  IFileCheckProgress
} from "../src/checks/get-check-progress"
import {
  GetFileCheckProgressQuery
} from "@nationalarchives/tdr-generated-graphql"
import { isError } from "../src/errorhandling"
import fetchMock, {enableFetchMocks} from "jest-fetch-mock"
enableFetchMocks()

jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

beforeEach(() => fetchMock.resetMocks())

const data: GetFileCheckProgressQuery = {
  getConsignment: {
    files: [],
    totalFiles: 10,
    allChecksSucceeded: false,
    fileChecks: {
      antivirusProgress: { filesProcessed: 2 },
      ffidProgress: { filesProcessed: 4 },
      checksumProgress: { filesProcessed: 3 }
    }
  }
}


test("getFileChecksProgress returns the correct consignment data with a successful api call", async () => {
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
  }
})

test("getFileChecksProgress throws an exception with a failed api call", async () => {
  fetchMock.mockReject(Error("Error from frontend"))
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getFileChecksProgress()).resolves.toEqual(Error("Error: Error from frontend"))
})

test("getFileChecksProgress throws a exception after errors from a successful api call", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  document.body.innerHTML = `
    <input id="consignmentId" type="hidden" value="${consignmentId}">
    <input name="csrfToken" value="abcde">
    `

  await expect(getFileChecksProgress()).resolves.toEqual(Error("Add client file metadata failed: There was an error"))
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
