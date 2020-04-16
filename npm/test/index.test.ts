const mockAuth = {
  getKeycloakInstance: jest.fn()
}

import { renderModules } from "../src/index"
import { mockKeycloakInstance } from "./utils"

jest.mock("../src/auth", () => mockAuth)

beforeEach(() => jest.resetModules())

beforeEach(() => {
  jest.resetModules()
})

test("renderModules calls authorisation when upload form present on page", async () => {
  const spyAuth = jest
    .spyOn(mockAuth, "getKeycloakInstance")
    .mockImplementation(() => Promise.resolve(mockKeycloakInstance))

  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()
  expect(spyAuth).toBeCalledTimes(1)

  spyAuth.mockRestore()
})

test("renderModules does not call authorisation when no upload form present on page", async () => {
  const spyAuth = jest
    .spyOn(mockAuth, "getKeycloakInstance")
    .mockImplementation(() => Promise.resolve(mockKeycloakInstance))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(spyAuth).toBeCalledTimes(0)

  spyAuth.mockRestore()
})
