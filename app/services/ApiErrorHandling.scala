package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import errors.AuthorisationException
import sangria.ast.Document
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError

import scala.concurrent.{ExecutionContext, Future}

object ApiErrorHandling {
  def sendApiRequest[Data, Variables](
                                       graphQlClient: GraphQLClient[Data, Variables],
                                       document: Document,
                                       token: BearerAccessToken,
                                       variables: Variables
                                     )(implicit executionContext: ExecutionContext): Future[Data] = {

    graphQlClient.getResult(token, document, Some(variables)).map(result => {
      result.errors match {
        case Nil => result.data.get
        case List(authError: NotAuthorisedError) => throw new AuthorisationException(authError.message)
        // TODO: Create generic exception
        case errors => throw new RuntimeException(errors.map(e => e.message).mkString)
      }
    })
  }
}
