package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.ConsignmentService

import javax.inject._
import scala.concurrent.ExecutionContext

@Singleton
class RegistrationController @Inject() (val controllerComponents: SecurityComponents, val keycloakConfiguration: KeycloakConfiguration, val consignmentService: ConsignmentService)(
    implicit val ec: ExecutionContext
) extends TokenSecurity
    with I18nSupport {

  def complete(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.registrationComplete(request.token.name))
  }
}
