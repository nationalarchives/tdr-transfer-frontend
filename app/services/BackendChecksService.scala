package services

import com.google.inject.Inject
import configuration.ApplicationConfig
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import play.api.Logging
import uk.gov.nationalarchives.tdr.common.utils.serviceinputs.Inputs.BackendChecksInput

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class BackendChecksService @Inject() (val applicationConfig: ApplicationConfig, val stepFunction: StepFunction)(implicit val executionContext: ExecutionContext) extends Logging {

  implicit val backendChecksStepFunctionInputEncoder: Encoder[BackendChecksInput] = deriveEncoder[BackendChecksInput]

  def triggerBackendChecks(consignmentId: UUID): Future[Boolean] = {
    val stepFunctionName = "Backend Checks"
    val input = BackendChecksInput(consignmentId.toString)
    stepFunction.triggerStepFunction(applicationConfig.backendChecksStepFunctionArn, input, stepFunctionName, consignmentId)
  }
}
