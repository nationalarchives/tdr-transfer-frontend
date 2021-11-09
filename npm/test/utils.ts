import {KeycloakInstance, KeycloakTokenParsed} from "keycloak-js"
import {
  IKeycloakInstanceWithJudgmentUser,
  IKeycloakTokenParsedWithJudgmentUser
} from "../src/upload"
import Mock = jest.Mock;

export const createMockKeycloakInstance: (
    updateToken?: Mock,
    isTokenExpired?: boolean,
    refreshTokenParsed?: KeycloakTokenParsed,
    tokenParsed?: IKeycloakTokenParsedWithJudgmentUser
) => IKeycloakInstanceWithJudgmentUser = (updateToken = jest.fn(),
                         isTokenExpired = false,
                         refreshTokenParsed,
                                          tokenParsed) => {

  return {
    refreshTokenParsed,
    tokenParsed,
    init: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
    register: jest.fn(),
    accountManagement: jest.fn(),
    createLoginUrl: jest.fn(),
    createLogoutUrl: jest.fn(),
    createRegisterUrl: jest.fn(),
    createAccountUrl: jest.fn(),
    isTokenExpired: () => { return isTokenExpired },
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  } as IKeycloakInstanceWithJudgmentUser
}

export const mockKeycloakInstance: IKeycloakInstanceWithJudgmentUser = createMockKeycloakInstance()
