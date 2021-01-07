import Keycloak, { KeycloakTokenParsed } from "keycloak-js"
import AWS, { CognitoIdentity, Credentials } from "aws-sdk"
import { LoggedOutError } from "../errorhandling"
import { IFrontEndInfo } from ".."
import { GetIdInput } from "aws-sdk/clients/cognitoidentity"

export const getKeycloakInstance: () => Promise<Keycloak.KeycloakInstance> = async () => {
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

  const cognitoIdentity = new CognitoIdentity({ region })
  const options: GetIdInput = {
    IdentityPoolId: identityPoolId,
    Logins: { [identityProviderName]: token }
  }

  const id = await cognitoIdentity.getId(options).promise()

  const openIdToken = await cognitoIdentity
    .getOpenIdToken({
      IdentityId: id.IdentityId!,
      Logins: { [identityProviderName]: token }
    })
    .promise()

  const sts = new AWS.STS({ region: "eu-west-2" })
  const response = sts
    .assumeRoleWithWebIdentity({
      DurationSeconds: 12 * 60 * 60,
      RoleArn: frontEndInfo.cognitoRoleArn,
      RoleSessionName: "Bob",
      WebIdentityToken: openIdToken.Token!
    })
    .promise()

  const assumeRole = await response
  const creds = assumeRole.Credentials!

  AWS.config.update({
    credentials: new Credentials({
      accessKeyId: creds.AccessKeyId,
      secretAccessKey: creds.SecretAccessKey,
      sessionToken: creds.SessionToken
    })
  })

  return id.IdentityId!
}
