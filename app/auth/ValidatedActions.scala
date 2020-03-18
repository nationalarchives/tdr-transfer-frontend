package auth

import configuration.GraphQLConfiguration
import controllers.routes
import graphql.codegen.GetTransferAgreement.{getTransferAgreement => gta}
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.ExecutionContext

abstract class ValidatedActions() extends TokenSecurity {
  implicit val ec: ExecutionContext
  val graphqlConfiguration: GraphQLConfiguration

  private val getTransferAgreementClient = graphqlConfiguration.getClient[gta.Data, gta.Variables]()

  def transferAgreementExistsAction(consignmentId: Long)(f: Request[AnyContent] => Result): Action[AnyContent]
  = secureAction.async { implicit request: Request[AnyContent] =>
    val variables = gta.Variables(consignmentId)
    getTransferAgreementClient.getResult(request.token.bearerAccessToken, gta.document, Some(variables)).map(data => {
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
