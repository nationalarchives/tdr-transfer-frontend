import Keycloak from "keycloak-js"
import AWS, {
  Credentials,
  CognitoIdentity,
  CognitoIdentityCredentials
} from "aws-sdk"

export interface IAuthenticatedUpload {
  s3: AWS.S3
  identityId: string
}

declare var TDR_AUTH_URL: string
declare var TDR_IDENTITY_POOL_ID: string

export const getToken: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak()

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

export const getAuthenticatedUploadObject: (
  token: string
) => Promise<IAuthenticatedUpload> = async token => {
  const credentials = new CognitoIdentityCredentials({
    IdentityPoolId: TDR_IDENTITY_POOL_ID,
    Logins: {
      [TDR_AUTH_URL]: token
    }
  })
  AWS.config.update({ region: "eu-west-2", credentials })
  await credentials.getPromise()
  const { identityId } = credentials
  return { s3: new AWS.S3(), identityId }
}
