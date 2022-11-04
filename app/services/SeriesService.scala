package services

import configuration.GraphQLConfiguration
import graphql.codegen.GetSeries.getSeries
import javax.inject.Inject
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.{ExecutionContext, Future}

class SeriesService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit ec: ExecutionContext) {

  private val getSeriesClient = graphqlConfiguration.getClient[getSeries.Data, getSeries.Variables]()

  def getSeriesForUser(token: Token): Future[List[getSeries.GetSeries]] = {
    val userTransferringBody: String = token.transferringBody
      .getOrElse(throw new RuntimeException(s"Transferring body missing from token for user ${token.userId}"))
    val variables: getSeries.Variables = new getSeries.Variables(userTransferringBody)

    sendApiRequest(getSeriesClient, getSeries.document, token.bearerAccessToken, variables).map(_.getSeries)
  }
}
