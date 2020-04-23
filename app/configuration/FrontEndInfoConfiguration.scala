package configuration

import javax.inject.Inject
import play.api.Configuration
import viewsapi.FrontEndInfo

class FrontEndInfoConfiguration @Inject ()(configuration: Configuration) {
  private def get(location: String) = configuration.get[String](location)
  def frontEndInfo: FrontEndInfo = FrontEndInfo(get("consignmentapi.url"), get("cognito.identityProviderName"), get("cognito.identitypool"), get("environment"), get("region"))
}
