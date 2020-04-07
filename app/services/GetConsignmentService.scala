package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignment
import graphql.codegen.GetConsignment.getConsignment
import javax.inject.{Inject, Singleton}
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GetConsignmentService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                     (implicit val ec: ExecutionContext)  {

  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()

  def consignmentExists(consignmentId: UUID,
                        token: BearerAccessToken): Future[Boolean] = {
    val variables: getConsignment.Variables = new GetConsignment.getConsignment.Variables(consignmentId)
    getConsignmentClient.getResult(token, getConsignment.document, Some(variables)).map(data => {
      data.errors match {
        case Nil => data.data.isDefined && data.data.get.getConsignment.isDefined
        case List(authError: NotAuthorisedError) => throw new AuthorisationException(authError.message)
        case errors => throw new RuntimeException(errors.map(e => e.message).mkString)
      }
    })
  }
}
