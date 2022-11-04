package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import javax.inject.Inject

class ViewHistoryController @Inject() (val consignmentService: ConsignmentService, val keycloakConfiguration: KeycloakConfiguration, val controllerComponents: SecurityComponents)
    extends TokenSecurity {
  def viewConsignments(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.viewHistory(request.token.name))
  }
}
