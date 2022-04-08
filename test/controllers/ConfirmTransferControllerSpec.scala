package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import errors.AuthorisationException
import graphql.codegen.AddFinalJudgmentTransferConfirmation.{addFinalJudgmentTransferConfirmation => afjtc}
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcs}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcstatus}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.i18n.Langs
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import play.api.test.WsTestClient.InternalWSClient
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import util.{CheckHtmlOfFormOptions, EnglishLang, FrontEndTestHelper}

import java.util.UUID
import scala.collection.immutable.TreeMap
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

  val options = Map(
    "openRecords" -> "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
    "transferLegalCustody" -> "I confirm that I am transferring legal custody of these records to The National Archives."
  )

  val checkHtmlOfFormOptions = new CheckHtmlOfFormOptions(options)
  val consignmentId: UUID = UUID.randomUUID()

  def exportService(configuration: Configuration): ConsignmentExportService = {
    val wsClient = new InternalWSClient("http", 9007)
    new ConsignmentExportService(wsClient, configuration, new GraphQLConfiguration(configuration))
  }

  "ConfirmTransferController GET" should {
    "render the confirm transfer page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      mockGetConsignmentStatusResponse(Some("Completed"))

      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlResponse(dataString)

      val confirmTransferPage = controller.confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      val confirmTransferPageAsString = contentAsString(confirmTransferPage)

      playStatus(confirmTransferPage) mustBe OK
      contentType(confirmTransferPage) mustBe Some("text/html")
      confirmTransferPageAsString must include("Confirm transfer")

      confirmTransferPageAsString must include("Series reference")
      confirmTransferPageAsString must include(consignmentSummaryResponse.series.get.code)

      confirmTransferPageAsString must include("Transferring body")
      confirmTransferPageAsString must include(consignmentSummaryResponse.transferringBody.get.name)

      confirmTransferPageAsString must include("Files uploaded for transfer")
      confirmTransferPageAsString must include(s"${consignmentSummaryResponse.totalFiles} files uploaded")

      confirmTransferPageAsString must include("Consignment reference")
      confirmTransferPageAsString must include(consignmentSummaryResponse.consignmentReference)

      confirmTransferPageAsString must include (s"""" href="/faq">""")

      checkHtmlOfFormOptions.checkForOptionAndItsAttributes(confirmTransferPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateConfirmTransferController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val confirmTransferPage = controller.confirmTransfer(consignmentId).apply(FakeRequest(GET, "/consignment/123/confirm-transfer"))

      redirectLocation(confirmTransferPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(confirmTransferPage) mustBe FOUND
    }

    "throw an authorisation exception when the user does not have permission to see a consignment's transfer summary" in {
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
      mockGraphqlResponse(dataString)
      mockGetConsignmentStatusResponse()

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withCSRFToken)

      val confirmTransferPageAsString = contentAsString(finalTransferConfirmationSubmitResult)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      confirmTransferPageAsString must include("govuk-error-message")
      confirmTransferPageAsString must include("error")

      confirmTransferPageAsString must include("There is a problem")
      confirmTransferPageAsString must include("#error-openRecords")
      confirmTransferPageAsString must include("#error-transferLegalCustody")
      checkHtmlOfFormOptions.checkForOptionAndItsAttributes(confirmTransferPageAsString)
    }

    "display correct error when only the open records option is selected and the final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlResponse(dataString)
      mockGetConsignmentStatusResponse()

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val incompleteTransferConfirmationForm = finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = false)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(incompleteTransferConfirmationForm: _*)
          .withCSRFToken)

      val confirmTransferPageAsString = contentAsString(finalTransferConfirmationSubmitResult)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      confirmTransferPageAsString must include("govuk-error-message")
      confirmTransferPageAsString must include("error")

      confirmTransferPageAsString must include("There is a problem")
      confirmTransferPageAsString must include("#error-transferLegalCustody")
      confirmTransferPageAsString must include("Transferral of legal custody of all records must be confirmed before proceeding")

      confirmTransferPageAsString must not include "#error-openRecords"
      confirmTransferPageAsString must not include "All records must be confirmed as open before proceeding"

      checkHtmlOfFormOptions.checkForOptionAndItsAttributes(confirmTransferPageAsString, incompleteTransferConfirmationForm.toMap)
    }

    "display correct error when only the transfer legal custody option is selected and the final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlResponse(dataString)
      mockGetConsignmentStatusResponse()

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val incompleteTransferConfirmationForm = finalTransferConfirmationForm(openRecordsValue = false, transferLegalCustodyValue = true)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(incompleteTransferConfirmationForm: _*)
          .withCSRFToken)

      val confirmTransferPageAsString = contentAsString(finalTransferConfirmationSubmitResult)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      confirmTransferPageAsString must include("govuk-error-message")
      confirmTransferPageAsString must include("error")

      confirmTransferPageAsString must include("There is a problem")
      confirmTransferPageAsString must include("#error-openRecords")
      confirmTransferPageAsString must include("All records must be confirmed as open before proceeding")

      confirmTransferPageAsString must not include "#error-transferLegalCustody"
      confirmTransferPageAsString must not include "Transferral of legal custody of all records must be confirmed before"

      checkHtmlOfFormOptions.checkForOptionAndItsAttributes(confirmTransferPageAsString, incompleteTransferConfirmationForm.toMap)
    }

    "add a final transfer confirmation when a valid form is submitted and the api response is successful" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockGetConsignmentStatusResponse()
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse()
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest()
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe SEE_OTHER
      redirectLocation(finalTransferConfirmationSubmitResult) must be(Some(s"/consignment/$consignmentId/transfer-complete"))
    }

    "add a final judgment transfer confirmation when the api response is successful" in {
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)

      val addFinalJudgmentTransferConfirmationResponse: afjtc.AddFinalJudgmentTransferConfirmation = createFinalJudgmentTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalJudgmentTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse(consignmentType = "judgment")
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val finalTransferConfirmationSubmitResult = controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest()
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken)

      playStatus(finalTransferConfirmationSubmitResult) mustBe SEE_OTHER
      redirectLocation(finalTransferConfirmationSubmitResult) must be(Some(s"/judgment/$consignmentId/transfer-complete"))
    }

    "render an error when a valid form is submitted but there is an error from the api" in {
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken)

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue
      failure mustBe an[Exception]
    }

    "render an error when there is an error from the api for judgment" in {
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitResult = controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken)

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue
      failure mustBe an[Exception]
    }

    "throw an authorisation exception when the user does not have permission to save the transfer confirmation" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val data: client.GraphqlData = client.GraphqlData(
        Some(gc.Data(None)),
        List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentStatusResponse(dataString)

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken)
      mockGraphqlResponse()

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "throw an authorisation exception when the user does not have permission to save the transfer confirmation for judgment" in {
      stubFinalJudgmentTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      mockGraphqlResponse(consignmentType = "judgment")
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitResult = controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken)

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "redirects to the transfer complete page when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockGetConsignmentStatusResponse()
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse()
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult: Result = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken
        ).futureValue

      finalTransferConfirmationSubmitResult.header.status should equal(303)
    }

    "return an error when the call to the export api fails" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse()
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError mustBe an[Exception]
    }

    "return an error when the call to the export api fails for judgment" in {
      val addFinalTransferConfirmationResponse: afjtc.AddFinalJudgmentTransferConfirmation = createFinalJudgmentTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse(consignmentType = "judgment")
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError.getMessage should equal(s"Call to export API has returned a non 200 response for consignment $consignmentId")
    }

    "calls the export api when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      mockGetConsignmentStatusResponse()
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse()
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken
        ).futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "calls the export api when a valid form is submitted for judgment" in {
      val addFinalTransferConfirmationResponse: afjtc.AddFinalJudgmentTransferConfirmation = createFinalJudgmentTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse(consignmentType = "judgment")
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(okJson("{}")))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken
        ).futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "return an error when the call to the graphql api fails" in {
      mockGraphqlResponse()
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentSummary"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "return an error when the call to the graphql api fails for judgment" in {
      mockGraphqlResponse(consignmentType = "judgment")
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentSummary"))
        .willReturn(serverError()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitError: Throwable = controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed = true): _*)
          .withCSRFToken
        ).failed.futureValue

      finalTransferConfirmationSubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "calls the graphql api four times when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      mockGetConsignmentStatusResponse()
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlResponse()
      wiremockExportServer.stubFor(post(urlEqualTo(s"/export/$consignmentId"))
        .willReturn(ok()))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = true): _*)
          .withCSRFToken
        ).futureValue

      wiremockServer.getAllServeEvents.size() should equal(4)
    }

    "render the confirm transfer 'already confirmed' page with an authenticated user if confirmTransfer status is 'Completed'" in {
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcstatus.Data, gcstatus.Variables]()
      val consignmentResponse = gcstatus.Data(Option(GetConsignment(None, CurrentStatus(None, None, None, Some("Completed")))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentStatusResponse(dataString)

      val transferControllerPage = controller.confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, f"/consignment/$consignmentId/confirm-transfer").withCSRFToken)
      val confirmTransferPageAsString = contentAsString(transferControllerPage)

      playStatus(transferControllerPage) mustBe OK
      contentType(transferControllerPage) mustBe Some("text/html")
      headers(transferControllerPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      confirmTransferPageAsString must include(
        s"""href="/consignment/$consignmentId/transfer-complete">
           |                Continue""".stripMargin)
      confirmTransferPageAsString must include("Your transfer has been completed")
    }

    "render the confirm transfer 'already confirmed' page with an authenticated user if the user navigates back to the" +
      "confirmTransfer after previously successfully submitting the transfer" in {
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcstatus.Data, gcstatus.Variables]()
      val consignmentResponse = gcstatus.Data(Option(GetConsignment(None, CurrentStatus(None, None, None, Some("Completed")))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentStatusResponse(dataString)

      val ctAlreadyConfirmedPage = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/confirm-transfer").withCSRFToken)
      val ctAlreadyConfirmedPageAsString = contentAsString(ctAlreadyConfirmedPage)

      playStatus(ctAlreadyConfirmedPage) mustBe OK
      contentType(ctAlreadyConfirmedPage) mustBe Some("text/html")
      headers(ctAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      ctAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/$consignmentId/transfer-complete">
           |                Continue""".stripMargin)
      ctAlreadyConfirmedPageAsString must include("Your transfer has been completed")
    }

    "render the confirm transfer 'already confirmed' page with an authenticated user if the user navigates back to the" +
      "confirmTransfer after previously submitting an incorrect form" in {
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcstatus.Data, gcstatus.Variables]()
      val consignmentResponse = gcstatus.Data(Option(GetConsignment(None, CurrentStatus(None, None, None, Some("Completed")))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentStatusResponse(dataString)
      val incompleteTransferConfirmationForm = finalTransferConfirmationForm(openRecordsValue = true, transferLegalCustodyValue = false)

      val ctAlreadyConfirmedPage = controller.finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/confirm-transfer")
          .withFormUrlEncodedBody(incompleteTransferConfirmationForm:_*)
          .withCSRFToken)
      val ctAlreadyConfirmedPageAsString = contentAsString(ctAlreadyConfirmedPage)

      playStatus(ctAlreadyConfirmedPage) mustBe OK
      contentType(ctAlreadyConfirmedPage) mustBe Some("text/html")
      headers(ctAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      ctAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/$consignmentId/transfer-complete">
           |                Continue""".stripMargin)
      ctAlreadyConfirmedPageAsString must include("Your transfer has been completed")
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, user)

        val fileChecksPage = url match {
          case "judgment" =>
            mockGraphqlResponse()
            controller.finalJudgmentTransferConfirmationSubmit(consignmentId)
            .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
              .withCSRFToken)
          case "consignment" =>
            mockGraphqlResponse(consignmentType = "judgment")
            controller.finalTransferConfirmationSubmit(consignmentId)
            .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
              .withCSRFToken
            )
        }
        playStatus(fileChecksPage) mustBe FORBIDDEN
      }
    }
  }

  private def mockGraphqlResponse(dataString: String = "", consignmentType: String = "standard") = {
    if(dataString.nonEmpty) {
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentSummary"))
        .willReturn(okJson(dataString)))
    }
    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }

  private def mockGraphqlConsignmentStatusResponse(dataString: String = "", consignmentType: String = "standard") = {
    if(dataString.nonEmpty) {
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentStatus"))
        .willReturn(okJson(dataString)))
    }

    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }

  private def instantiateConfirmTransferController(securityComponents: SecurityComponents,
                                                   keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val confirmTransferService = new ConfirmTransferService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new ConfirmTransferController(securityComponents, new GraphQLConfiguration(app.configuration),
      keycloakConfiguration, consignmentService, confirmTransferService, exportService(app.configuration), consignmentStatusService, langs)
  }

  private def getConsignmentSummaryResponse: gcs.GetConsignment = {
    val seriesCode = Some(gcs.GetConsignment.Series("Mock Series 2"))
    val transferringBodyName = Some(gcs.GetConsignment.TransferringBody("MockBody 2"))
    val totalFiles: Int = 4
    val consignmentReference = "TEST-TDR-2021-GB"
    new gcs.GetConsignment(seriesCode, transferringBodyName, totalFiles, consignmentReference)
  }

  private def finalTransferConfirmationForm(openRecordsValue: Boolean, transferLegalCustodyValue: Boolean): Seq[(String, String)] = {
    Seq(
      ("openRecords", openRecordsValue.toString),
      ("transferLegalCustody", transferLegalCustodyValue.toString)
    )
  }

  private def finalJudgmentTransferConfirmationForm(legalCustodyTransferConfirmed: Boolean): Seq[(String, String)] = {
    Seq(
      ("legalCustodyTransferConfirmed", legalCustodyTransferConfirmed.toString)
    )
  }

  private def createFinalTransferConfirmationResponse = new aftc.AddFinalTransferConfirmation(
    consignmentId,
    finalOpenRecordsConfirmed = true,
    legalCustodyTransferConfirmed = true
  )

  private def createFinalJudgmentTransferConfirmationResponse = new afjtc.AddFinalJudgmentTransferConfirmation(
    consignmentId,
    legalCustodyTransferConfirmed = true
  )

  private def stubFinalTransferConfirmationResponse(finalTransferConfirmation: Option[aftc.AddFinalTransferConfirmation] = None,
                                                    errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(finalTransferConfirmation.map(ftc => aftc.Data(ftc)), errors)
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation addFinalTransferConfirmation($$input:AddFinalTransferConfirmationInput!)
                            {addFinalTransferConfirmation(addFinalTransferConfirmationInput:$$input)
                            {consignmentId finalOpenRecordsConfirmed legalCustodyTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"$consignmentId",
                                 "finalOpenRecordsConfirmed":true,
                                 "legalCustodyTransferConfirmed":true
                                }
                       }
                             }""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(equalToJson(query))
      .willReturn(okJson(dataString)))
  }
  private def stubFinalJudgmentTransferConfirmationResponse(finalJudgmentTransferConfirmation: Option[afjtc.AddFinalJudgmentTransferConfirmation] = None,
                                                            errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[afjtc.Data, afjtc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(finalJudgmentTransferConfirmation.map(ftc => afjtc.Data(ftc)), errors)
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation addFinalJudgmentTransferConfirmation($$input:AddFinalJudgmentTransferConfirmationInput!)
                            {addFinalJudgmentTransferConfirmation(addFinalJudgmentTransferConfirmationInput:$$input)
                            {consignmentId legalCustodyTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"$consignmentId",
                                 "legalCustodyTransferConfirmed":true
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

  private def mockGetConsignmentStatusResponse(seriesStatus: Option[String] = None,
                                               transferAgreementStatus: Option[String] = None,
                                               uploadStatus: Option[String] = None,
                                               confirmTransferStatus: Option[String] = None)
                                              (implicit ec: ExecutionContext) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcstatus.Data, gcstatus.Variables]()
    val data = client.GraphqlData(Option(gcstatus.Data(Option(gcstatus.GetConsignment(
      None, CurrentStatus(seriesStatus, transferAgreementStatus, uploadStatus, confirmTransferStatus))))), List())
    val dataString = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val formattedJsonBody =
      s"""{"query":"query getConsignmentStatus($$consignmentId:UUID!){
                                                       getConsignment(consignmentid:$$consignmentId){
                                                         currentStatus{series transferAgreement upload confirmTransfer}
                                                       }
                                                }",
                                                "variables":{
                                                  "consignmentId":"${consignmentId.toString}"
                                                }
                                       }"""
    val unformattedJsonBody = removeNewLinesAndIndentation(formattedJsonBody)

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentStatus"))
      .willReturn(okJson(dataString))
    )
  }

  private def removeNewLinesAndIndentation(formattedJsonBody: String) = {
    formattedJsonBody.replaceAll("\n\\s*", "")
  }
}
