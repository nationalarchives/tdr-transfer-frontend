package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.util.ConsignmentProperty._
import graphql.codegen.AddOrUpdateConsignmenetMetadata.addOrUpdateConsignmentMetadata.{AddOrUpdateConsignmentMetadata => ResponseConsignmentMetadata}
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => aoucm}
import graphql.codegen.GetConsignmenetMetadata.{getConsignmentFilesMetadata => gcm}
import graphql.codegen.types.{AddOrUpdateConsignmentMetadataInput, ConsignmentMetadataFilter, AddOrUpdateConsignmentMetadata => InputConsignmentMetadata}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

class ConsignmentMetadataService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addOrUpdateConsignmentMetadataClient = graphqlConfiguration.getClient[aoucm.Data, aoucm.Variables]()
  private val getConsignmentMetadataClient = graphqlConfiguration.getClient[gcm.Data, gcm.Variables]()
  val tdrDataLoadHeaderMapper = ConfigUtils.loadConfiguration.propertyToOutputMapper("tdrDataLoadHeader")

  def addOrUpdateConsignmentNeutralCitationNumber(
      consignmentId: UUID,
      neutralCitationData: NeutralCitationData,
      token: BearerAccessToken
  ): Future[List[ResponseConsignmentMetadata]] = {
    val metadataList = List(
      InputConsignmentMetadata(tdrDataLoadHeaderMapper(NCN), neutralCitationData.neutralCitation.getOrElse("")),
      InputConsignmentMetadata(tdrDataLoadHeaderMapper(NO_NCN), neutralCitationData.noNeutralCitation.toString),
      InputConsignmentMetadata(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), neutralCitationData.judgmentReference.getOrElse(""))
    )
    addOrUpdateConsignmentMetadata(consignmentId, metadataList, token)
  }

  def addOrUpdateConsignmentMetadata(
      consignmentId: UUID,
      consignmentMetadata: List[InputConsignmentMetadata],
      token: BearerAccessToken
  ): Future[List[aoucm.AddOrUpdateConsignmentMetadata]] = {
    val input = AddOrUpdateConsignmentMetadataInput(consignmentId, consignmentMetadata)
    val variables = aoucm.Variables(input)
    sendApiRequest(addOrUpdateConsignmentMetadataClient, aoucm.document, token, variables).map(_.addOrUpdateConsignmentMetadata)
  }

  def getConsignmentMetadata(consignmentId: UUID, fields: List[String], token: BearerAccessToken): Future[Map[String, String]] = {
    val filter = ConsignmentMetadataFilter(fields)
    val variables = gcm.Variables(consignmentId, Some(filter))
    sendApiRequest(getConsignmentMetadataClient, gcm.document, token, variables).map { data =>
      data.getConsignment match {
        case Some(consignment) =>
          consignment.consignmentMetadata.map(m => m.propertyName -> m.value).toMap
        case None => throw new IllegalStateException(s"No consignment found for consignment $consignmentId")
      }
    }
  }
  def getNeutralCitationData(consignmentId: UUID, token: BearerAccessToken): Future[NeutralCitationData] = {
    val fields = List(tdrDataLoadHeaderMapper(NCN), tdrDataLoadHeaderMapper(NO_NCN), tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE))
    getConsignmentMetadata(consignmentId, fields, token).map { metadataMap =>
      NeutralCitationData(
        neutralCitation = metadataMap.get(tdrDataLoadHeaderMapper(NCN)).filter(_.nonEmpty),
        noNeutralCitation = metadataMap.get(tdrDataLoadHeaderMapper(NO_NCN)).contains("true"),
        judgmentReference = metadataMap.get(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE)).filter(_.nonEmpty)
      )
    }
  }
}
