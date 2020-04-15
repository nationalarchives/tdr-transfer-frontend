import Keycloak from "keycloak-js"
import AWS, { CognitoIdentityCredentials } from "aws-sdk"

declare var TDR_AUTH_URL: string
declare var TDR_IDENTITY_POOL_ID: string

export const getToken: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )
  const authenticated: boolean = await keycloak.init({
    promiseType: "native",
    onLoad: "check-sso"
  })

  if (authenticated) {
    return keycloak
  } else {
    throw "User is not authenticated"
  }
}

export const refreshOrReturnToken: (
  keycloak: Keycloak.KeycloakInstance<"native">
) => Promise<string> = async keycloak => {
  if (keycloak.isTokenExpired(30)) {
    await keycloak.updateToken(30)
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
      [TDR_AUTH_URL]: token
    }
  })
  AWS.config.update({ region: "eu-west-2", credentials })
  await credentials.getPromise()
  const { identityId } = credentials
  return identityId
}
