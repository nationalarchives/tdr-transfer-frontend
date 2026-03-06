package services

import com.google.inject.Inject
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}
import services.StepFunction.StepFunctionInput

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class BackendChecksService @Inject() (val configuration: Configuration, val stepFunction: StepFunction)(implicit val executionContext: ExecutionContext) extends Logging {


  implicit val backendChecksStepFunctionInputEncoder: Encoder[BackendChecksStepFunctionInput] = deriveEncoder[BackendChecksStepFunctionInput]

  def triggerBackendChecks(consignmentId: UUID): Future[Boolean] = {
    val stepFunctionArn = s"${configuration.get[String]("backendchecks.stepFunctionArn")}"
    val stepFunctionName = "Backend Checks"
    val input = BackendChecksStepFunctionInput(consignmentId.toString)
    stepFunction.triggerStepFunction(stepFunctionArn, input, stepFunctionName, consignmentId)
  }
}

case class BackendChecksStepFunctionInput(consignmentId: String) extends StepFunctionInput
