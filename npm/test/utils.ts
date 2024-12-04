import { KeycloakTokenParsed } from "keycloak-js"
import {
  IKeycloakInstance,
  IKeycloakTokenParsed
} from "../src/upload"
import {Mock, vi} from "vitest";

const keycloakTokenParsed: { judgment_user?: boolean } = {}

export const createMockKeycloakInstance: (
  updateToken?: Mock,
  isTokenExpired?: boolean,
  refreshTokenParsed?: KeycloakTokenParsed,
  isJudgmentUser?: boolean
) => IKeycloakInstance = (
  updateToken = vi.fn(),
  isTokenExpired = false,
  refreshTokenParsed,
  isJudgmentUser = false
) => {
  if (isJudgmentUser) {
    keycloakTokenParsed["judgment_user"] = true
  }
  return {
    refreshTokenParsed,
    tokenParsed: keycloakTokenParsed as IKeycloakTokenParsed,
    init: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    accountManagement: vi.fn(),
    createLoginUrl: vi.fn(),
    createLogoutUrl: vi.fn(),
    createRegisterUrl: vi.fn(),
    createAccountUrl: vi.fn(),
    isTokenExpired: () => {
      return isTokenExpired
    },
    updateToken,
    clearToken: vi.fn(),
    hasRealmRole: vi.fn(),
    hasResourceRole: vi.fn(),
    loadUserInfo: vi.fn(),
    loadUserProfile: vi.fn(),
    token: "fake-auth-token"
  } as IKeycloakInstance
}

export const mockKeycloakInstance: IKeycloakInstance =
  createMockKeycloakInstance()
