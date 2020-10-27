package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

class FileNotFoundController @Inject()(val controllerComponents: SecurityComponents,
                                  val keycloakConfiguration: KeycloakConfiguration) extends TokenSecurity with I18nSupport {

  def notFoundError(consignmentId: UUID): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.notFoundError(consignmentId.toString))
  }
}
