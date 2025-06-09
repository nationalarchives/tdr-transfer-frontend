package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services.{ConsignmentService, ConsignmentStatusService}

import java.util.UUID
import javax.inject.Inject

class MetadataReviewStatusController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: ApplicationConfig
) extends TokenSecurity {

  def metadataReviewStatusPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentStatus <- consignmentStatusService.consignmentStatusSeries(consignmentId, request.token.bearerAccessToken)
      reviewStatus = consignmentStatus.flatMap(s => consignmentStatusService.getStatusValues(s.consignmentStatuses, MetadataReviewType).values.headOption.flatten)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield reviewStatus
      .map(status => Ok(views.html.standard.metadataReviewStatus(consignmentId, reference, request.token.name, request.token.email, status)))
      .getOrElse(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
  }
}
