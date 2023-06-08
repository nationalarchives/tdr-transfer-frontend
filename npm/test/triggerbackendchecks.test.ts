import { TriggerBackendChecks } from "../src/triggerbackendchecks"

import fetchMock, { enableFetchMocks } from "jest-fetch-mock"

enableFetchMocks()

const consignmentId = "a201c866-0423-4c90-a024-7d894f290df8"

jest.mock("uuid", () => "eb7b7961-395d-4b4c-afc6-9ebcadaf0150")

beforeEach(() => {
  document.body.innerHTML =
    '<input name="csrfToken" value="abcde">' +
    `<input id="consignmentId" value=${consignmentId}>`
  fetchMock.resetMocks()
  jest.resetModules()
})

test("triggerBackendChecks returns the correct status", async () => {
  fetchMock.mockResponse("{}")

  const triggerBackendChecks = new TriggerBackendChecks()
  const result = await triggerBackendChecks.triggerBackendChecks(consignmentId)
  expect((result as Response).status).toEqual(200)
})

test("triggerBackendChecks calls the correct url", async () => {
  fetchMock.mockResponse("{}")
  const triggerBackendChecks = new TriggerBackendChecks()

  await triggerBackendChecks.triggerBackendChecks(consignmentId)

  expect(fetchMock.mock.calls[0][0]).toEqual(
    `/consignment/${consignmentId}/trigger-backend-checks`
  )
})

test("triggerBackendChecks returns error if the response is not 200", async () => {
  fetchMock.mockResponse("error", {
    statusText: "There was an error",
    status: 500
  })
  const triggerBackendChecks = new TriggerBackendChecks()
  await expect(
    triggerBackendChecks.triggerBackendChecks(consignmentId)
  ).resolves.toStrictEqual(
    Error("Backend checks failed to trigger: There was an error")
  )
})

test("triggerBackendChecks returns error if the front end call fails", async () => {
  fetchMock.mockReject(Error("Error from frontend"))
  const triggerBackendChecks = new TriggerBackendChecks()
  await expect(
    triggerBackendChecks.triggerBackendChecks(consignmentId)
  ).resolves.toStrictEqual(Error("Error: Error from frontend"))
})
