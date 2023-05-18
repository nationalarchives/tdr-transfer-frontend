package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddMultipleFileStatuses.{addMultipleFileStatuses => amfs}
import graphql.codegen.types.AddMultipleFileStatusesInput
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addFileStatusClient = graphqlConfiguration.getClient[amfs.Data, amfs.Variables]()

  def addFileStatus(addMultipleFileStatusesInput: AddMultipleFileStatusesInput, token: BearerAccessToken): Future[List[amfs.AddMultipleFileStatuses]] = {
    val variables = amfs.Variables(addMultipleFileStatusesInput)
    sendApiRequest(addFileStatusClient, amfs.document, token, variables).map(data => data.addMultipleFileStatuses)
  }
}
