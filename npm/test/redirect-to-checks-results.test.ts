import { redirectToChecksResults } from "../src/checks/redirect-to-checks-results"
import { isError } from "../src/errorhandling"

const mockNavigate = jest.fn()

beforeEach(() => {
  jest.clearAllMocks()
})

test("redirectToChecksResults redirects to the url in the 'fileChecksResultsUrl' input", () => {
  const resultsUrl =
    "/consignment/e25438db-4bfb-41c9-8fff-6f2e4cca6421/file-checks-results"
  document.body.innerHTML = `<input id="fileChecksResultsUrl" type="hidden" value="${resultsUrl}">`

  const result = redirectToChecksResults(mockNavigate)

  expect(isError(result)).toBe(false)
  expect(mockNavigate).toHaveBeenCalledWith(resultsUrl)
})

test("redirectToChecksResults returns an error and does not redirect if the 'fileChecksResultsUrl' input is missing", () => {
  document.body.innerHTML = ""

  const result = redirectToChecksResults(mockNavigate)

  expect(isError(result)).toBe(true)
  expect(mockNavigate).not.toHaveBeenCalled()
})

test("redirectToChecksResults returns an error and does not redirect if the 'fileChecksResultsUrl' input is empty", () => {
  document.body.innerHTML = `<input id="fileChecksResultsUrl" type="hidden" value="">`

  const result = redirectToChecksResults(mockNavigate)

  expect(isError(result)).toBe(true)
  expect(mockNavigate).not.toHaveBeenCalled()
})
