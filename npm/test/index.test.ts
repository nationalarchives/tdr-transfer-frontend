import { KeycloakInstance } from "keycloak-js"
import { renderModules } from "../src/index"

jest.mock("../src/auth")
import { getKeycloakInstance, authenticateAndGetIdentityId } from "../src/auth"
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
  jest.resetAllMocks()
})

test("renderModules calls authorisation when upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))
  const authenticateAndGetIdentity = authenticateAndGetIdentityId as jest.Mock
  authenticateAndGetIdentity.mockImplementation(() =>
    Promise.resolve("identityId")
  )

  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()
  expect(keycloakInstance).toBeCalledTimes(1)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when no upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))
  const authenticateAndGetIdentity = authenticateAndGetIdentityId as jest.Mock
  authenticateAndGetIdentity.mockImplementation(() =>
    Promise.resolve("identityId")
  )

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
