package validation

import auth.TokenSecurity
import configuration.GraphQLConfiguration
import controllers.routes
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}

import java.util.UUID
import scala.concurrent.ExecutionContext

abstract class ValidatedActions() extends TokenSecurity with I18nSupport {
  implicit val ec: ExecutionContext
  val graphqlConfiguration: GraphQLConfiguration

  def consignmentExists(consignmentId: UUID)(f: Request[AnyContent] => Result): Action[AnyContent] = secureAction.async {
    implicit request: Request[AnyContent] =>
      val consignmentService = new ConsignmentService(graphqlConfiguration)
      val consignmentExists = consignmentService.consignmentExists(consignmentId, request.token.bearerAccessToken)
      consignmentExists.map {
        case true => f(request)
        case false => NotFound(views.html.notFoundError())
      }
  }

  def uploadPermitted(consignmentId: UUID)(f: Request[AnyContent] => Result): Action[AnyContent] = secureAction.async {
    implicit request: Request[AnyContent] =>
      val transferAgreementService = new TransferAgreementService(graphqlConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        transferAgreementExists <- transferAgreementService.transferAgreementExists(consignmentId, request.token.bearerAccessToken)
        consignmentStatus <- consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken)
      } yield {
        val uploadStatus: String = consignmentStatus.flatMap(_.upload).getOrElse("")

        if (!transferAgreementExists) {
          Redirect(routes.TransferAgreementController.transferAgreement(consignmentId))
        } else if (uploadStatus == "InProgress") {
          Ok(views.html.uploadInProgress(consignmentId))
        } else if (uploadStatus == "Completed") {
          Ok(views.html.uploadHasCompleted(consignmentId))
        } else {
          f(request)
        }
      }
  }
}
