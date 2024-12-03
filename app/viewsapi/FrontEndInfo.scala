package viewsapi

case class FrontEndInfo(
    apiUrl: String,
    stage: String,
    region: String,
    uploadUrl: String,
    authUrl: String,
    clientId: String,
    realm: String
)
