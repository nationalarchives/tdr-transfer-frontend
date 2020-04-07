package validation

import java.util.UUID

import auth.TokenSecurity
import configuration.GraphQLConfiguration
import controllers.routes
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.GetConsignmentService

import scala.concurrent.ExecutionContext

abstract class ValidatedActions() extends TokenSecurity with I18nSupport {
  implicit val ec: ExecutionContext
  val graphqlConfiguration: GraphQLConfiguration

  private val isTransferAgreementCompleteClient = graphqlConfiguration.getClient[itac.Data, itac.Variables]()

  def consignmentExists(consignmentId: UUID)(f: Request[AnyContent] => Result): Action[AnyContent] = secureAction.async {
    implicit request: Request[AnyContent] =>
    val getConsignmentService = new GetConsignmentService(graphqlConfiguration)
    val consignmentExists = getConsignmentService.consignmentExists(consignmentId, request.token.bearerAccessToken)
    consignmentExists.map {
      case true => f(request)
      case false => NotFound(views.html.notFoundError("The consignment you are trying to access does not exist"))
    }
  }

  def transferAgreementExistsAction(consignmentId: UUID)(f: Request[AnyContent] => Result): Action[AnyContent]
  = secureAction.async { implicit request: Request[AnyContent] =>
    val variables = itac.Variables(consignmentId)
    isTransferAgreementCompleteClient.getResult(request.token.bearerAccessToken, itac.document, Some(variables)).map(data => {
      val isComplete: Option[Boolean] = for {
        dataDefined <- data.data
        transferAgreement <- dataDefined.getTransferAgreement
      } yield transferAgreement.isAgreementComplete

      if (isComplete.isDefined && isComplete.get) {
        f(request)
      } else {
        Redirect(routes.TransferAgreementController.transferAgreement(consignmentId))
      }
    })
  }
}
