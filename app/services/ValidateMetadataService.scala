package services

import configuration.GraphQLConfiguration
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ValidateMetadataService @Inject() (val ws: WSClient, val configuration: Configuration, graphQLConfiguration: GraphQLConfiguration)(implicit
    val executionContext: ExecutionContext
) extends Logging {

  def triggerMetadataValidation(consignmentId: UUID, token: String): Future[Boolean] = {
    val url = s"${configuration.get[String]("metadatavalidation.baseUrl")}/draft-metadata/validate/$consignmentId"
    ws.url(url)
      .addHttpHeaders(("Authorization", token), ("Content-Type", "application/json"))
      .post("{}")
      .flatMap(r =>
        r.status match {
          case 200 => Future(true)
          case _ =>
            logger.error(s"Validate draft metadata api response ${r.status} ${r.body}")
            Future.failed(new Exception(s"Call to validate draft metadata API has returned a non 200 response for consignment $consignmentId"))
        }
      )
  }
}
