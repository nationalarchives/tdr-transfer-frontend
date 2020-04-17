import Keycloak from "keycloak-js"
import AWS, { CognitoIdentityCredentials } from "aws-sdk"

declare var TDR_IDENTITY_PROVIDER_NAME: string
declare var TDR_IDENTITY_POOL_ID: string
declare var REGION: string

export const getKeycloakInstance: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  const keycloakInstance: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )
  const authenticated: boolean = await keycloakInstance.init({
    promiseType: "native",
    onLoad: "check-sso"
  })

  if (authenticated) {
    return keycloakInstance
  } else {
    throw "User is not authenticated"
  }
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
  keycloak: Keycloak.KeycloakInstance<"native">
) => Promise<string> = async keycloak => {
  const token = await refreshOrReturnToken(keycloak)
  const credentials = new CognitoIdentityCredentials({
    IdentityPoolId: TDR_IDENTITY_POOL_ID,
    Logins: {
      [TDR_IDENTITY_PROVIDER_NAME]: token
    }
  })
  AWS.config.update({ region: REGION, credentials })
  await credentials.getPromise()
  const { identityId } = credentials
  return identityId
}
