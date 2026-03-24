package services

import cats.effect.unsafe.implicits.global
import com.google.inject.Inject
import io.circe.Encoder
import play.api.{Configuration, Logging}
import services.StepFunction.StepFunctionInput
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class StepFunction @Inject() (
    val configuration: Configuration
)(implicit val executionContext: ExecutionContext)
    extends Logging {
  private val stepFunctionEndpoint: String = s"${configuration.get[String]("stepFunction.endpoint")}"
  private val utils: StepFunctionUtils = StepFunctionUtils(sfnAsyncClient(stepFunctionEndpoint))

  def triggerStepFunction[T <: Product: Encoder](stepFunctionArn: String, input: T, stepFunctionName: String, executionId: UUID): Future[Boolean] = {
    for {
      _ <- utils
        .startExecution(
          stateMachineArn = stepFunctionArn,
          input,
          Some(executionId.toString)
        )
        .onError(err => {
          logger.error(s"Step function $stepFunctionName trigger failed: ${err.getMessage}")
          throw new RuntimeException(s"Step function $stepFunctionName trigger failed: ${err.getMessage}")
        })
        .unsafeToFuture()
    } yield true
  }
}

object StepFunction {
  trait StepFunctionInput {
    def consignmentId: String
  }
}
