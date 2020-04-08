package services

import configuration.GraphQLConfiguration

import scala.concurrent.{ExecutionContext, Future}
import graphql.codegen.GetSeries.getSeries
import javax.inject.Inject
import uk.gov.nationalarchives.tdr.keycloak.Token

class SeriesService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit ec: ExecutionContext) {

  private val getSeriesClient = graphqlConfiguration.getClient[getSeries.Data, getSeries.Variables]()

  def getSeriesForUser(token: Token): Future[List[getSeries.GetSeries]] = {
    val userTransferringBody: String = token.transferringBody
      .getOrElse(throw new RuntimeException(s"Transferring body missing from token for user ${token.userId}"))
    val variables: getSeries.Variables = new getSeries.Variables(userTransferringBody)

    getSeriesClient.getResult(token.bearerAccessToken, getSeries.document, Some(variables)).map(data => {
      // TODO: Use pattern matching
      if (data.data.isDefined) {
        data.data.get.getSeries
      } else {
        // TODO: Create generic exception
        val errors = data.errors.map(e => e.message).mkString
        throw new RuntimeException(s"Error getting series for user `${token.userId}`: $errors")
      }
    })
  }
}
