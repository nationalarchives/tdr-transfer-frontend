import Keycloak, { KeycloakInstance } from "keycloak-js"
import { IKeycloakTokenParsed } from "../upload"
import { handleUploadError, LoggedOutError } from "../errorhandling"

export const getKeycloakInstance: () => Promise<Keycloak.KeycloakInstance> =
  async () => {
    const keycloakInstance: Keycloak.KeycloakInstance = Keycloak(
      `${window.location.origin}/keycloak.json`
    )

    const authenticated = await keycloakInstance.init({
      onLoad: "check-sso",
      silentCheckSsoRedirectUri: window.location.origin + "/silent-sso-login"
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
  token: IKeycloakTokenParsed | undefined
) => boolean = (token) => {
  const now = Math.round(new Date().getTime() / 1000)
  return token != undefined && token.exp != undefined && token.exp < now
}

export const scheduleTokenRefresh: (
  keycloak: KeycloakInstance,
  cookiesUrl: string,
  idleSessionMinValiditySecs?: number
) => void = (keycloak, cookiesUrl, idleSessionMinValiditySecs = 60) => {
  const refreshToken = keycloak.refreshTokenParsed
  if (refreshToken != undefined && refreshToken.exp != undefined) {
    const nowInSecs = Math.round(new Date().getTime() / 1000)
    const expInSecs = refreshToken.exp
    //Expiry is a future time, add min validity to the 'now' to check if expiry is about to expire
    const timeoutInMs =
      (expInSecs - (nowInSecs + idleSessionMinValiditySecs)) * 1000

    setTimeout(() => {
      refreshOrReturnToken(keycloak).then(() => {
        fetch(cookiesUrl, {
          credentials: "include",
          headers: { Authorization: `Bearer ${keycloak.token}` }
        }).then(() => {
          scheduleTokenRefresh(keycloak, cookiesUrl)
        })
      })
    }, timeoutInMs)
  }
}

export const refreshOrReturnToken: (
  keycloak: Keycloak.KeycloakInstance,
  tokenMinValidityInSecs?: number
) => Promise<string> = async (keycloak, tokenMinValidityInSecs = 30) => {
  if (keycloak.isTokenExpired(tokenMinValidityInSecs)) {
    if (isRefreshTokenExpired(keycloak.refreshTokenParsed)) {
      handleUploadError(
        new LoggedOutError(keycloak.createLoginUrl(), "User is logged out"),
        "Refresh token has expired"
      )
    }
    await keycloak.updateToken(tokenMinValidityInSecs)
  }
  if (keycloak.token) {
    return keycloak.token
  } else {
    throw "Something really odd has happened, Keycloak is down or something"
  }
}
