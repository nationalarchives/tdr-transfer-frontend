import Keycloak, { KeycloakTokenParsed } from "keycloak-js"
import AWS, { CognitoIdentityCredentials } from "aws-sdk"
import { LoggedOutError } from "../errorhandling"
import { IFrontEndInfo } from ".."

export const getKeycloakInstance: () => Promise<
  Keycloak.KeycloakInstance
> = async () => {
  const keycloakInstance: Keycloak.KeycloakInstance = Keycloak(
    `${window.location.origin}/keycloak.json`
  )

  const authenticated = await keycloakInstance.init({
    onLoad: "check-sso",
    silentCheckSsoRedirectUri:
      window.location.origin + "/assets/html/silent-check-sso.html"
  })

  if (!authenticated) {
    console.log("User is not authenticated. Redirecting to login page")
    await keycloakInstance.login({
      redirectUri: window.location.href,
      prompt: "login"
    })
  }

  return keycloakInstance
}

const isRefreshTokenExpired: (
  token: KeycloakTokenParsed | undefined
) => boolean = (token) => {
  const now = Math.round(new Date().getTime() / 1000)
  return token != undefined && token.exp != undefined && token.exp < now
}

export const refreshOrReturnToken: (
  keycloak: Keycloak.KeycloakInstance,
  tokenMinValidityInSecs?: number
) => Promise<string> = async (keycloak, tokenMinValidityInSecs = 30) => {
  if (keycloak.isTokenExpired(tokenMinValidityInSecs)) {
    if (isRefreshTokenExpired(keycloak.refreshTokenParsed)) {
      throw new LoggedOutError(keycloak.createLoginUrl(), "User is logged out")
    }
    await keycloak.updateToken(tokenMinValidityInSecs)
  }
  if (keycloak.token) {
    return keycloak.token
  } else {
    throw "Token is expired"
  }
}

export const authenticateAndGetIdentityId: (
  keycloak: Keycloak.KeycloakInstance,
  frontEndInfo: IFrontEndInfo
) => Promise<string> = async (keycloak, frontEndInfo) => {
  const token = await refreshOrReturnToken(keycloak)
  const { identityProviderName, identityPoolId, region } = frontEndInfo

  const credentials = new CognitoIdentityCredentials({
    IdentityPoolId: identityPoolId,
    Logins: {
      [identityProviderName]: token
    }
  })
  AWS.config.update({ region, credentials })
  await credentials.getPromise()
  const { identityId } = credentials
  return identityId
}
