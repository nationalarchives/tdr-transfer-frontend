package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetSeries.{getSeries => gs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class TransferAgreementControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "TransferAgreementController GET" should {

    "render the transfer agreement page with an authenticated user" in {

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val transferAgreementPage = controller.transferAgreement(123)
        .apply(FakeRequest(GET, "/consignment/123/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      contentAsString(transferAgreementPage) must include("transferAgreement.header")
      contentAsString(transferAgreementPage) must include("transferAgreement.publicRecord")
      contentAsString(transferAgreementPage) must include("transferAgreement.crownCopyright")
      contentAsString(transferAgreementPage) must include("transferAgreement.english")
      contentAsString(transferAgreementPage) must include("transferAgreement.digital")
      contentAsString(transferAgreementPage) must include("transferAgreement.droAppraisalSelection")
      contentAsString(transferAgreementPage) must include("transferAgreement.droSensitivity")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new TransferAgreementController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val transferAgreementPage = controller.transferAgreement(123).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))
      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "create a transfer agreement when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ata.Data, ata.Variables]()
      val consignmentId = 1L
      val transferAgreementId = Some(1L)
      val addTransferAgreementResponse: ata.AddTransferAgreement = new ata.AddTransferAgreement(
        consignmentId,
        Some(true),
        Some(true),
        Some(true),
        Some(true),
        Some(true),
        Some(true),
        transferAgreementId
      )

      val data: client.GraphqlData = client.GraphqlData(Some(ata.Data(addTransferAgreementResponse)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(
          ("publicRecord", true.toString),
          ("crownCopyright", true.toString),
          ("english", true.toString),
          ("digital", true.toString),
          ("droAppraisalSelection", true.toString),
          ("droSensitivity", true.toString)
        ).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/1/upload"))
    }

    "renders an error when a valid form is submitted but there is an error from the api" in {
      val consignmentId = 1L
      val client = new GraphQLConfiguration(app.configuration).getClient[ata.Data, ata.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, "/consignment/" + consignmentId.toString + "/transfer-agreement").withFormUrlEncodedBody(
          ("publicRecord", true.toString),
          ("crownCopyright", true.toString),
          ("english", true.toString),
          ("digital", true.toString),
          ("droAppraisalSelection", true.toString),
          ("droSensitivity", true.toString)).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe INTERNAL_SERVER_ERROR
    }

    "display errors when an invalid form is submitted" in {
      val consignmentId = 1L
      val controller = new TransferAgreementController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, "/consignment/" + consignmentId.toString + "/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      contentAsString(transferAgreementSubmit) must include("govuk-error-message")
      contentAsString(transferAgreementSubmit) must include("error")
    }
  }
}
