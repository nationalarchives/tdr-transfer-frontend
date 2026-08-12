package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration}
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import play.api.Logging
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.common.utils.serviceinputs.Inputs.ExportInput
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject() (val stepFunction: StepFunction, val applicationConfig: ApplicationConfig, graphQLConfiguration: GraphQLConfiguration)(implicit
    val executionContext: ExecutionContext
) extends Logging {

  implicit val exportStepFunctionInputEncoder: Encoder[ExportInput] = deriveEncoder[ExportInput]

  def updateTransferInitiated(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val client = graphQLConfiguration.getClient[Data, Variables]()
    sendApiRequest(client, document, token, Variables(consignmentId))
      .map(d => d.updateTransferInitiated.isDefined)
  }

  def triggerExport(consignmentId: UUID, consignmentRef: String, token: Token): Future[Boolean] = {
    logger.info(s"Export was triggered by ${token.userId} for consignment:$consignmentId")
    val stepFunctionName = "Export"
    val input = ExportInput(consignmentId.toString)
    stepFunction.triggerStepFunction(applicationConfig.exportStepFunctionArn, input, stepFunctionName, s"$consignmentRef-${System.currentTimeMillis()}")
  }
}
