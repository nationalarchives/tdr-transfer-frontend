package services

import uk.gov.nationalarchives.tdr.keycloak.Token

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SeriesService @Inject() (val dynamoService: DynamoService)(implicit ec: ExecutionContext) {


  def getSeriesForUser(token: Token): Future[List[DynamoService.Series]] = {
    val userTransferringBody: String = token.transferringBody
      .getOrElse(throw new RuntimeException(s"Transferring body missing from token for user ${token.userId}"))
    dynamoService.getSeries(userTransferringBody)
  }
}
