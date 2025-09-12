package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, contentType, redirectLocation, status => playStatus, _}
import services.ConsignmentService
import services.ConsignmentMetadataService
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import controllers.util.ConsignmentProperty.NeutralCitationData
import graphql.codegen.AddOrUpdateConsignmenetMetadata.addOrUpdateConsignmentMetadata.AddOrUpdateConsignmentMetadata
import testUtils.FrontEndTestHelper
import scala.concurrent.Future

import java.util.UUID
import scala.concurrent.ExecutionContext

class JudgmentNeutralCitationControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  private def instantiateController() = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentMetadataService = mock[ConsignmentMetadataService]
    when(consignmentMetadataService.addOrUpdateConsignmentNeutralCitationNumber(any[UUID], any[NeutralCitationData], any[BearerAccessToken]))
      .thenReturn(Future.successful(List.empty[AddOrUpdateConsignmentMetadata]))
    new JudgmentNeutralCitationController(
      getAuthorisedSecurityComponents,
      graphQLConfiguration,
      getValidJudgmentUserKeycloakConfiguration,
      consignmentService,
      consignmentMetadataService
    )
  }

  "JudgmentNeutralCitationController POST" should {
    "return BadRequest and show error when no NCN and checkbox not selected" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val result = controller
        .validateNCN(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation").withCSRFToken)

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      body must include("There is a problem")
      body must include("update-reasons-error")
      body must include("Provide the neutral citation number (NCN) for the original judgment")
    }

    "return BadRequest and show error NCN less than 10 characters" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> "123")
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      body must include("There is a problem")
      body must include("update-reasons-error")
      body must include("Neutral citation number must be between 10 and 100 characters")
      body must include("123")
    }

    "return BadRequest and show error NCN more than 100 characters" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val longNcn = "a" * 101

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> longNcn)
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      body must include("There is a problem")
      body must include("update-reasons-error")
      body must include("Neutral citation number must be between 10 and 100 characters")
    }

    "return BadRequest and show error when the user enters more than 500 characters for judgment_reference field" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val longJudgmentReference = "a" * 501

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody("judgment_reference" -> longJudgmentReference)
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      body must include("There is a problem")
      body must include("update-reasons-error")
      body must include("Enter a valid NCN, or select 'Original judgment to this update does not have an NCN'")
    }

    "redirect to upload page when NCN is provided" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val validNcn = "[2025] EWCOP 123 (T1)"
      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> validNcn)
            .withCSRFToken
        )
      playStatus(result) mustBe SEE_OTHER
      val redirect = redirectLocation(result).value
      redirect must startWith(s"/judgment/$consignmentId/upload?judgment_neutral_citation=")
    }

    "redirect to upload page when 'no-ncn' checkbox selected" in {
      val consignmentId = UUID.randomUUID()
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation")
            .withFormUrlEncodedBody("judgment_no_neutral_citation" -> "no-ncn-select", "judgment_reference" -> "An example reference")
            .withCSRFToken
        )

      playStatus(result) mustBe SEE_OTHER
      val redirect = redirectLocation(result).value
      redirect mustBe s"/judgment/$consignmentId/upload?judgment_no_neutral_citation=no-ncn-select&judgment_reference=An+example+reference"
    }
  }
}
