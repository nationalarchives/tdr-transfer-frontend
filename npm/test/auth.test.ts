import { getToken } from "../src/auth"
import Keycloak, { KeycloakPromise, KeycloakError } from "keycloak-js"

jest.mock("keycloak-js", () => jest.fn)
// const a: KeycloakPromise<boolean, KeycloakError> = Keycloak().init({})
// jest.spyOn(Keycloak(), "init").mockImplementation(() => KeycloakPromise())
test("a sample test", () => {
  getToken()
  expect(1 + 2).toBe(3)
})
