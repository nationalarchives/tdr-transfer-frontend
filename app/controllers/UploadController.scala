package controllers

import auth.TokenSecurity
import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class UploadController @Inject()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] with I18nSupport {
  private val secureAction = Secure("OidcClient", authorizers = "custom")

  def uploadPage(consignmentId: Long): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.upload())
  }
}
