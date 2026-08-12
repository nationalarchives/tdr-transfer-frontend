package services

import cats.effect.unsafe.implicits.global
import com.google.inject.Inject
import configuration.ApplicationConfig
import io.circe.Encoder
import play.api.Logging
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class StepFunction @Inject() (val applicationConfig: ApplicationConfig)(implicit val executionContext: ExecutionContext) extends Logging {
  private val utils: StepFunctionUtils = StepFunctionUtils(sfnAsyncClient(applicationConfig.stepFunctionEndpoint))

  def triggerStepFunction[T <: Product: Encoder](stepFunctionArn: String, input: T, stepFunctionName: String, executionName: String): Future[Boolean] = {
    for {
      _ <- utils
        .startExecution(
          stateMachineArn = stepFunctionArn,
          input,
          Some(executionName)
        )
        .onError(err => {
          logger.error(s"Step function $stepFunctionName trigger failed: ${err.getMessage}")
          throw new Exception(s"Step function $stepFunctionName trigger failed: ${err.getMessage}")
        })
        .unsafeToFuture()
    } yield true
  }
}
