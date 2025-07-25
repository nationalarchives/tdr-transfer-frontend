package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}
import services.ApiErrorHandling._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject() (val ws: WSClient, val configuration: Configuration, graphQLConfiguration: GraphQLConfiguration)(implicit
    val executionContext: ExecutionContext
) extends Logging {

  def updateTransferInitiated(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val client = graphQLConfiguration.getClient[Data, Variables]()
    sendApiRequest(client, document, token, Variables(consignmentId))
      .map(d => d.updateTransferInitiated.isDefined)
  }

  def triggerExport(consignmentId: UUID, token: String): Future[Boolean] = {
    val url = s"${configuration.get[String]("export.baseUrl")}/export/$consignmentId"
    ws.url(url)
      .addHttpHeaders(("Authorization", token), ("Content-Type", "application/json"))
      .post("{}")
      .flatMap(r =>
        r.status match {
          case 200 => Future(true)
          case _ =>
            logger.error(s"Export api response ${r.status} ${r.body}")
            Future.failed(new Exception(s"Call to export API has returned a non 200 response for consignment $consignmentId"))
        }
      )
  }
}
