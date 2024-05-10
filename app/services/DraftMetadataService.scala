package services

import com.google.inject.Inject
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DraftMetadataService @Inject() (val wsClient: WSClient, val configuration: Configuration)(implicit val executionContext: ExecutionContext) extends Logging {

  def triggerDraftMetadataValidator(consignmentId: UUID, uploadFileName: String, token: String): Future[Boolean] = {
    val url = s"${configuration.get[String]("metadatavalidation.baseUrl")}/draft-metadata/validate/$consignmentId?fileName=$uploadFileName"
    wsClient
      .url(url)
      .addHttpHeaders(("Authorization", token), ("Content-Type", "application/json"))
      .post("{}")
      .flatMap(r =>
        r.status match {
          case 200 => Future(true)
          case _ =>
            logger.error(s"Draft metadata validation api response ${r.status} ${r.body}")
            Future.failed(new Exception(s"Call to draft metadata validator failed API has returned a non 200 response for consignment $consignmentId"))
        }
      )
  }
}
