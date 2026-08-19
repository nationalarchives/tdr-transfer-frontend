package controllers

import auth.UnprotectedPageController
import configuration.ApplicationConfig
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

import javax.inject.{Inject, Singleton}

@Singleton
class ConnectorSharePointController @Inject() (
    securityComponents: SecurityComponents,
    val applicationConfig: ApplicationConfig
) extends UnprotectedPageController(securityComponents)
    with I18nSupport {
  private val blockPages = applicationConfig.blockConnectorSharePointPages

  private def notFound(request: Request[AnyContent]) = {
    NotFound(views.html.notFoundError("", isLoggedIn = false, isJudgmentUser = false)(request2Messages(request), request))
  }

  def help(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (blockPages) {
      notFound(request)
    } else Ok(views.html.connectorforsharepoint.help(request.isLoggedIn, request.name))
  }

  def licence(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (blockPages) {
      notFound(request)
    } else Ok(views.html.connectorforsharepoint.licence(request.isLoggedIn, request.name))
  }

  def privacyPolicy(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (blockPages) {
      notFound(request)
    } else Ok(views.html.connectorforsharepoint.privacyPolicy(request.isLoggedIn, request.name))
  }
}
