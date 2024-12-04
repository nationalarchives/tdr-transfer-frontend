import {test, expect, vi} from "vitest"
import { getKeycloakInstance } from "../src/auth";
import {isError} from "../src/errorhandling";
import {KeycloakInitOptions} from "keycloak-js";
import { fetch } from 'cross-fetch'

global.fetch = fetch

vi.doMock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

const keycloakMock = {
    __esModule: true,
    namedExport: vi.fn(),
    default: vi.fn()
}
vi.doMock("keycloak-js", () => keycloakMock)

export function sum(a: number, b: number) {
    return a + b
}

test('adds 1 + 2 to equal 3', () => {
    expect(sum(1, 2)).toBe(3)
})

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

test("Redirects user to login page and returns a new token if the user is not authenticated", async () => {
    // const mock = vi.fn().mockImplementation(getLatest)
    keycloakMock.default.mockImplementation(
        () => new MockKeycloakUnauthenticated()
    )
    const instance = await getKeycloakInstance()
    expect(isError(instance)).toBe(false)
    if(!isError(instance)) {
        expect(instance.token).toEqual("fake-auth-login-token")
    }
})