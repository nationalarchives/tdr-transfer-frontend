package auth

import org.pac4j.core.authorization.authorizer.DefaultAuthorizers
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{SecureAction, Security}
import play.api.mvc.AnyContent

trait OidcSecurity extends Security[CommonProfile] {
  val secureAction: SecureAction[CommonProfile, AnyContent, AuthenticatedRequest] = Secure("OidcClient", authorizers = DefaultAuthorizers.NONE)
}
