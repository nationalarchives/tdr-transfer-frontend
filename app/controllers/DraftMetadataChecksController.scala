package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import io.circe.syntax._
import io.circe.generic.auto._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class DraftMetadataChecksController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val frontEndInfoConfiguration: ApplicationConfig,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def draftMetadataChecksPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId)
    } yield {
      Ok(views.html.draftmetadata.draftMetadataChecks(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, request.token.name))
        .uncache()
    }
  }

  def draftMetadataValidationProgress(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request =>
    consignmentStatusService
      .getConsignmentStatuses(consignmentId)
      .map(_.asJson.noSpaces)
      .map(Ok(_))
  }
}
