package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetCustomMetadata.customMetadata.{CustomMetadata, Variables}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.DeleteFileMetadata.deleteFileMetadata.DeleteFileMetadata
import graphql.codegen.DeleteFileMetadata.{deleteFileMetadata => dfm}
import graphql.codegen.types.{DeleteFileMetadataInput, UpdateBulkFileMetadataInput, UpdateFileMetadataInput}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CustomMetadataService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val customMetadataStatusClient: GraphQLClient[cm.Data, Variables] = graphqlConfiguration.getClient[cm.Data, cm.Variables]()
  private val updateBulkMetadataClient: GraphQLClient[abfm.Data, abfm.Variables] = graphqlConfiguration.getClient[abfm.Data, abfm.Variables]()
  private val deleteFileMetadataClient: GraphQLClient[dfm.Data, dfm.Variables] = graphqlConfiguration.getClient[dfm.Data, dfm.Variables]()

  def getCustomMetadata(consignmentId: UUID, token: BearerAccessToken): Future[List[CustomMetadata]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(customMetadataStatusClient, cm.document, token, variables).map(data => data.customMetadata)
  }

  def updateMetadataHandler(consignmentId: UUID, fileIds: List[UUID], token: BearerAccessToken, metadataInput: List[UpdateFileMetadataInput]): Future[(abfm.Data, dfm.Data)] = {
    val nonEmptyEmptyInputs = metadataInput.partition(_.value.nonEmpty)
    val nonEmptyInputs = nonEmptyEmptyInputs._1
    val emptyProperties = nonEmptyEmptyInputs._2.map(_.filePropertyName).toSet

    for {
      deletedData <-
        if (emptyProperties.nonEmpty) { deleteMetadata(fileIds, token, emptyProperties) }
        else { Future(dfm.Data(dfm.DeleteFileMetadata(List(), List()))) } // ignore Intellij scala type mismatch error
      savedData <-
        if (nonEmptyInputs.nonEmpty) {
          saveMetadata(consignmentId, fileIds, token, nonEmptyInputs)
        } else { Future(abfm.Data(abfm.UpdateBulkFileMetadata(Nil, Nil))) }
    } yield {
      (savedData, deletedData)
    }
  }

  private def saveMetadata(consignmentId: UUID, fileIds: List[UUID], token: BearerAccessToken, metadataInput: List[UpdateFileMetadataInput]): Future[abfm.Data] = {
    val input = UpdateBulkFileMetadataInput(consignmentId, fileIds, metadataInput)
    val variables = abfm.Variables(input)
    sendApiRequest(updateBulkMetadataClient, abfm.document, token, variables)
  }

  def deleteMetadata(fileIds: List[UUID], token: BearerAccessToken, propertyNames: Set[String]): Future[dfm.Data] = {
    val input = DeleteFileMetadataInput(fileIds, Some(propertyNames.toList))
    val variables = dfm.Variables(input)
    sendApiRequest(deleteFileMetadataClient, dfm.document, token, variables)
  }
}
