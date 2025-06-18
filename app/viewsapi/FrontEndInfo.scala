package viewsapi

case class FrontEndInfo(
    apiUrl: String,
    stage: String,
    region: String,
    uploadUrl: String,
    authUrl: String,
    clientId: String,
    realm: String,
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    awsSessionToken: String
)
