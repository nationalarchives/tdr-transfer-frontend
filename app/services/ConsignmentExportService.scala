package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import ApiErrorHandling._
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSResponse}
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject()(val ws: WSClient, val configuration: Configuration, graphQLConfiguration: GraphQLConfiguration)(implicit val executionContext: ExecutionContext) {

  def updateTransferInititated(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val client = graphQLConfiguration.getClient[Data, Variables]()
    sendApiRequest(client, document, token, Variables(consignmentId))
      .map(d => d.updateTransferInitiated.isDefined)
      .recover(_ => false)
  }

  def triggerExport(consignmentId: UUID, token: String): Future[Boolean] = {
    val url = s"${configuration.get[String]("export.baseUrl")}/export/$consignmentId"
    ws.url(url)
      .addHttpHeaders(("Authorization", token), ("Content-Type", "application/json"))
      .post("{}")
      .map(r => r.status == 200)
      .recover(_ => false)
  }
}
