package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject() (val ws: WSClient, val configuration: Configuration, dynamoService: DynamoService, s3Service: S3Service)(implicit
    val executionContext: ExecutionContext
) extends Logging {

  def updateTransferInitiated(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    dynamoService.setFieldToValue(consignmentId, "status_TransferInitiated", Instant.now).map(res => res.sdkHttpResponse().isSuccessful)
  }

  def triggerExport(consignmentId: UUID, token: Token): Future[Boolean] = {
    s3Service.exportConsignment(token.userId, consignmentId).map(res => res.forall(_.sdkHttpResponse().isSuccessful))
  }
}
