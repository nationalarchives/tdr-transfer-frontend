package services

import java.util.UUID

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportService @Inject()(val ws: WSClient, val configuration: Configuration)(implicit val executionContext: ExecutionContext) {

  def triggerExport(consignmentId: UUID, token: String): Future[Boolean] =
    ws.url(s"${configuration.get[String]("export.baseUrl")}/export/$consignmentId")
      .addHttpHeaders(("Authorization", token), ("Content-Type", "application/json"))
      .post("{}")
      .map(r => r.status == 200)
      .recover(_ => false)
}
