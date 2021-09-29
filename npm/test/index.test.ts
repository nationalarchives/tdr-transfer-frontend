import { KeycloakInstance } from "keycloak-js"
import { renderModules } from "../src/index"

jest.mock("../src/auth")
import { getKeycloakInstance } from "../src/auth"
import {createMockKeycloakInstance} from "./utils";

beforeEach(() => jest.resetModules())

const mockKeycloak: KeycloakInstance = createMockKeycloakInstance()

const getFrontEndInfoHtml: () => string = () => {
  return `
    <input type="hidden" class="api-url">
    <input type="hidden" class="stage">
    <input type="hidden" class="region">
    <input type="hidden" class="upload-url"
  `.toString()
}

test("renderModules calls authorisation when upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    '<div id="file-upload">' +
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>" +
    "</div>"

  renderModules()
  expect(keycloakInstance).toBeCalledTimes(1)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when no upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(keycloakInstance).toBeCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when no identity pool id present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(keycloakInstance).toBeCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when the front end info is missing", () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()
  expect(keycloakInstance).toBeCalledTimes(0)

  keycloakInstance.mockRestore()
})
