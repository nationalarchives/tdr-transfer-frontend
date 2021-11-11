import { KeycloakTokenParsed } from "keycloak-js"
import {
  IKeycloakInstanceWithJudgmentUser,
  IKeycloakTokenParsedWithJudgmentUser
} from "../src/upload"
import Mock = jest.Mock

const keycloakTokenParsed: { judgment_user?: boolean } = {}

export const createMockKeycloakInstance: (
  updateToken?: Mock,
  isTokenExpired?: boolean,
  refreshTokenParsed?: KeycloakTokenParsed,
  isJudgmentUser?: boolean
) => IKeycloakInstanceWithJudgmentUser = (
  updateToken = jest.fn(),
  isTokenExpired = false,
  refreshTokenParsed,
  isJudgmentUser = false
) => {
  if (isJudgmentUser) {
    keycloakTokenParsed["judgment_user"] = true
  }
  return {
    refreshTokenParsed,
    tokenParsed: keycloakTokenParsed as IKeycloakTokenParsedWithJudgmentUser,
    init: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
    register: jest.fn(),
    accountManagement: jest.fn(),
    createLoginUrl: jest.fn(),
    createLogoutUrl: jest.fn(),
    createRegisterUrl: jest.fn(),
    createAccountUrl: jest.fn(),
    isTokenExpired: () => {
      return isTokenExpired
    },
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  } as IKeycloakInstanceWithJudgmentUser
}

export const mockKeycloakInstance: IKeycloakInstanceWithJudgmentUser =
  createMockKeycloakInstance()
