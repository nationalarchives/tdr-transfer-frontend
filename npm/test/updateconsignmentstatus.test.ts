import { UpdateConsignmentStatus } from "../src/updateconsignmentstatus"

import fetchMock, {enableFetchMocks} from "jest-fetch-mock"
enableFetchMocks()
jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

beforeEach(() => {
  document.body.innerHTML='<input name="csrfToken" value="abcde">'
  fetchMock.resetMocks()
  jest.resetModules()
})

const uploadFilesInfo = {
  consignmentId: "2o4i5u4ywd5g4",
  parentFolder: "TEST PARENT FOLDER NAME"
}

test("markUploadStatusAsCompleted returns the status of 1", async () => {
  fetchMock.mockResponse(JSON.stringify({updateConsignmentStatus: 1}))

  const updateConsignmentStatus = new UpdateConsignmentStatus()
  const result = await updateConsignmentStatus.markUploadStatusAsCompleted(
    uploadFilesInfo
  )

  expect(result).toEqual(1)
})

test("markUploadStatusAsCompleted returns error if the response is not 200", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})
  const uploadMetadata = new UpdateConsignmentStatus()
  await expect(
    uploadMetadata.markUploadStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("Update consignment status failed: There was an error"))
})

test("markUploadStatusAsCompleted returns error if the front end call fails", async () => {
  fetchMock.mockReject(Error("Error from frontend"))
  const uploadMetadata = new UpdateConsignmentStatus()
  await expect(
    uploadMetadata.markUploadStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("Error: Error from frontend"))
})
