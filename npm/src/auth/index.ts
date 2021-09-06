import Keycloak, {KeycloakInstance, KeycloakTokenParsed} from "keycloak-js"
import AWS, { CognitoIdentity, Credentials } from "aws-sdk"
import { LoggedOutError } from "../errorhandling"
import { IFrontEndInfo } from ".."
import { GetIdInput } from "aws-sdk/clients/cognitoidentity"

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
  token: KeycloakTokenParsed | undefined,
  tokenMinValidityInSecs? : number
) => boolean = (token, tokenMinValidityInSecs = 0) => {
  const now = Math.round(new Date().getTime() / 1000)
  //Add the min token validity to 'now' as token.exp is a future time, so will return true sooner than the expiry time
  //This will give time to refresh the token for the calling client
  return token != undefined && token.exp != undefined && token.exp < (now + tokenMinValidityInSecs)
}

export const idleSessionTimeoutAboutToExpire: (
    keycloak: Keycloak.KeycloakInstance,
    idleSessionMinValiditySecs? : number
) => boolean = (keycloak, idleSessionMinValiditySecs= 30) => {

  return isRefreshTokenExpired(keycloak.refreshTokenParsed, idleSessionMinValiditySecs)
}

export const refreshOrReturnToken: (
  keycloak: Keycloak.KeycloakInstance,
  tokenMinValidityInSecs?: number
) => Promise<string> = async (keycloak, tokenMinValidityInSecs = 30) => {
  if (keycloak.isTokenExpired(tokenMinValidityInSecs)) {
    if (isRefreshTokenExpired(keycloak.refreshTokenParsed)) {
      throw new LoggedOutError(keycloak.createLoginUrl(), "User is logged out")
    }
    console.log("Update token")
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
  frontEndInfo: IFrontEndInfo,
  cognitoIdentity: CognitoIdentity,
  sts: AWS.STS
) => Promise<string> = async (keycloak, frontEndInfo, cognitoIdentity, sts) => {
  const token = await refreshOrReturnToken(keycloak)
  const { identityProviderName, identityPoolId, region } = frontEndInfo

  const options: GetIdInput = {
    IdentityPoolId: identityPoolId,
    Logins: { [identityProviderName]: token }
  }
  const id = await cognitoIdentity.getId(options).promise()
  if (id.IdentityId) {
    const identityId = id.IdentityId
    const openIdToken = await cognitoIdentity
      .getOpenIdToken({
        IdentityId: identityId,
        Logins: { [identityProviderName]: token }
      })
      .promise()

    if (openIdToken.Token) {
      const response = sts
        .assumeRoleWithWebIdentity({
          DurationSeconds: 60 * 60 * 3,
          RoleArn: frontEndInfo.cognitoRoleArn,
          RoleSessionName: identityId.split(":")[1], //Cognito user uuid. Can see who did what in cloudtrail
          WebIdentityToken: openIdToken.Token
        })
        .promise()

      const assumeRole = await response
      if (assumeRole.Credentials) {
        const creds = assumeRole.Credentials

        AWS.config.update({
          credentials: new Credentials({
            accessKeyId: creds.AccessKeyId,
            secretAccessKey: creds.SecretAccessKey,
            sessionToken: creds.SessionToken
          })
        })
      } else {
        throw new Error("Cannot get credentials from sts")
      }
    } else {
      throw new Error("Cannot get an openid token from cognito")
    }
    return id.IdentityId
  } else {
    throw new Error("Cannot get cognito identity id")
  }
}
