package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.MetadataController
import controllers.MetadataController.MetadataForm
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment.Files.AllMetadata
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata._
import graphql.codegen.AddFileMetadata.addFileMetadata
import graphql.codegen.types.AddFileMetadataInput

import java.util.UUID

class FileMetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {

  def submitMetadata(fileId: UUID, newForm: List[MetadataForm], token: BearerAccessToken): Future[List[addFileMetadata.Data]] = {
    val addMetadataClient = graphqlConfiguration.getClient[addFileMetadata.Data, addFileMetadata.Variables]()
    Future.sequence(
      newForm.map(formValue => {
        sendApiRequest(addMetadataClient, addFileMetadata.document, token, addFileMetadata.Variables(AddFileMetadataInput(formValue.name, fileId, formValue.value.format)))
      })
    )

  }

  private val metadataClient = graphqlConfiguration.getClient[Data, Variables]()

  def getConsignmentMetadata(consignmentId: UUID, fileId: UUID, token: BearerAccessToken, fieldType: String): Future[List[AllMetadata]] = {
    sendApiRequest(metadataClient, document, token, Variables(consignmentId, Some(fileId))).map(gc => gc.getConsignment match {
      case Some(value) => value.files
        .flatMap(_.allMetadata
        .filter(p =>
          p.propertyGroup.getOrElse("").toLowerCase == fieldType)
        )
      case None => Nil
    })
  }
}
