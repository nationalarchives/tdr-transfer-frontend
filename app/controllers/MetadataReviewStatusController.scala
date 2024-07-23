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

  def metadataReviewStatusPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockMetadataReview) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      for {
        consignmentStatus <- consignmentStatusService.consignmentStatusSeries(consignmentId, request.token.bearerAccessToken)
        reviewStatus = consignmentStatus.flatMap(s => consignmentStatusService.getStatusValues(s.consignmentStatuses, MetadataReviewType).values.headOption.flatten)
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        result <- reviewStatus match {
          case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(CompletedWithIssuesValue.value) =>
            Future(
              Ok(views.html.standard.metadataReviewStatus(consignmentId, reference, request.token.name, request.token.email, reviewStatus.get))
            )
          case None =>
            Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
          case _ =>
            throw new IllegalStateException(s"Unexpected review status: $reviewStatus for consignment $consignmentId")
        }
      } yield result
    }
  }

  def metadataReviewActionRequired(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      _ <- consignmentStatusService.updateConsignmentStatus(
        ConsignmentStatusInput(consignmentId, DescriptiveMetadataType.id, Some(InProgressValue.value)),
        request.token.bearerAccessToken
      )
      _ <- consignmentStatusService.updateConsignmentStatus(
        ConsignmentStatusInput(consignmentId, ClosureMetadataType.id, Some(InProgressValue.value)),
        request.token.bearerAccessToken
      )
      _ <- consignmentStatusService.updateConsignmentStatus(
        ConsignmentStatusInput(consignmentId, DraftMetadataType.id, Some(InProgressValue.value)),
        request.token.bearerAccessToken
      )
    } yield {
      Redirect(routes.AdditionalMetadataController.start(consignmentId))
    }
  }
}
