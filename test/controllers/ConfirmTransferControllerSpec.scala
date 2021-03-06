package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, ok, okJson, post, serverError, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcs}
import graphql.codegen.AddFinalTransferConfirmation.{AddFinalTransferConfirmation => aftc}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
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

class ConfirmTransferControllerSpec extends FrontEndTestHelper {
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

  "ConfirmTransferController GET" should {
    "render the transfer summary page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)

      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val confirmTransferPage = controller.confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      playStatus(confirmTransferPage) mustBe OK
      contentType(confirmTransferPage) mustBe Some("text/html")
      contentAsString(confirmTransferPage) must include("Confirm transfer")

      contentAsString(confirmTransferPage) must include("Series reference")
      contentAsString(confirmTransferPage) must include(consignmentSummaryResponse.series.get.code.get)

      contentAsString(confirmTransferPage) must include("Transferring body")
      contentAsString(confirmTransferPage) must include(consignmentSummaryResponse.transferringBody.get.name.get)

      contentAsString(confirmTransferPage) must include("Files uploaded for transfer")
      contentAsString(confirmTransferPage) must include(s"${consignmentSummaryResponse.totalFiles} files uploaded")

      contentAsString(confirmTransferPage) must include("Consignment reference")
      contentAsString(confirmTransferPage) must include(consignmentSummaryResponse.consignmentReference)

      contentAsString(confirmTransferPage) must include("I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.")
      contentAsString(confirmTransferPage) must include("I confirm that I am transferring legal ownership of these records to The National Archives.")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateConfirmTransferController(getUnauthorisedSecurityComponents)
      val confirmTransferPage = controller.confirmTransfer(consignmentId).apply(FakeRequest(GET, "/consignment/123/confirm-transfer"))

      redirectLocation(confirmTransferPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(confirmTransferPage) mustBe FOUND
    }

    "throws an authorisation exception when the user does not have permission to see a consignment's transfer summary" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val graphQlError = GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(None)), List(graphQlError))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val confirmTransferPage = controller.confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      val failure: Throwable = confirmTransferPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display correct errors when an empty final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = false, transferLegalOwnershipValue = false): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      contentAsString(finalTransferConfirmationSubmitResult) must include("govuk-error-message")
      contentAsString(finalTransferConfirmationSubmitResult) must include("error")

      contentAsString(finalTransferConfirmationSubmitResult) must include("There is a problem")
      contentAsString(finalTransferConfirmationSubmitResult) must include("#error-openRecords")
      contentAsString(finalTransferConfirmationSubmitResult) must include("#error-transferLegalOwnership")
      contentAsString(finalTransferConfirmationSubmitResult) must include("All records must be confirmed as open before proceeding")
      contentAsString(finalTransferConfirmationSubmitResult) must include("Transferral of legal ownership of all records must be confirmed before proceeding")
    }

    "display correct error when only the open records option is selected and the final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = false): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      contentAsString(finalTransferConfirmationSubmitResult) must include("govuk-error-message")
      contentAsString(finalTransferConfirmationSubmitResult) must include("error")

      contentAsString(finalTransferConfirmationSubmitResult) must include("There is a problem")
      contentAsString(finalTransferConfirmationSubmitResult) must include("#error-transferLegalOwnership")
      contentAsString(finalTransferConfirmationSubmitResult) must include("Transferral of legal ownership of all records must be confirmed before proceeding")

      contentAsString(finalTransferConfirmationSubmitResult) must not include "#error-openRecords"
      contentAsString(finalTransferConfirmationSubmitResult) must not include "All records must be confirmed as open before proceeding"
    }

    "display correct error when only the transfer legal ownership option is selected and the final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = false, transferLegalOwnershipValue = true): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      contentAsString(finalTransferConfirmationSubmitResult) must include("govuk-error-message")
      contentAsString(finalTransferConfirmationSubmitResult) must include("error")

      contentAsString(finalTransferConfirmationSubmitResult) must include("There is a problem")
      contentAsString(finalTransferConfirmationSubmitResult) must include("#error-openRecords")
      contentAsString(finalTransferConfirmationSubmitResult) must include("All records must be confirmed as open before proceeding")

      contentAsString(finalTransferConfirmationSubmitResult) must not include "#error-transferLegalOwnership"
      contentAsString(finalTransferConfirmationSubmitResult) must not include "Transferral of legal ownership of all records must be confirmed before"
    }

    "add a final transfer confirmation when a valid form is submitted and the api response is successful" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest()
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe SEE_OTHER
      redirectLocation(finalTransferConfirmationSubmitResult) must be(Some(s"/consignment/$consignmentId/transfer-complete"))
    }

    "render an error when a valid form is submitted but there is an error from the api" in {
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken)

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue
      failure mustBe an[Exception]
    }

    "throws an authorisation exception when the user does not have permission to save the transfer summary" in {
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken)

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "redirects to the transfer complete page when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult: Result = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken
        ).futureValue

      finalTransferConfirmationSubmitResult.header.status should equal(303)
    }

    "return an error when the call to the export api fails" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError.getMessage should equal(s"Call to export API has returned a non 200 response for consignment $consignmentId")
    }

    "calls the export api when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken
        ).futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "return an error when the call to the graphql api fails" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "calls the graphql api twice when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(ok()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalOwnershipValue = true): _*)
          .withCSRFToken
        ).futureValue

      wiremockServer.getAllServeEvents.size() should equal(2)
    }
  }

  private def instantiateConfirmTransferController(securityComponents: SecurityComponents) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new ConfirmTransferController(securityComponents, new GraphQLConfiguration(app.configuration),
      getValidKeycloakConfiguration, consignmentService, exportService(app.configuration), langs)
  }

  private def getConsignmentSummaryResponse: gcs.GetConsignment = {
    val seriesCode = Some(gcs.GetConsignment.Series(Some("Mock Series 2")))
    val transferringBodyName = Some(gcs.GetConsignment.TransferringBody(Some("MockBody 2")))
    val totalFiles: Int = 4
    val consignmentReference = "TEST-TDR-2021-GB"
    new gcs.GetConsignment(seriesCode, transferringBodyName, totalFiles, consignmentReference)
  }

  private def finalTransferConfirmationForm(openRecordsValue: Boolean, transferLegalOwnershipValue: Boolean): Seq[(String, String)] = {
    Seq(
      ("openRecords", openRecordsValue.toString),
      ("transferLegalOwnership", transferLegalOwnershipValue.toString)
    )
  }

  private def createFinalTransferConfirmationResponse = new aftc.AddFinalTransferConfirmation(
    consignmentId,
    finalOpenRecordsConfirmed = true,
    legalOwnershipTransferConfirmed = true
  )

  private def stubFinalTransferConfirmationResponse(finalTransferConfirmation: Option[aftc.AddFinalTransferConfirmation] = None,
                                                    errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(finalTransferConfirmation.map(ftc => aftc.Data(ftc)), errors)
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation AddFinalTransferConfirmation($$input:AddFinalTransferConfirmationInput!)
                            {addFinalTransferConfirmation(addFinalTransferConfirmationInput:$$input)
                            {consignmentId finalOpenRecordsConfirmed legalOwnershipTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"${consignmentId.toString}",
                                 "finalOpenRecordsConfirmed":true,
                                 "legalOwnershipTransferConfirmed":true
                                }
                       }
                             }""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(equalToJson(query))
      .willReturn(okJson(dataString)))
  }

  private def mockUpdateTransferInitiatedResponse = {
    val utClient = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
    val utData: utClient.GraphqlData = utClient.GraphqlData(Some(ut.Data(Option(1))), List())
    val utDataString: String = utData.asJson.printWith(Printer(dropNullValues = false, ""))
    val utQuery =
      s"""{"query":"mutation updateTransferInitiated($$consignmentId:UUID!)
         |                 {updateTransferInitiated(consignmentid:$$consignmentId)}",
         | "variables":{"consignmentId": "${consignmentId.toString}"}
         |}""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(equalToJson(utQuery))
      .willReturn(okJson(utDataString)))
  }
}
