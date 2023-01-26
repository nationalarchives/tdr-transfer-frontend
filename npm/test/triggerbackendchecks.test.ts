import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
import { TriggerBackendChecks } from "../src/triggerbackendchecks"

enableFetchMocks()

const consignmentId = "a201c866-0423-4c90-a024-7d894f290df8"

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
