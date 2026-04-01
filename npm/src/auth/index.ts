import Keycloak from "keycloak-js"
import { IKeycloakTokenParsed } from "../upload"
import { IFrontEndInfo } from "../index"

export const getKeycloakInstance: (
  frontEndInfo: IFrontEndInfo
) => Promise<Keycloak | Error> = async (frontEndInfo: IFrontEndInfo) => {
  const keycloakInstance = new Keycloak({
    url: `${frontEndInfo.authUrl}`,
    realm: `${frontEndInfo.realm}`,
    clientId: `${frontEndInfo.clientId}`
  })
    console.log("++++++ realm: " +  frontEndInfo.realm + " " + frontEndInfo.clientId)
  keycloakInstance.onTokenExpired = () => console.log("++++++++++ TOKEN EXPIRED ++++++++++")

  const errorHandlingModule = await import("../errorhandling")
  try {
    const authenticated = await keycloakInstance.init({
      onLoad: "check-sso",
      silentCheckSsoRedirectUri: window.location.origin + "/silent-sso-login"
    })
      //TODO: the timeskew is typically 5-8 seconds
    console.log("===== timeSkew: " + keycloakInstance.timeSkew)
    if (errorHandlingModule.isError(authenticated)) {
      return authenticated
    } else {
      if (!authenticated) {
        console.log("User is not authenticated. Redirecting to login page")
        await keycloakInstance.login({
          redirectUri: window.location.href,
          prompt: "login"
        })
      }
    }
  } catch (e) {
    return Error(errorHandlingModule.getErrorMessage(e))
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
  keycloak: Keycloak,
  cookiesUrl: string,
  idleSessionMinValiditySecs?: number
) => void = (keycloak, cookiesUrl, idleSessionMinValiditySecs = 60) => {
  const refreshToken = keycloak.refreshTokenParsed
  if (refreshToken != undefined && refreshToken.exp != undefined) {

      console.log("-----------------------")

    const nowInSecs = Math.round(new Date().getTime() / 1000)

    const expInSecs = refreshToken.exp
    //Expiry is a future time, add min validity to the 'now' to check if expiry is about to expire
    const timeoutInMs =
      (expInSecs - (nowInSecs + idleSessionMinValiditySecs)) * 1000
      console.log("--- nowInSec " + nowInSecs)
      console.log("--- refreshToken.exp " + refreshToken.exp)
      console.log("--- mas valid " + idleSessionMinValiditySecs)
      console.log("--- timeout in ms " +  timeoutInMs)
      console.log("-----------------------")
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
  keycloak: Keycloak,
  tokenMinValidityInSecs?: number
) => Promise<string | Error> = async (
  keycloak,
  tokenMinValidityInSecs = 30
) => {
    console.log("****** REFRESHED ********  AAA")
  if (keycloak.isTokenExpired(tokenMinValidityInSecs)) {
      console.log("****** REFRESHED ********  BBB")
    if (isRefreshTokenExpired(keycloak.refreshTokenParsed)) {
        console.log("****** REFRESHED ********  CCC")
      const errorHandlingModule = await import("../errorhandling")
      const error = new errorHandlingModule.LoggedOutError(
        await keycloak.createLoginUrl(),
        "Refresh token has expired: User is logged out"
      )
      errorHandlingModule.handleUploadError(error)
      return error
    } else {
        console.log("****** REFRESHED ********  DDD")
      await keycloak.updateToken(tokenMinValidityInSecs).catch((err) => {
        return new Error(err)
      })
    }
  }
  if (keycloak.token) {
    return keycloak.token
  } else {
    //We shouldn't normally throw Errors but this is exceptional
    throw `No token is available on instance ${keycloak.authServerUrl}`
  }
}
