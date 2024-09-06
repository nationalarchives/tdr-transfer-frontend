package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DraftMetadataChecksResultsController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def draftMetadataChecksResultsPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      val token = request.token.bearerAccessToken
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      } yield {
        Ok(
          views.html.draftmetadata
            .draftMetadataChecksResults(consignmentId, reference, getValue(consignmentStatuses, DraftMetadataType), request.token.name)
        )
          .uncache()
      }
    }
  }

  def getValue(statuses: List[GetConsignment.ConsignmentStatuses], statusType: StatusType): DraftMetadataProgress = {
    val failed = DraftMetadataProgress("FAILED", "red")
    statuses.find(_.statusType == statusType.id).map(_.value).map {
      case FailedValue.value              => failed
      case CompletedWithIssuesValue.value => DraftMetadataProgress("ERRORS", "red")
      case CompletedValue.value           => DraftMetadataProgress("IMPORTED", "blue")
      case _                              => failed
    } getOrElse failed
  }
}

case class DraftMetadataProgress(value: String, colour: String)
