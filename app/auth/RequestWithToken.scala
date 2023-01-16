package auth

import play.api.mvc.{AnyContent, Request}
import uk.gov.nationalarchives.tdr.keycloak.Token

class RequestWithToken(request: Request[AnyContent], val token: Token)

object RequestWithToken {
  def apply(request: Request[AnyContent], token: Option[Token]): RequestWithToken = new RequestWithToken(request, token.getOrElse(throw new RuntimeException("Token not provided")))
}
