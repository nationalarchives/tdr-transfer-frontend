import * as clientprocessing from "../src/clientprocessing"
import { upload, retrieveConsignmentId } from "../src/upload"
import { GraphqlClient } from "../src/graphql"
import { KeycloakInstance } from "keycloak-js"

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

test("Calls processing files function if file upload form present", () => {
  const spy = jest.spyOn(clientprocessing, "processFiles")
  const client = new GraphqlClient("test", mockKeycloak)
  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  upload(client)
  expect(spy).toBeCalledTimes(1)

  spy.mockRestore()
})

test("Does not call processing files if file upload form is not present", () => {
  const spy = jest.spyOn(clientprocessing, "processFiles")
  const client = new GraphqlClient("test", mockKeycloak)

  document.body.innerHTML =
    "<div>" +
    '<form id="wrong-id">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  upload(client)
  expect(spy).toBeCalledTimes(0)

  spy.mockRestore()
})

test("Return consignment id from windows location", () => {
  const url = "https://test.gov.uk/consignment/1/upload"
  Object.defineProperty(window, "location", {
    value: {
      href: url,
      pathname: "/consignment/1/upload"
    }
  })

  expect(retrieveConsignmentId()).toBe(1)
})
