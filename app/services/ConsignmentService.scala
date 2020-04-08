package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.types.AddConsignmentInput
import graphql.codegen.{AddConsignment, GetConsignment}
import javax.inject.{Inject, Singleton}
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConsignmentService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                  (implicit val ec: ExecutionContext)  {

  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()
  private val addConsignmentClient = graphqlConfiguration.getClient[addConsignment.Data, addConsignment.Variables]()

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

  def createConsignment(seriesId: UUID, token: BearerAccessToken): Future[addConsignment.AddConsignment] = {
    val addConsignmentInput: AddConsignmentInput = AddConsignmentInput(seriesId)
    val variables: addConsignment.Variables = AddConsignment.addConsignment.Variables(addConsignmentInput)

    addConsignmentClient.getResult(token, addConsignment.document, Some(variables)).map(data => {
      data.errors match {
        case Nil => data.data.get.addConsignment
        case List(authError: NotAuthorisedError) => throw new AuthorisationException(authError.message)
        case errors => throw new RuntimeException(errors.map(e => e.message).mkString)
      }
    })
  }
}
