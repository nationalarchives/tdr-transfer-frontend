package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.TransferCompleteEvent
import services.{ConsignmentService, MessagingService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TransferCompleteController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val messagingService: MessagingService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentTransferSummary <- consignmentService.getConsignmentConfirmTransfer(consignmentId)
    } yield {
//      messagingService.sendTransferCompleteNotification(
//        TransferCompleteEvent(
//          transferringBodyName = if (consignmentTransferSummary.transferringBodyName.isEmpty) {None} else {Option(consignmentTransferSummary.transferringBodyName)},
//          consignmentReference = consignmentTransferSummary.consignmentReference,
//          consignmentId = consignmentId.toString,
//          seriesName = consignmentTransferSummary.seriesName,
//          userId = request.token.userId.toString,
//          userEmail = request.token.email
//        )
//      )
      Ok(views.html.standard.transferComplete(consignmentId, consignmentTransferSummary.consignmentReference, request.token.name))
    }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference, request.token.name))
      }
  }
}
