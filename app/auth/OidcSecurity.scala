package auth

import org.pac4j.core.authorization.authorizer.DefaultAuthorizers
import controllers.routes

import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{SecureAction, Security}
import play.api.mvc.{Action, AnyContent, Request, Result}

trait OidcSecurity extends Security[CommonProfile] {

  val secureAction: SecureAction[CommonProfile, AnyContent, AuthenticatedRequest] = Secure("OidcClient", authorizers = DefaultAuthorizers.NONE)

  def transferAgreementExistsAction(consignmentId: Long)(f: Request[AnyContent] => Result): Action[AnyContent]
  = secureAction { implicit request: Request[AnyContent] =>
    if(false) {
      f(request)
    } else {
      Redirect(routes.TransferAgreementController.transferAgreement(consignmentId))
    }
  }
}
