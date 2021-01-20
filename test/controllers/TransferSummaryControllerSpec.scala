package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{ok, okJson, post, serverError, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcs}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures._
import play.api.Configuration
import play.api.Play.materializer
import play.api.i18n.Langs
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import services.ConsignmentService
import play.api.test.WsTestClient.InternalWSClient
import services.ConsignmentExportService
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import util.{EnglishLang, FrontEndTestHelper}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala

class TransferSummaryControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val wiremockExportServer = new WireMockServer(9007)

  override def beforeEach(): Unit = {
    wiremockServer.start()
    wiremockExportServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockExportServer.resetAll()
    wiremockServer.stop()
    wiremockExportServer.stop()
  }

  val langs: Langs = new EnglishLang
  val consignmentId: UUID = UUID.randomUUID()

  def exportService(configuration: Configuration): ConsignmentExportService = {
    val wsClient = new InternalWSClient("http", 9007)
    new ConsignmentExportService(wsClient, configuration, new GraphQLConfiguration(configuration))
  }

  "TransferSummaryController GET" should {
    "render the transfer summary page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)

      val controller = new TransferSummaryController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val seriesCode = Some(gcs.GetConsignment.Series(Some("Mock Series")))
      val transferringBodyName = Some(gcs.GetConsignment.TransferringBody(Some("MockBody")))
      val totalFiles: Int = 3

      val consignmentResponse: gcs.GetConsignment = new gcs.GetConsignment(seriesCode, transferringBodyName, totalFiles)
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val transferSummaryPage = controller.transferSummary(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-summary").withCSRFToken)

      playStatus(transferSummaryPage) mustBe OK
      contentType(transferSummaryPage) mustBe Some("text/html")
      contentAsString(transferSummaryPage) must include("transferSummary.header")

      contentAsString(transferSummaryPage) must include("transferSummary.seriesReference")
      contentAsString(transferSummaryPage) must include(seriesCode.get.code.get)

      contentAsString(transferSummaryPage) must include("transferSummary.transferringBody")
      contentAsString(transferSummaryPage) must include(transferringBodyName.get.name.get)

      contentAsString(transferSummaryPage) must include("transferSummary.filesUploadedForTransfer")
      contentAsString(transferSummaryPage) must include(s"$totalFiles files uploaded")

      contentAsString(transferSummaryPage) must include("transferSummary.openRecords")
      contentAsString(transferSummaryPage) must include("transferSummary.transferLegalOwnership")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new TransferSummaryController(getUnauthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)
      val transferSummaryPage = controller.transferSummary(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-summary"))

      redirectLocation(transferSummaryPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferSummaryPage) mustBe FOUND
    }

    "throws an authorisation exception when the user does not have permission to see a consignment's transfer summary" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val graphQlError = GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(None)), List(graphQlError))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val transferSummaryPage = controller.transferSummary(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-summary").withCSRFToken)

      val failure: Throwable = transferSummaryPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an invalid form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val seriesCode = Some(gcs.GetConsignment.Series(Some("Mock Series 2")))
      val transferringBodyName = Some(gcs.GetConsignment.TransferringBody(Some("MockBody 2")))
      val totalFiles: Int = 4

      val consignmentResponse: gcs.GetConsignment = new gcs.GetConsignment(seriesCode, transferringBodyName, totalFiles)
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val transferSummarySubmit = controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary").withCSRFToken)

      playStatus(transferSummarySubmit) mustBe BAD_REQUEST
      contentAsString(transferSummarySubmit) must include("govuk-error-message")
      contentAsString(transferSummarySubmit) must include("error")
    }

    "redirects to the transfer confirmation page when a valid form is submitted" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      mockGraphqlResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val transferSummarySubmit: Result = controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary")
          .withFormUrlEncodedBody(("openRecords", "true"), ("transferLegalOwnership", "true"))
          .withCSRFToken
        ).futureValue

      transferSummarySubmit.header.status should equal(303)
    }

    "return an error when the call to the export api fails" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val client = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(ut.Data(Option(1))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(serverError()))

      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val transferSummarySubmitError: Throwable = controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary")
          .withFormUrlEncodedBody(("openRecords", "true"), ("transferLegalOwnership", "true"))
          .withCSRFToken
        ).failed.futureValue

      transferSummarySubmitError.getMessage should equal(s"Call to export API has returned a non 200 response for consignment $consignmentId")
    }

    "calls the export api when a valid form is submitted" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))
      mockGraphqlResponse
      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary")
          .withFormUrlEncodedBody(("openRecords", "true"), ("transferLegalOwnership", "true"))
          .withCSRFToken
        ).futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "return an error when the call to the graphql api fails" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val client = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(serverError()))

      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      val transferSummarySubmitError: Throwable = controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary")
          .withFormUrlEncodedBody(("openRecords", "true"), ("transferLegalOwnership", "true"))
          .withCSRFToken
        ).failed.futureValue

      transferSummarySubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "calls the graphql api when a valid form is submitted" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      mockGraphqlResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(ok()))

      val controller = new TransferSummaryController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)

      controller.transferSummarySubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-summary")
          .withFormUrlEncodedBody(("openRecords", "true"), ("transferLegalOwnership", "true"))
          .withCSRFToken
        ).futureValue

      wiremockServer.getAllServeEvents.size() should equal(1)
    }
  }

  private def mockGraphqlResponse = {
    val client = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(ut.Data(Option(1))), List())
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))
  }
}
