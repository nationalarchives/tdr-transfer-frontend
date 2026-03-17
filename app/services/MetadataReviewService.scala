package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetMetadataReviewDetails.{getMetadataReviewDetails => gmrd}
import services.ApiErrorHandling._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetadataReviewService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {

  private val getMetadataReviewDetailsClient = graphqlConfiguration.getClient[gmrd.Data, gmrd.Variables]()

  def getMetadataReviewDetails(consignmentId: UUID, token: BearerAccessToken): Future[List[gmrd.GetMetadataReviewDetails]] = {
    val variables = gmrd.Variables(consignmentId)
    sendApiRequest(getMetadataReviewDetailsClient, gmrd.document, token, variables)
      .map(data => data.getMetadataReviewDetails)
  }
}

