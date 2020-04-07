package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment
import graphql.codegen.GetConsignment.getConsignment
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GetConsignmentService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                     (implicit val ec: ExecutionContext)  {

  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()

  def consignmentExists(consignmentId: UUID,
                        token: BearerAccessToken): Future[Boolean] = {
    val variables: getConsignment.Variables = new GetConsignment.getConsignment.Variables(consignmentId)
    getConsignmentClient.getResult(token, getConsignment.document, Some(variables)).map(data => {
      data.data.isDefined && data.data.get.getConsignment.isDefined
    })
  }
}
