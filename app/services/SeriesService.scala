package services

import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetSeries.getSeries
import javax.inject.Inject
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.{ExecutionContext, Future}

class SeriesService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit ec: ExecutionContext) {

  private val getSeriesClient = graphqlConfiguration.getClient[getSeries.Data, getSeries.Variables]()

  def getSeriesForUser(token: Token): Future[List[getSeries.GetSeries]] = {
    val userTransferringBody: String = token.transferringBody
      .getOrElse(throw new RuntimeException(s"Transferring body missing from token for user ${token.userId}"))
    val variables: getSeries.Variables = new getSeries.Variables(userTransferringBody)

    getSeriesClient.getResult(token.bearerAccessToken, getSeries.document, Some(variables)).map(result => {
      result.errors match {
        case Nil => result.data.get.getSeries
        case List(authError: NotAuthorisedError) => throw new AuthorisationException(authError.message)
        // TODO: Create generic exception
        case errors => throw new RuntimeException(errors.map(e => e.message).mkString)
      }
    })
  }
}
