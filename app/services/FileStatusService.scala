package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddFileStatus.{addFileStatus => afs}
import graphql.codegen.types.AddFileStatusInput
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addFileStatusClient = graphqlConfiguration.getClient[afs.Data, afs.Variables]()

  def addFileStatus(addFileStatusInput: AddFileStatusInput, token: BearerAccessToken): Future[afs.AddFileStatus] = {
    val variables = afs.Variables(addFileStatusInput)
    sendApiRequest(addFileStatusClient, afs.document, token, variables).map(data => data.addFileStatus)
  }
}
