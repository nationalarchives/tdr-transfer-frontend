import {KeycloakInstance, KeycloakTokenParsed} from "keycloak-js"
import Mock = jest.Mock;

export const createMockKeycloakInstance: (
    updateToken?: Mock,
    isTokenExpired?: Mock,
    refreshTokenParsed?: KeycloakTokenParsed
) => KeycloakInstance = (updateToken = jest.fn(),
                         isTokenExpired = jest.fn(),
                         refreshTokenParsed) => {

  return {
    refreshTokenParsed,
    init: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
    register: jest.fn(),
    accountManagement: jest.fn(),
    createLoginUrl: jest.fn(),
    createLogoutUrl: jest.fn(),
    createRegisterUrl: jest.fn(),
    createAccountUrl: jest.fn(),
    isTokenExpired,
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }
}

export const mockKeycloakInstance: KeycloakInstance = createMockKeycloakInstance()
