import { vi, test } from 'vitest';
import {createMockKeycloakInstance} from "./utils";
import {KeycloakInitOptions, KeycloakInstance, KeycloakTokenParsed} from "keycloak-js"
import {refreshOrReturnToken, scheduleTokenRefresh} from "../src/auth"
import { getKeycloakInstance } from "../src/auth"
import { LoggedOutError } from "../src/errorhandling"
import "jest-fetch-mock"
import {isError} from "../src/errorhandling";

vi.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')
const keycloakMock = {
  __esModule: true,
  namedExport: vi.fn(),
  default: vi.fn()
}
vi.mock("keycloak-js", () => keycloakMock)

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
  login = (_: KeycloakInitOptions) => {
    return new Promise((resolve, _) => {
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
  login = (_: KeycloakInitOptions) => {
    return new Promise((resolve, _) => {
      resolve(true)
    })
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.resetModules()
})

test("Redirects user to login page and returns a new token if the user is not authenticated", async () => {
  keycloakMock.default.mockImplementation(
    () => new MockKeycloakUnauthenticated()
  )
  const instance = await getKeycloakInstance()
  expect(isError(instance)).toBe(false)
  if(!isError(instance)) {
    expect(instance.token).toEqual("fake-auth-login-token")
  }
})

test("Returns a token if the user is logged in", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakAuthenticated())
  const instance: KeycloakInstance | Error = await getKeycloakInstance()
  expect(isError(instance)).toBe(false)
  if(!isError(instance)) {
    expect(instance.token).toEqual("fake-auth-token")
  }
})

test("Returns an error if login attempt fails", async () => {
  keycloakMock.default.mockImplementation(() => new MockKeycloakError())
  await expect(getKeycloakInstance()).resolves.toEqual(Error("There has been an error"))
})

test("Doesn't call refresh token if the token is not expired", async () => {
  const isTokenExpired = false
  const mockUpdateToken = vi.fn()
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired)

  await refreshOrReturnToken(mockKeycloak)

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("Throws an error if the access token and refresh token have expired", async () => {
  const isTokenExpired = true
  const refreshTokenParsed = { exp: new Date().getTime() / 1000 - 1000 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(undefined, isTokenExpired, refreshTokenParsed)

  await expect(refreshOrReturnToken(mockKeycloak)).resolves.toEqual(
    new LoggedOutError("", "Refresh token has expired: User is logged out")
  )
})

test("'scheduleTokenRefresh' should refresh tokens if refresh token will expire within the given timeframe", async () => {
  const mockUpdateToken = vi.fn().mockImplementation((_: number) => new Promise((res, _) => res(true)))
  const isTokenExpired = true
  const refreshTokenParsed: KeycloakTokenParsed = { exp: Math.round(new Date().getTime() / 1000) + 60 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsed)

  vi.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak, "https://example.com/cookies")
  vi.runAllTimers()

  expect(mockUpdateToken).toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if the access token has not expired", async () => {
  const mockUpdateToken = vi.fn()
  const isTokenExpired = false
  const refreshTokenParsed: KeycloakTokenParsed = { exp: Math.round(new Date().getTime() / 1000) + 60 }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsed)

  vi.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak, "https://example.com/cookies")
  vi.runAllTimers()

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if there is no refresh token", async () => {
  const mockUpdateToken = vi.fn()
  const isTokenExpired = true
  const noRefreshToken: any = undefined
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, noRefreshToken)

  vi.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak, "https://example.com/cookies")
  vi.runAllTimers()

  expect(mockUpdateToken).not.toHaveBeenCalled()
})

test("'scheduleTokenRefresh' should not refresh tokens if there is no refresh token expiry defined", async () => {
  const mockUpdateToken = vi.fn()
  const isTokenExpired = true
  const refreshTokenParsedNoExp = { }
  const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(mockUpdateToken, isTokenExpired, refreshTokenParsedNoExp)

  vi.useFakeTimers()
  scheduleTokenRefresh(mockKeycloak, "https://example.com/cookies")
  vi.runAllTimers()
  expect(mockUpdateToken).not.toHaveBeenCalled()
})

// test("'scheduleTokenRefresh' should call the cookies endpoint to refresh the signed cookies", async () => {
//   fetchMocker.mockResponse("ok")
//   const mockKeycloak: KeycloakInstance = createMockKeycloakInstance(vi.fn(), false, {exp: 1})
//   const cookiesUrl = "https://example.com/cookies"
//   vi.useFakeTimers()
//   scheduleTokenRefresh(mockKeycloak, cookiesUrl)
//   vi.runAllTimers()
//   const response = await fetchMocker.mock
//   const calls = response.calls
//   expect(calls.length).toBe(1)
//   expect(response!.calls[0][0]).toBe(cookiesUrl)
//   const headers = response!.calls[0]![1]!.headers as {Authorization: string}
//   expect(headers["Authorization"]).toBe("Bearer fake-auth-token")
// })
