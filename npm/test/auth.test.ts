import { refreshOrReturnToken, authenticateAndGetIdentityId } from "../src/auth"
import { getKeycloakInstance } from "../src/auth"
jest.mock("keycloak-js")
jest.mock("aws-sdk")

import Keycloak, { KeycloakInstance } from "keycloak-js"
import AWS from "aws-sdk"

class MockKeycloakAuthenticated {
  token: string = "fake-auth-token"

  isTokenExpired() {
    return false
  }

  init() {
    return new Promise((resolve, _) => resolve(true))
  }
}
class MockKeycloakUnauthenticated {
  init() {
    return new Promise((resolve, _) => resolve(false))
  }
}
class MockKeycloakError {
  init() {
    return new Promise((_, reject) => reject("There has been an error"))
  }
}

beforeEach(() => {
  jest.resetAllMocks()
  jest.resetModules()
})

test("Returns an error if the user is not logged in", async () => {
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakUnauthenticated()
  })
  await expect(getKeycloakInstance()).rejects.toEqual(
    "User is not authenticated"
  )
})

test("Returns a token if the user is logged in", async () => {
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakAuthenticated()
  })
  await expect(getKeycloakInstance()).resolves.toEqual({
    token: "fake-auth-token"
  })
})

test("Returns an error if login attempt fails", async () => {
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakError()
  })
  await expect(getKeycloakInstance()).rejects.toEqual("There has been an error")
})

test("Calls the correct authentication update", async () => {
  const config = AWS.config.update as jest.Mock
  const token = "testtoken"
  config.mockImplementation(() => console.log("update called"))
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakAuthenticated()
  })
  const keycloak = await getKeycloakInstance()
  const identityId = await authenticateAndGetIdentityId(keycloak)
  expect(identityId).toBeUndefined()
  expect(config).toHaveBeenCalled()
})

test("Calls refresh token if the token is expired", async () => {
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const updateToken = jest.fn()
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
    isTokenExpired,
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }

  refreshOrReturnToken(mockKeycloak)

  expect(updateToken).toHaveBeenCalled()
})

test("Doesn't call refresh token if the token is not expired", () => {
  const isTokenExpired = jest.fn().mockImplementation(() => false)
  const updateToken = jest.fn()
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
    isTokenExpired,
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }

  refreshOrReturnToken(mockKeycloak)

  expect(updateToken).not.toHaveBeenCalled()
})
