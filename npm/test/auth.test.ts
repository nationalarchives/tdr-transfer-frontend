const keycloakMock = {
  __esModule: true,
  namedExport: jest.fn(),
  default: jest.fn()
}

jest.mock("keycloak-js", () => keycloakMock)
jest.mock("aws-sdk")

import { KeycloakInitOptions, KeycloakInstance } from "keycloak-js"
import {refreshOrReturnToken, authenticateAndGetIdentityId, refreshIdleSessionTimeout} from "../src/auth"
import { getKeycloakInstance } from "../src/auth"
import AWS, { CognitoIdentity, Request, Service } from "aws-sdk"
import { IFrontEndInfo } from "../src"
import { LoggedOutError } from "../src/errorhandling"

class MockKeycloakAuthenticated {
  token: string = "fake-auth-token"

  isTokenExpired = () => {
    return false
  }
  init = (_: KeycloakInitOptions) => {
    return new Promise(function (resolve, _) {
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
    return new Promise(function (resolve, _) {
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
    return new Promise(function (_, reject) {
      reject("There has been an error")
    })
  }
}

class MockCognitoIdentity extends CognitoIdentity {
  getId = jest.fn()
  getOpenIdToken = jest.fn()
}

class MockSTS extends AWS.STS {
  assumeRoleWithWebIdentity = jest.fn()
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

  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const keycloak = await getKeycloakInstance()
  const frontEndInfo: IFrontEndInfo = {
    stage: "",
    region: "",
    identityPoolId: "",
    identityProviderName: "",
    apiUrl: "",
    cognitoRoleArn: ""
  }
  const identity = new MockCognitoIdentity()
  identity.getId.mockReturnValue({
    promise: () => ({ IdentityId: "identityId" })
  })
  identity.getOpenIdToken.mockReturnValue({
    promise: () => ({ Token: "token" })
  })
  const sts = new MockSTS()
  sts.assumeRoleWithWebIdentity.mockReturnValue({
    promise: () => ({
      Credentials: {
        AccessKeyId: "accessKey",
        SecretAccessKey: "secretKey",
        SessionToken: "sessionToken"
      }
    })
  })

  const identityId = await authenticateAndGetIdentityId(
    keycloak,
    frontEndInfo,
    identity,
    sts
  )
  expect(identity.getId).toHaveBeenCalled()
  expect(identity.getOpenIdToken).toHaveBeenCalled()
  expect(sts.assumeRoleWithWebIdentity).toHaveBeenCalled()
  expect(identityId).toBe("identityId")
  expect(config).toHaveBeenCalled()
})

test("Throws an error if getting the identity id fails", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const keycloak = await getKeycloakInstance()
  const frontEndInfo: IFrontEndInfo = {
    stage: "",
    region: "",
    identityPoolId: "",
    identityProviderName: "",
    apiUrl: "",
    cognitoRoleArn: ""
  }
  const identity = new MockCognitoIdentity()
  identity.getId.mockReturnValue({
    promise: () => ({ IdentityId: null })
  })

  const identityId = authenticateAndGetIdentityId(
    keycloak,
    frontEndInfo,
    identity,
    new AWS.STS()
  )
  await expect(identityId).rejects.toStrictEqual(
    Error("Cannot get cognito identity id")
  )
})

test("Throws an error if getting the open id token fails", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const keycloak = await getKeycloakInstance()
  const frontEndInfo: IFrontEndInfo = {
    stage: "",
    region: "",
    identityPoolId: "",
    identityProviderName: "",
    apiUrl: "",
    cognitoRoleArn: ""
  }
  const identity = new MockCognitoIdentity()
  identity.getId.mockReturnValue({
    promise: () => ({ IdentityId: "identityId" })
  })
  identity.getOpenIdToken.mockReturnValue({
    promise: () => ({ Token: null })
  })

  const identityId = authenticateAndGetIdentityId(
    keycloak,
    frontEndInfo,
    identity,
    new AWS.STS()
  )
  await expect(identityId).rejects.toStrictEqual(
    Error("Cannot get an openid token from cognito")
  )
})

test("Throws an error if getting credentials from STS fails", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const keycloak = await getKeycloakInstance()
  const frontEndInfo: IFrontEndInfo = {
    stage: "",
    region: "",
    identityPoolId: "",
    identityProviderName: "",
    apiUrl: "",
    cognitoRoleArn: ""
  }
  const identity = new MockCognitoIdentity()
  identity.getId.mockReturnValue({
    promise: () => ({ IdentityId: "identityId" })
  })
  identity.getOpenIdToken.mockReturnValue({
    promise: () => ({ Token: "token" })
  })

  const sts = new MockSTS()
  sts.assumeRoleWithWebIdentity.mockReturnValue({
    promise: () => ({
      Credentials: null
    })
  })

  const identityId = authenticateAndGetIdentityId(
    keycloak,
    frontEndInfo,
    identity,
    sts
  )
  await expect(identityId).rejects.toStrictEqual(
    Error("Cannot get credentials from sts")
  )
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

test("Throws an error if the access token and refresh token have expired", async () => {
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const mockKeycloak: KeycloakInstance = {
    refreshTokenParsed: { exp: new Date().getTime() / 1000 - 1000 },
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
    updateToken: jest.fn(),
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }

  await expect(refreshOrReturnToken(mockKeycloak)).rejects.toEqual(
    new LoggedOutError("", "User is logged out")
  )
})

test("'refreshIdleSessionTimeout' should refresh tokens if refresh token will expire within the given timeframe", async () => {
  const updateToken = jest.fn()
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const mockKeycloak: KeycloakInstance = {
    refreshTokenParsed: { exp: Math.round(new Date().getTime() / 1000) + 60 },
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

  jest.useFakeTimers()
  refreshIdleSessionTimeout(mockKeycloak)
  jest.runAllTimers()

  expect(updateToken).toHaveBeenCalled()
})

test("'refreshIdleSessionTimeout' should not refresh tokens if the access token has not expired", async () => {
  const updateToken = jest.fn()
  const isTokenExpired = jest.fn().mockImplementation(() => false)
  const mockKeycloak: KeycloakInstance = {
    refreshTokenParsed: { exp: Math.round(new Date().getTime() / 1000) + 60 },
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

  jest.useFakeTimers()
  refreshIdleSessionTimeout(mockKeycloak)
  jest.runAllTimers()

  expect(updateToken).not.toHaveBeenCalled()
})

test("'refreshIdleSessionTimeout' should not refresh tokens if there is no refresh token", async () => {
  const updateToken = jest.fn()
  const isTokenExpired = jest.fn().mockImplementation(() => true)
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

  jest.useFakeTimers()
  refreshIdleSessionTimeout(mockKeycloak)
  jest.runAllTimers()

  expect(updateToken).not.toHaveBeenCalled()
})

test("'refreshIdleSessionTimeout' should not refresh tokens if there is no refresh token expiry defined", async () => {
  const updateToken = jest.fn()
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const mockKeycloak: KeycloakInstance = {
    refreshTokenParsed: { },
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

  jest.useFakeTimers()
  refreshIdleSessionTimeout(mockKeycloak)
  jest.runAllTimers()

  expect(updateToken).not.toHaveBeenCalled()
})
