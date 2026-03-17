package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetMetadataReviewDetails.{getMetadataReviewDetails => gmrd}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import sttp.client3.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class MetadataReviewServiceSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val getMetadataReviewDetailsClient = mock[GraphQLClient[gmrd.Data, gmrd.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")

  when(graphQLConfig.getClient[gmrd.Data, gmrd.Variables]()).thenReturn(getMetadataReviewDetailsClient)

  private val metadataReviewService = new MetadataReviewService(graphQLConfig)

  private val someDateTime = ZonedDateTime.of(LocalDateTime.of(2024, 6, 1, 10, 0), ZoneId.systemDefault())

  private val reviewDetail1 = gmrd.GetMetadataReviewDetails(
    metadataReviewLogId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
    consignmentId = consignmentId,
    userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
    action = "Submission",
    eventTime = someDateTime
  )

  private val reviewDetail2 = gmrd.GetMetadataReviewDetails(
    metadataReviewLogId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
    consignmentId = consignmentId,
    userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
    action = "Approval",
    eventTime = someDateTime.plusHours(1)
  )

  override def afterEach(): Unit = {
    Mockito.reset(getMetadataReviewDetailsClient)
  }

  "getMetadataReviewDetails" should {
    "return a list of metadata review details for a given consignment id" in {
      val reviewDetails = List(reviewDetail1, reviewDetail2)
      val response = GraphQlResponse(Some(gmrd.Data(reviewDetails)), Nil)

      when(getMetadataReviewDetailsClient.getResult(token, gmrd.document, Some(gmrd.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val result = metadataReviewService.getMetadataReviewDetails(consignmentId, token).futureValue

      result.size should be(2)

      val firstDetail = result.find(_.action == "Submission").get
      firstDetail.metadataReviewLogId should equal(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
      firstDetail.consignmentId should equal(consignmentId)
      firstDetail.userId should equal(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
      firstDetail.eventTime should equal(someDateTime)

      val secondDetail = result.find(_.action == "Approval").get
      secondDetail.metadataReviewLogId should equal(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))
      secondDetail.consignmentId should equal(consignmentId)
      secondDetail.userId should equal(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
      secondDetail.eventTime should equal(someDateTime.plusHours(1))
    }

    "return an empty list when there are no metadata review details for the given consignment" in {
      val response = GraphQlResponse(Some(gmrd.Data(List.empty)), Nil)

      when(getMetadataReviewDetailsClient.getResult(token, gmrd.document, Some(gmrd.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val result = metadataReviewService.getMetadataReviewDetails(consignmentId, token).futureValue

      result should be(empty)
    }

    "return an error when the API has an error" in {
      when(getMetadataReviewDetailsClient.getResult(token, gmrd.document, Some(gmrd.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val result = metadataReviewService.getMetadataReviewDetails(consignmentId, token)

      result.failed.futureValue shouldBe a[HttpError[_]]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[gmrd.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))

      when(getMetadataReviewDetailsClient.getResult(token, gmrd.document, Some(gmrd.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val result = metadataReviewService.getMetadataReviewDetails(consignmentId, token)

      result.failed.futureValue shouldBe a[AuthorisationException]
    }
  }
}

