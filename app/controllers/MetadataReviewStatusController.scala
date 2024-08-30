package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, ConsignmentStatusService}
import services.Statuses._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class MetadataReviewStatusController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: ApplicationConfig
) extends TokenSecurity {

  def metadataReviewStatusPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockMetadataReview) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      for {
        consignmentStatus <- consignmentStatusService.consignmentStatusSeries(consignmentId, request.token.bearerAccessToken)
        reviewStatus = consignmentStatus.flatMap(s => consignmentStatusService.getStatusValues(s.consignmentStatuses, MetadataReviewType).values.headOption.flatten)
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield reviewStatus
        .map(status => Ok(views.html.standard.metadataReviewStatus(consignmentId, reference, request.token.name, request.token.email, status)))
        .getOrElse(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    }
  }
}
