package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails.GetConsignment
import graphql.codegen.types.AddConsignmentInput
import org.keycloak.representations.AccessToken
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentServiceSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val getConsignmentClient = mock[GraphQLClient[gc.Data, gc.Variables]]
  private val addConsignmentClient = mock[GraphQLClient[addConsignment.Data, addConsignment.Variables]]
  private val getConsignmentFolderInfoClient = mock[GraphQLClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]]
  when(graphQlConfig.getClient[gc.Data, gc.Variables]()).thenReturn(getConsignmentClient)
  when(graphQlConfig.getClient[addConsignment.Data, addConsignment.Variables]()).thenReturn(addConsignmentClient)
  when(graphQlConfig.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()).thenReturn(getConsignmentFolderInfoClient)

  private val consignmentService = new ConsignmentService(graphQlConfig)

  private val accessToken = new AccessToken()
  private val bearerAccessToken = new BearerAccessToken("some-token")
  private val token = new Token(accessToken, bearerAccessToken)
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")
  private val seriesId = Some(UUID.fromString("d54a5118-33a0-4ba2-8030-d16efcf1d1f4"))

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentClient)
    Mockito.reset(addConsignmentClient)
  }

  "consignmentExists" should {
    "return true when given a valid consignment id" in {
      val response = GraphQlResponse(Some(gc.Data(Some(gc.GetConsignment(consignmentId, seriesId)))), Nil)
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val actualResults = getConsignment.futureValue

      actualResults should be(true)
    }

    "return false if consignment with given id does not exist" in {
      val response = GraphQlResponse(Some(gc.Data(None)), Nil)
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val actualResults = getConsignment.futureValue

      actualResults should be(false)
    }

    "return an error when the API has an error" in {
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[gc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val results = getConsignment.failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "createConsignment" should {
    "create a consignment of type 'standard' with the given series when no user type provided" in {
      val noUserTypeToken = mock[Token]
      when(noUserTypeToken.bearerAccessToken).thenReturn(bearerAccessToken)

      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(seriesId, Some("standard"))))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(Option(seriesId), token)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "create a consignment of type 'standard' with the given series when standard user type provided" in {
      val standardUserToken: Token = mock[Token]
      when(standardUserToken.isStandardUser).thenReturn(true)
      when(standardUserToken.bearerAccessToken).thenReturn(bearerAccessToken)

      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(seriesId, Some("standard"))))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(seriesId, standardUserToken)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "create a consignment of type 'judgment' when judgment user type provided" in {
      val judgmentUserToken: Token = mock[Token]
      when(judgmentUserToken.isJudgmentUser).thenReturn(true)
      when(judgmentUserToken.bearerAccessToken).thenReturn(bearerAccessToken)
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), None))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(None, Some("judgment"))))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(None, judgmentUserToken)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "return the created consignment" in {
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, Some("standard"))))))
        .thenReturn(Future.successful(response))

      val result = consignmentService.createConsignment(seriesIdOption, token).futureValue

      result.consignmentid should contain(consignmentId)
    }

    "return an error when the API has an error" in {
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, Some("standard"))))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.createConsignment(Option(seriesId), token)

      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[addConsignment.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, Some("standard"))))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.createConsignment(Option(seriesId), token).failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "getConsignmentFolderInfo" should {
    "return information about a consignment when given a consignment ID" in {
      val response = GraphQlResponse[getConsignmentFolderDetails
      .Data](Some(getConsignmentFolderDetails
        .Data(Some(getConsignmentFolderDetails
          .GetConsignment(3, Some("Test Parent Folder"))))), Nil)

      when(getConsignmentFolderInfoClient.getResult(
        bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

      getConsignmentDetails should be(GetConsignment(3, Some("Test Parent Folder")))
    }
  }

  "return an error if the API returns an error" in {
    when(getConsignmentFolderInfoClient.getResult(
      bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).failed.futureValue

    getConsignmentDetails shouldBe a[HttpError]
  }

  "return an empty object if there are no consignment details" in {
    val response = GraphQlResponse[getConsignmentFolderDetails
    .Data](Some(getConsignmentFolderDetails
      .Data(Some(getConsignmentFolderDetails
        .GetConsignment(0, None)))), Nil)

    when(getConsignmentFolderInfoClient.getResult(
      bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

    getConsignmentDetails should be(GetConsignment(0, None))
  }
}
