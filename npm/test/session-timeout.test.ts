import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
import {createMockKeycloakInstance, frontendInfo} from "./utils"
import Keycloak, { KeycloakTokenParsed } from "keycloak-js"
import "jest-fetch-mock"

enableFetchMocks()

jest.mock("uuid", () => "eb7b7961-395d-4b4c-afc6-9ebcadaf0150")

document.body.innerHTML =
  '<test-dialog class="timeout-dialog">' +
  '<button id="extend-timeout" class="govuk-button">' +
  "Keep me signed in" +
  "</button>" +
  "</test-dialog>"

import { initialiseSessionTimeout } from "../src/auth/session-timeout"

const keycloakMock = {
  __esModule: true,
  namedExport: jest.fn(),
  default: jest.fn()
}

jest.mock("keycloak-js", () => keycloakMock)

// Typescript complains that there's no showModal function even though there is.
// This is probably some weirdness with the jest typescript definitions
// This creates a new html element that has the write function on it.
customElements.define(
  "test-dialog",
  class extends HTMLElement {
    open = false
    showModal = () => (this.open = true)
    close = () => {}
  }
)

const timeoutDialog: HTMLDialogElement =
  document.querySelector(".timeout-dialog")!
const extendTimeout: HTMLButtonElement =
  document.querySelector("#extend-timeout")!
const createMockKeycloak = (
  updateToken: jest.Mock,
  isTokenExpired: boolean,
  refreshTokenParsed: KeycloakTokenParsed
): Keycloak.KeycloakInstance => {
  return createMockKeycloakInstance(
    updateToken,
    isTokenExpired,
    refreshTokenParsed
  )
}
const initialiseSessionTimer = async (now: Date) => {
  jest.setSystemTime(now)
  await initialiseSessionTimeout(frontendInfo)
  jest.runOnlyPendingTimers()
}

beforeEach(() => {
  fetchMock.mockClear()
  jest.clearAllMocks()
  jest.resetModules()
  jest.useFakeTimers()
})

test("'initialiseSessionTimeout' should refresh tokens if the user clicks to extend the session", async () => {
  const mockUpdateToken = jest
    .fn()
    .mockImplementation((_: number) => new Promise((res, _) => res(true)))
  const isTokenExpired = false
  const now = new Date()
  const refreshTokenParsed: KeycloakTokenParsed = {
    exp: Math.round(now.getTime() / 1000) + 300
  }

  const mockKeycloak: Keycloak.KeycloakInstance = createMockKeycloak(
    mockUpdateToken,
    isTokenExpired,
    refreshTokenParsed
  )

  keycloakMock.default.mockImplementation(() => mockKeycloak)
  await initialiseSessionTimer(now)
  expect(timeoutDialog.open).toEqual(true)

  extendTimeout.click()

  jest.useRealTimers()
  await new Promise((r) => setTimeout(r, 1)) //Slightly cheating, the await allows the refreshToken to work
  expect(mockKeycloak.updateToken).toBeCalled()
})

test("'initialiseSessionTimeout' should sign the user out if the session expires", async () => {
  const mockUpdateToken = jest
    .fn()
    .mockImplementation((_: number) => new Promise((res, _) => res(true)))
  const isTokenExpired = false
  const now = new Date()
  const refreshTokenParsed: KeycloakTokenParsed = {
    exp: Math.round(now.getTime() / 1000) - 300
  }

  const mockKeycloak: Keycloak.KeycloakInstance = createMockKeycloak(
    mockUpdateToken,
    isTokenExpired,
    refreshTokenParsed
  )

  keycloakMock.default.mockImplementation(() => mockKeycloak)
  await initialiseSessionTimer(now)

  expect(timeoutDialog.open).toEqual(true)
  jest.useRealTimers()
  expect(mockKeycloak.logout).toBeCalled()
})
