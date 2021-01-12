package configuration

import javax.inject.Inject
import play.api.Configuration
import viewsapi.FrontEndInfo

class FrontEndInfoConfiguration @Inject ()(configuration: Configuration) {

  private def get(location: String) = configuration.get[String](location)
  private def getOptional(location: String) = configuration.getOptional[String](location)

  def frontEndInfo: FrontEndInfo = FrontEndInfo(
    get("consignmentapi.url"),
    getOptional("s3.endpointOverride"),
    get("cognito.identityProviderName"),
    get("cognito.identitypool"),
    getOptional("cognito.endpointOverride"),
    get("environment"),
    get("region"),
    get("cognito.roleArn")
  )
}
