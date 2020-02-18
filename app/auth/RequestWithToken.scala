package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import play.api.mvc.{AnyContent, Request}

class RequestWithToken(request: Request[AnyContent], val token: BearerAccessToken)
object RequestWithToken {
  def apply(request: Request[AnyContent], token: BearerAccessToken): RequestWithToken = new RequestWithToken(request, token)
}
