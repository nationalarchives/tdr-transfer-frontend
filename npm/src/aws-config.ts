import AWS from "aws-sdk"
import { IFrontEndInfo } from "."

export const configureAws: (frontEndInfo: IFrontEndInfo) => void = (
  frontEndInfo: IFrontEndInfo
) => {
  if (frontEndInfo.cognitoEndpointOverride) {
    overrideCognitoEndpoint(frontEndInfo.cognitoEndpointOverride)
  }
  if (frontEndInfo.s3EndpointOverride) {
    overrideS3Endpoint(frontEndInfo.s3EndpointOverride)
  }
}

const overrideCognitoEndpoint: (cognitoEndpointOverride: string) => void = (
  cognitoEndpointOverride: string
) => {
  console.log(`Overriding Cognito endpoint with '${cognitoEndpointOverride}'`)
  AWS.config.cognitoidentity = {
    endpoint: cognitoEndpointOverride
  }
}

const overrideS3Endpoint: (s3EndpointOverride: string) => void = (
  s3EndpointOverride: string
) => {
  console.log(`Overriding S3 endpoint with '${s3EndpointOverride}'`)
  AWS.config.s3 = {
    endpoint: s3EndpointOverride,
    s3ForcePathStyle: true
  }
}
