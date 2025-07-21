package services

import cats.effect.unsafe.implicits.global
import com.google.inject.Inject
import io.circe.generic.encoding.DerivedAsObjectEncoder.deriveEncoder
import play.api.cache.redis.CacheApi
import play.api.{Configuration, Logging}
import uk.gov.nationalarchives.aws.utils.stepfunction.{StepFunctionClients, StepFunctionUtils}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class BackendChecksService @Inject() (val cacheApi: CacheApi, val configuration: Configuration)(implicit val executionContext: ExecutionContext) extends Logging {

  private val sfnClient = StepFunctionClients.sfnAsyncClient(s"${configuration.get[String]("backendchecks.baseUrl")}")
  private val sfnUtils = StepFunctionUtils(sfnClient)

  def triggerBackendChecks(consignmentId: UUID, token: String): Future[Boolean] = {
    val executionArnCache = cacheApi.set[String](s"${consignmentId}_execArn")
    val input = Input(consignmentId)
    for {
      a <- sfnUtils.startExecution("sfnArn", input).unsafeToFuture()
    } yield {
      executionArnCache.add(a.executionArn())
      true
    }
  }

  case class Input(consignmentId: UUID)
}
