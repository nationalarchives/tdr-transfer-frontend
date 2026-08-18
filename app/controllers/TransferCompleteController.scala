package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.TransferCompleteEvent
import services.Statuses.ConfirmTransferType
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TransferCompleteController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val messagingService: MessagingService,
    val applicationConfig: ApplicationConfig,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  private def sendMessage(statuses: List[ConsignmentStatuses]): Boolean = {
    !statuses.exists(_.statusType == ConfirmTransferType.id)
  }

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentTransferSummary <- consignmentService.getConsignmentConfirmTransfer(consignmentId, request.token.bearerAccessToken)
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
    } yield {
      if (sendMessage(consignmentStatuses)) {
        messagingService.sendTransferCompleteNotification(
          TransferCompleteEvent(
            transferringBodyName = consignmentTransferSummary.transferringBodyName,
            consignmentReference = consignmentTransferSummary.consignmentReference,
            consignmentId = consignmentId.toString,
            seriesName = consignmentTransferSummary.seriesName,
            userId = request.token.userId.toString,
            userEmail = request.token.email
          )
        )
      }
      Ok(views.html.standard.transferComplete(consignmentId, consignmentTransferSummary.consignmentReference, request.token.name))
    }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference, request.token.name))
      }
  }
}
