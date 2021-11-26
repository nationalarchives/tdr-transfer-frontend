package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, SeriesService}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class MetadataController @Inject()(val controllerComponents: SecurityComponents,
                                  val keycloakConfiguration: KeycloakConfiguration
                                 )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport { {

  def displayMetadata: Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    Future(Ok)
  }
}
