package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import play.api.{Configuration, Logging}
import services.ApiErrorHandling._
import services.StepFunction.StepFunctionInput
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject() (val stepFunction: StepFunction, val configuration: Configuration, graphQLConfiguration: GraphQLConfiguration)(implicit
    val executionContext: ExecutionContext
) extends Logging {

  implicit val exportStepFunctionInputEncoder: Encoder[ExportStepFunctionInput] = deriveEncoder[ExportStepFunctionInput]

  def updateTransferInitiated(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val client = graphQLConfiguration.getClient[Data, Variables]()
    sendApiRequest(client, document, token, Variables(consignmentId))
      .map(d => d.updateTransferInitiated.isDefined)
  }

  def triggerExport(consignmentId: UUID, token: Token): Future[Boolean] = {
    logger.info(s"Export was triggered by ${token.userId} for consignment:$consignmentId")
    val stepFunctionArn = s"${configuration.get[String]("export.stepFunctionArn")}"
    val stepFunctionName = "Export"
    val input = ExportStepFunctionInput(consignmentId.toString)
    stepFunction.triggerStepFunction(stepFunctionArn, input, stepFunctionName, consignmentId)
  }
}

case class ExportStepFunctionInput(consignmentId: String) extends StepFunctionInput
