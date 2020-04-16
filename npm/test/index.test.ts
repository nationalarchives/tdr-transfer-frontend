import { getKeycloakInstance } from "../src/auth"
import { renderModules } from "../src/index"
import { mockKeycloakInstance } from "./utils"

jest.mock("../src/auth")

const mockGetKeycloakInstance = getKeycloakInstance as jest.Mock
mockGetKeycloakInstance.mockImplementation(() =>
  Promise.resolve(mockKeycloakInstance)
)

beforeEach(() => jest.resetModules())
afterEach(() => mockGetKeycloakInstance.mockReset())

test("renderModules calls authorisation when upload form present on page", async () => {
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()
  expect(mockGetKeycloakInstance).toBeCalledTimes(1)
})

test("renderModules does not call authorisation when no upload form present on page", async () => {
  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(mockGetKeycloakInstance).toBeCalledTimes(0)
})
