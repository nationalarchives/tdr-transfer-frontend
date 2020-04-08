package validation

import java.util.UUID

import auth.TokenSecurity
import configuration.GraphQLConfiguration
import controllers.routes
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, TransferAgreementService}

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
      case false => NotFound(views.html.notFoundError("The consignment you are trying to access does not exist"))
    }
  }

  def transferAgreementExistsAction(consignmentId: UUID)(f: Request[AnyContent] => Result): Action[AnyContent]
  = secureAction.async { implicit request: Request[AnyContent] =>
    val transferAgreementService = new TransferAgreementService(graphqlConfiguration)

    transferAgreementService.transferAgreementExists(consignmentId, request.token.bearerAccessToken) map {
      case true => f(request)
      case false => Redirect(routes.TransferAgreementController.transferAgreement(consignmentId))
    }
  }
}
