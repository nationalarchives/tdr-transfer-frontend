const mockAuth = {
  getToken: jest.fn()
}

import { KeycloakInstance } from "keycloak-js"
import { renderModules } from "../src/index"

jest.mock("../src/auth", () => mockAuth)

beforeEach(() => jest.resetModules())

const mockKeycloak: KeycloakInstance<"native"> = {
  init: jest.fn(),
  login: jest.fn(),
  logout: jest.fn(),
  register: jest.fn(),
  accountManagement: jest.fn(),
  createLoginUrl: jest.fn(),
  createLogoutUrl: jest.fn(),
  createRegisterUrl: jest.fn(),
  createAccountUrl: jest.fn(),
  isTokenExpired: jest.fn(),
  updateToken: jest.fn(),
  clearToken: jest.fn(),
  hasRealmRole: jest.fn(),
  hasResourceRole: jest.fn(),
  loadUserInfo: jest.fn(),
  loadUserProfile: jest.fn(),
  token: "fake-auth-token"
}

beforeEach(() => {
  jest.resetModules()
})

test("renderModules calls authorisation when upload for present on page", async () => {
  const spyAuth = jest
    .spyOn(mockAuth, "getToken")
    .mockImplementation(() => Promise.resolve(mockKeycloak))

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
    .spyOn(mockAuth, "getToken")
    .mockImplementation(() => Promise.resolve(mockKeycloak))

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
