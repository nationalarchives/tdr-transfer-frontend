const keycloakMock = {
  __esModule: true,
  namedExport: jest.fn(),
  default: jest.fn()
}

jest.mock("keycloak-js", () => keycloakMock)
jest.mock("aws-sdk")

import { KeycloakInitOptions, KeycloakInstance } from "keycloak-js"
import { refreshOrReturnToken, authenticateAndGetIdentityId } from "../src/auth"
import { getKeycloakInstance } from "../src/auth"
import AWS from "aws-sdk"
import { IFrontEndInfo } from "../src"

class MockKeycloakAuthenticated {
  token: string = "fake-auth-token"

  isTokenExpired = () => {
    return false
  }
  init = (_: KeycloakInitOptions) => {
    return new Promise(function(resolve, _) {
      resolve(true)
    })
  }
}

class MockKeycloakUnauthenticated {
  token: string = "fake-auth-login-token"

  isTokenExpired = () => {
    return false
  }
  init = (_: KeycloakInitOptions) => {
    return new Promise(function(resolve, _) {
      resolve(false)
    })
  }
  login = (_: KeycloakInitOptions) => {
    return new Promise((resolve, _) => {
      resolve(true)
    })
  }
}

class MockKeycloakError {
  token: string = "fake-auth-token"

  isTokenExpired = () => {
    return false
  }
  init = (_: KeycloakInitOptions) => {
    return new Promise(function(_, reject) {
      reject("There has been an error")
    })
  }
}

beforeEach(() => {
  jest.resetAllMocks()
  jest.resetModules()
})

test("Redirects user to login page and returns a new token if the user is not authenticated", async () => {
  keycloakMock.default.mockImplementation(
    () => new MockKeycloakUnauthenticated()
  )
  const instance = await getKeycloakInstance()
  expect(instance.token).toEqual("fake-auth-login-token")
})

test("Returns a token if the user is logged in", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const instance = await getKeycloakInstance()
  expect(instance.token).toEqual("fake-auth-token")
})

test("Returns an error if login attempt fails", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakError())
  await expect(getKeycloakInstance()).rejects.toEqual("There has been an error")
})

test("Calls the correct authentication update", async () => {
  const config = AWS.config.update as jest.Mock
  const token = "testtoken"
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const keycloak = await getKeycloakInstance()
  const frontEndInfo: IFrontEndInfo = {
    stage: "",
    region: "",
    identityPoolId: "",
    identityProviderName: "",
    apiUrl: ""
  }
  const identityId = await authenticateAndGetIdentityId(keycloak, frontEndInfo)
  expect(identityId).toBeUndefined()
  expect(config).toHaveBeenCalled()
})

test("Calls refresh token if the token is expired", async () => {
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const updateToken = jest.fn()
  const mockKeycloak: KeycloakInstance = {
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

  await refreshOrReturnToken(mockKeycloak)

  expect(updateToken).toHaveBeenCalled()
})

test("Doesn't call refresh token if the token is not expired", async () => {
  const isTokenExpired = jest.fn().mockImplementation(() => false)
  const updateToken = jest.fn()
  const mockKeycloak: KeycloakInstance = {
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

  await refreshOrReturnToken(mockKeycloak)

  expect(updateToken).not.toHaveBeenCalled()
})
