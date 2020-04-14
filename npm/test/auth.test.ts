import { getToken, getAuthenticatedUploadObject } from "../src/auth"
jest.mock("keycloak-js")
jest.mock("aws-sdk")

import Keycloak from "keycloak-js"
import AWS from "aws-sdk"

class MockKeycloakAuthenticated {
  token: string = "fake-auth-token"

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
  await expect(getToken()).rejects.toEqual("User is not authenticated")
})

test("Returns a token if the user is logged in", async () => {
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakAuthenticated()
  })
  await expect(getToken()).resolves.toEqual({ token: "fake-auth-token" })
})

test("Returns an error if login attempt fails", async () => {
  ;(Keycloak as jest.Mock).mockImplementation(() => {
    return new MockKeycloakError()
  })
  await expect(getToken()).rejects.toEqual("There has been an error")
})

test("Returns the correct authenticated upload object", async () => {
  const config = AWS.config.update as jest.Mock
  const token = "testtoken"
  config.mockImplementation(() => console.log("update called"))

  const { s3, identityId } = await getAuthenticatedUploadObject(token)
  expect(s3).not.toBeUndefined()
  expect(identityId).toBeUndefined()
  expect(config).toHaveBeenCalled()
})
