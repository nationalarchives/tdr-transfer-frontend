package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.util.ConsignmentProperty._
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => aoucm}
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment.ConsignmentMetadata
import graphql.codegen.GetConsignmentMetadata.{getConsignmentMetadata => gcm}
import graphql.codegen.types.{AddOrUpdateConsignmentMetadata, AddOrUpdateConsignmentMetadataInput, ConsignmentMetadataFilter}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import sttp.client3.HttpError
import sttp.model.StatusCode
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
      val metadata = List(
        AddOrUpdateConsignmentMetadata(tdrDataLoadHeaderMapper(NCN), "NCN123"),
        AddOrUpdateConsignmentMetadata(tdrDataLoadHeaderMapper(NO_NCN), "false"),
        AddOrUpdateConsignmentMetadata(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), "")
      )
      val expectedVariables = Some(aoucm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, metadata)))
      val gqlResponse = buildGraphQlSuccessResponse(metadata.map(cm => (cm.propertyName, cm.value)))

      when(addOrUpdateClient.getResult(bearerAccessToken, aoucm.document, expectedVariables)).thenReturn(Future.successful(gqlResponse))

      val result = service.addOrUpdateConsignmentMetadata(consignmentId, metadata, bearerAccessToken).futureValue
      result.map(r => (r.propertyName, r.value)) should contain theSameElementsInOrderAs metadata.map(m => (m.propertyName, m.value))
      verify(addOrUpdateClient).getResult(bearerAccessToken, aoucm.document, expectedVariables)
    }

    "propagate HTTP errors" in {
      val metadata = Nil
      val expectedVariables = Some(aoucm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, metadata)))
      when(addOrUpdateClient.getResult(bearerAccessToken, aoucm.document, expectedVariables))
        .thenReturn(Future.failed(HttpError("boom", StatusCode.InternalServerError)))

      val failed = service.addOrUpdateConsignmentMetadata(consignmentId, metadata, bearerAccessToken).failed.futureValue
      failed shouldBe a[HttpError[_]]
    }
  }

  "addOrUpdateConsignmentNeutralCitationNumber" should {

    "map NeutralCitationData with noNeutralCitation = true and judgmentReference provided" in {
      val data = NeutralCitationData(None, noNeutralCitation = true, judgmentReference = Some("JR-2"))
      val expectedMetadataValues = List(
        tdrDataLoadHeaderMapper(NCN) -> "",
        tdrDataLoadHeaderMapper(NO_NCN) -> "true",
        tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE) -> "JR-2"
      )
      val responseVariablesMetadata = expectedMetadataValues.map { case (name, value) => AddOrUpdateConsignmentMetadata(name, value) }
      val expectedVariables = Some(aoucm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, responseVariablesMetadata)))
      val gqlResponse = buildGraphQlSuccessResponse(expectedMetadataValues)
      when(addOrUpdateClient.getResult(bearerAccessToken, aoucm.document, expectedVariables)).thenReturn(Future.successful(gqlResponse))

      val result = service.addOrUpdateConsignmentNeutralCitationNumber(consignmentId, data, bearerAccessToken).futureValue
      result.map(r => (r.propertyName, r.value)) should contain theSameElementsInOrderAs expectedMetadataValues
    }

    "map NeutralCitationData with noNeutralCitation = true and no judgmentReference" in {
      val data = NeutralCitationData(Some("Unused"), noNeutralCitation = true, judgmentReference = None)
      val expectedMetadataValues = List(
        tdrDataLoadHeaderMapper(NCN) -> "Unused",
        tdrDataLoadHeaderMapper(NO_NCN) -> "true",
        tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE) -> ""
      )
      val responseVariablesMetadata = expectedMetadataValues.map { case (name, value) => AddOrUpdateConsignmentMetadata(name, value) }
      val expectedVariables = Some(aoucm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, responseVariablesMetadata)))
      val gqlResponse = buildGraphQlSuccessResponse(expectedMetadataValues)
      when(addOrUpdateClient.getResult(bearerAccessToken, aoucm.document, expectedVariables)).thenReturn(Future.successful(gqlResponse))

      val result = service.addOrUpdateConsignmentNeutralCitationNumber(consignmentId, data, bearerAccessToken).futureValue
      result.map(r => (r.propertyName, r.value)) should contain theSameElementsInOrderAs expectedMetadataValues
    }
  }

  "getNeutralCitationData" should {
    "map full metadata set to NeutralCitationData with values" in {
      val consignmentIdLocal = UUID.randomUUID()
      val token = bearerAccessToken
      val metadataValues = List[ConsignmentMetadata](
        ConsignmentMetadata(tdrDataLoadHeaderMapper(NCN), "NCN2025 EWCOP 123"),
        ConsignmentMetadata(tdrDataLoadHeaderMapper(NO_NCN), "true"),
        ConsignmentMetadata(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), "Ref-ABC")
      )
      val fields = List(tdrDataLoadHeaderMapper(NCN), tdrDataLoadHeaderMapper(NO_NCN), tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE))
      val filter = ConsignmentMetadataFilter(fields)
      val variables = Some(gcm.Variables(consignmentIdLocal, Some(filter)))
      val data = GraphQlResponse(
        Some(
          gcm.Data(
            Some(
              gcm.GetConsignment(
                consignmentIdLocal.toString,
                metadataValues
              )
            )
          )
        ),
        Nil
      )
      when(getConsignmentMetadataClient.getResult(token, gcm.document, variables)).thenReturn(Future.successful(data))

      val result = service.getNeutralCitationData(consignmentIdLocal, token).futureValue
      result.neutralCitation shouldBe Some("NCN2025 EWCOP 123")
      result.noNeutralCitation shouldBe true
      result.judgmentReference shouldBe Some("Ref-ABC")
    }

    "handle empty and false values producing Nones and false boolean" in {
      val consignmentIdLocal = UUID.randomUUID()
      val token = bearerAccessToken
      val metadataValues = List[ConsignmentMetadata](
        ConsignmentMetadata(tdrDataLoadHeaderMapper(NCN), ""),
        ConsignmentMetadata(tdrDataLoadHeaderMapper(NO_NCN), "false"),
        ConsignmentMetadata(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), "")
      )
      val fields = List(tdrDataLoadHeaderMapper(NCN), tdrDataLoadHeaderMapper(NO_NCN), tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE))
      val filter = ConsignmentMetadataFilter(fields)
      val variables = Some(gcm.Variables(consignmentIdLocal, Some(filter)))
      val data = GraphQlResponse(
        Some(
          gcm.Data(
            Some(
              gcm.GetConsignment(
                consignmentIdLocal.toString,
                metadataValues
              )
            )
          )
        ),
        Nil
      )
      when(getConsignmentMetadataClient.getResult(token, gcm.document, variables)).thenReturn(Future.successful(data))

      val result = service.getNeutralCitationData(consignmentIdLocal, token).futureValue
      result.neutralCitation shouldBe None
      result.noNeutralCitation shouldBe false
      result.judgmentReference shouldBe None
    }
  }
}
