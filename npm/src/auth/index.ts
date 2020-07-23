import Keycloak from "keycloak-js"
import AWS, { CognitoIdentityCredentials } from "aws-sdk"
import { IFrontEndInfo } from ".."

export const getKeycloakInstance: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  const keycloakInstance: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )

  const authenticated = await keycloakInstance.init({
    promiseType: "native",
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

export const refreshOrReturnToken: (
  keycloak: Keycloak.KeycloakInstance<"native">,
  tokenMinValidityInSecs?: number
) => Promise<string> = async (keycloak, tokenMinValidityInSecs = 30) => {
  if (keycloak.isTokenExpired(tokenMinValidityInSecs)) {
    await keycloak.updateToken(tokenMinValidityInSecs)
  }
  if (keycloak.token) {
    return keycloak.token
  } else {
    throw "Token is expired"
  }
}

export const authenticateAndGetIdentityId: (
  keycloak: Keycloak.KeycloakInstance<"native">,
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
