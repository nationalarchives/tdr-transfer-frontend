package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import errors.{AuthorisationException, GraphQlException}
import sangria.ast.Document
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError

import java.io.FileWriter
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object ApiErrorHandling {
  def sendApiRequest[Data, Variables](
      graphQlClient: GraphQLClient[Data, Variables],
      document: Document,
      token: BearerAccessToken,
      variables: Variables
  )(implicit executionContext: ExecutionContext): Future[Data] = {

    graphQlClient
      .getResult(token, document, Some(variables))
      .map(result =>
        result.errors match {
          case Nil                                 =>
            println("===> new ok")
            result.data.get
          case List(authError: NotAuthorisedError) =>
            println("===> new auth error")
            val fw = new FileWriter("test.txt", true)
            try {
              fw.write(LocalDateTime.now() +  " ===> new auth error" + "\n")
            }
            finally fw.close()
            throw new AuthorisationException(authError.message)
          case errors                              =>
            println("===> new errors")
            val fw = new FileWriter("test.txt", true)
            try {
              fw.write(LocalDateTime.now() +  " ===> new errors" + "\n")
            }
            finally fw.close()
            throw new GraphQlException(errors)
        }
      )
  }
}
