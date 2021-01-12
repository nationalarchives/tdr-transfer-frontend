package viewsapi

case class FrontEndInfo(
                         apiUrl: String,
                         s3EndpointOverride: Option[String],
                         identityProviderName: String,
                         identityPoolId: String,
                         cognitoEndpointOverride: Option[String],
                         stage: String,
                         region: String,
                         cognitoRoleArn: String
                       )
