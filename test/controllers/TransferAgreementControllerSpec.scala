package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.i18n.Langs
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import util.FrontEndTestHelper
import services.GetConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient
import util.{EnglishLang, FrontEndTestHelper}

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

  val langs: Langs = new EnglishLang

  "TransferAgreementController GET" should {

    "render the transfer agreement page with an authenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new TransferAgreementController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration,
        new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)

      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val consignmentResponse: gc.GetConsignment = new gc.GetConsignment(UUID.randomUUID(), UUID.randomUUID())
      val data: client.GraphqlData = client.GraphqlData(Some(gc.Data(Some(consignmentResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))


      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)


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
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new TransferAgreementController(getUnauthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "return a not found error page if the transfer agreement is not assigned to an existing consignment" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gc.Data(None)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val consignmentId = UUID.randomUUID()
      val controller = new TransferAgreementController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementPage) mustBe NOT_FOUND
      contentAsString(transferAgreementPage) must include ("404")
    }

    "create a transfer agreement when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ata.Data, ata.Variables]()
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val transferAgreementId = Some(UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68"))
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

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)
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
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
    }

    "renders an error when a valid form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val client = new GraphQLConfiguration(app.configuration).getClient[ata.Data, ata.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)
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
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new TransferAgreementController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, new GetConsignmentService(new GraphQLConfiguration(app.configuration)), langs)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, "/consignment/" + consignmentId.toString + "/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      contentAsString(transferAgreementSubmit) must include("govuk-error-message")
      contentAsString(transferAgreementSubmit) must include("error")
    }
  }
}
