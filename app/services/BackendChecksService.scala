package services

import com.google.inject.Inject
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}
import services.StepFunction.StepFunctionInput
import uk.gov.nationalarchives.tdr.common.utils.serviceinputs.Inputs.BackendChecksInput

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class BackendChecksService @Inject() (val configuration: Configuration, val stepFunction: StepFunction)(implicit val executionContext: ExecutionContext) extends Logging {

  implicit val backendChecksStepFunctionInputEncoder: Encoder[BackendChecksInput] = deriveEncoder[BackendChecksInput]

  def triggerBackendChecks(consignmentId: UUID): Future[Boolean] = {
    val stepFunctionArn = s"${configuration.get[String]("backendchecks.stepFunctionArn")}"
    val stepFunctionName = "Backend Checks"
    val input = BackendChecksInput(consignmentId.toString, consignmentId.toString)
    stepFunction.triggerStepFunction(stepFunctionArn, input, stepFunctionName, consignmentId)
  }
}
