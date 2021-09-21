import {createMockKeycloakInstance} from "./utils";

const keycloakMock = {
  __esModule: true,
  namedExport: jest.fn(),
  default: jest.fn()
}

jest.mock("keycloak-js", () => keycloakMock)
jest.mock("aws-sdk")

import {KeycloakInitOptions, KeycloakInstance, KeycloakTokenParsed} from "keycloak-js"
import {refreshOrReturnToken, authenticateAndGetIdentityId, scheduleTokenRefresh} from "../src/auth"
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
  const isTokenExpiredTrue = true
  const mockUpdateToken = jest.fn()
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpiredTrue)

  await refreshOrReturnToken(mockKeycloak)

  expect(mockUpdateToken).toHaveBeenCalled()
})

test("Doesn't call refresh token if the token is not expired", async () => {
  const isTokenExpired = false
  const mockUpdateToken = jest.fn()
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired)

  await refreshOrReturnToken(mockKeycloak)

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("Throws an error if the access token and refresh token have expired", async () => {
  const isTokenExpired = true
  const refreshTokenParsed = { exp: new Date().getTime() / 1000 - 1000 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(undefined, isTokenExpired, refreshTokenParsed)

  await expect(refreshOrReturnToken(mockKeycloak)).rejects.toEqual(
    new LoggedOutError("", "User is logged out")
  )
})

test("'scheduleTokenRefresh' should refresh tokens if refresh token will expire within the given timeframe", async () => {
  const mockUpdateToken = jest.fn()
  const isTokenExpired = true
  const refreshTokenParsed: KeycloakTokenParsed = { exp: Math.round(new Date().getTime() / 1000) + 60 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsed)

  jest.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak)
  jest.runAllTimers()

  expect(mockUpdateToken).toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if the access token has not expired", async () => {
  const mockUpdateToken = jest.fn()
  const isTokenExpired = false
  const refreshTokenParsed: KeycloakTokenParsed = { exp: Math.round(new Date().getTime() / 1000) + 60 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsed)

  jest.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak)
  jest.runAllTimers()

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if there is no refresh token", async () => {
  const mockUpdateToken = jest.fn()
  const isTokenExpired = true
  const noRefreshToken = undefined
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, noRefreshToken)

  jest.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak)
  jest.runAllTimers()

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if there is no refresh token expiry defined", async () => {
  const mockUpdateToken = jest.fn()
  const isTokenExpired = true
  const refreshTokenParsedNoExp = { }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsedNoExp)

  jest.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak)
  jest.runAllTimers()

  expect(mockUpdateToken).not.toHaveBeenCalled()
})
