import { KeycloakTokenParsed } from "keycloak-js"
import {
  IKeycloakInstance,
  IKeycloakTokenParsed
} from "../src/upload"
import Mock = jest.Mock
import {IFrontEndInfo} from "../src";

const keycloakTokenParsed: { judgment_user?: boolean } = {}

export const createMockKeycloakInstance: (
  updateToken?: Mock,
  isTokenExpired?: boolean,
  refreshTokenParsed?: KeycloakTokenParsed,
  isJudgmentUser?: boolean
) => IKeycloakInstance = (
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
    tokenParsed: keycloakTokenParsed as IKeycloakTokenParsed,
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
    token: "fake-auth-token",
    didInitialize: true,
    // Added base Keycloak properties to satisfy IKeycloakInstance typing
    authenticated: true,
    responseMode: "query",
    responseType: "code",
    flow: "standard",
    realm: "test-realm",
    clientId: "test-client",
    refreshToken: "fake-refresh-token",
    tokenTimeout: 0,
    timeSkew: 0,
    loginRequired: false
  } as IKeycloakInstance
}

export const mockKeycloakInstance: IKeycloakInstance =
  createMockKeycloakInstance()

export const frontendInfo: IFrontEndInfo = {
  apiUrl: "",
  region: "",
  stage: "test",
  uploadUrl: "https://example.com",
  authUrl: "",
  clientId: "",
  realm: ""
}
