import AWS, { Credentials } from "aws-sdk"
import { IFrontEndInfo } from "."

export const configureAws: (frontEndInfo: IFrontEndInfo) => void = (
  frontEndInfo: IFrontEndInfo
) => {
  if (frontEndInfo.uploadUrl) {
    overrideS3Endpoint(frontEndInfo.uploadUrl)
  }
}

const overrideS3Endpoint: (s3EndpointOverride: string) => void = (
  s3EndpointOverride: string
) => {
  const endpoint = new AWS.Endpoint(s3EndpointOverride)
  AWS.config.update({
    credentials: new Credentials({
      // The AWS SDK requires credentials to generate a request, but we are not using them for authentication so set
      // them to placeholder values
      accessKeyId: "placeholder-id",
      secretAccessKey: "placeholder-secret"
    }),
    httpOptions: {
      xhrWithCredentials: true
    }
  })

  AWS.config.s3 = {
    endpoint: endpoint,
    s3BucketEndpoint: true
  }
}
