package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.util.ConsignmentProperty._
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => aoucm}
import graphql.codegen.GetConsignmentMetadata.{getConsignmentMetadata => gcm}
import graphql.codegen.types.{AddOrUpdateConsignmentMetadata, AddOrUpdateConsignmentMetadataInput}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentMetadataServiceSpec extends AnyWordSpec with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val addOrUpdateClient = mock[GraphQLClient[aoucm.Data, aoucm.Variables]]
  private val getConsignmentMetadataClient = mock[GraphQLClient[gcm.Data, gcm.Variables]]
  when(graphQlConfig.getClient[aoucm.Data, aoucm.Variables]()).thenReturn(addOrUpdateClient)
  when(graphQlConfig.getClient[gcm.Data, gcm.Variables]()).thenReturn(getConsignmentMetadataClient)

  private val service = new ConsignmentMetadataService(graphQlConfig)
  private val bearerAccessToken = new BearerAccessToken("test-token")
  private val consignmentId: UUID = UUID.randomUUID()

  private def buildGraphQlSuccessResponse(returnValues: List[(String, String)]) = {
    val resultObjects = returnValues.map { case (name, value) => aoucm.AddOrUpdateConsignmentMetadata(consignmentId, name, value) }
    GraphQlResponse(Some(aoucm.Data(resultObjects)), Nil)
  }

  "addOrUpdateConsignmentMetadata" should {
    "send the correct variables to the GraphQL API and return the response list" in {
      val consignmentMetadata = Map(
        NCN -> "NCN123",
        NO_NCN -> "false",
        JUDGMENT_REFERENCE -> ""
      )
      val metadata = consignmentMetadata.map(cm => AddOrUpdateConsignmentMetadata(tdrDataLoadHeaderMapper(cm._1), cm._2)).toList
      val expectedVariables = Some(aoucm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, metadata)))
      val gqlResponse = buildGraphQlSuccessResponse(metadata.map(cm => (cm.propertyName, cm.value)))

      when(addOrUpdateClient.getResult(bearerAccessToken, aoucm.document, expectedVariables)).thenReturn(Future.successful(gqlResponse))
      val result = service.addOrUpdateConsignmentMetadata(consignmentId, consignmentMetadata, bearerAccessToken).futureValue
      result.map(r => (r.propertyName, r.value)) should contain theSameElementsInOrderAs metadata.map(m => (m.propertyName, m.value))
      verify(addOrUpdateClient).getResult(bearerAccessToken, aoucm.document, expectedVariables)
    }
  }
}
