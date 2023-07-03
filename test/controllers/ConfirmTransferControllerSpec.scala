package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import errors.AuthorisationException
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcs}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
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
import testUtils.{CheckPageForStaticElements, EnglishLang, FormTester, FrontEndTestHelper}
import testUtils.DefaultMockFormOptions.expectedConfirmTransferOptions

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
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

  val formTester = new FormTester(expectedConfirmTransferOptions)
  val checkPageForStaticElements = new CheckPageForStaticElements
  val consignmentId: UUID = UUID.randomUUID()
  private val completedConfirmTransferForm: Seq[(String, String)] = Seq(("transferLegalCustody", "true"))
  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  def exportService(configuration: Configuration): ConsignmentExportService = {
    val wsClient = new InternalWSClient("http", 9007)
    new ConsignmentExportService(wsClient, configuration, new GraphQLConfiguration(configuration))
  }

  "ConfirmTransferController GET" should {
    "render the confirm transfer page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentSummaryResponse(dataString)

      val confirmTransferPage = controller
        .confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      val confirmTransferPageAsString = contentAsString(confirmTransferPage)

      playStatus(confirmTransferPage) mustBe OK
      contentType(confirmTransferPage) mustBe Some("text/html")
      confirmTransferPageAsString must include("<title>Confirm transfer - Transfer Digital Records - GOV.UK</title>")
      confirmTransferPageAsString must include("""<h1 class="govuk-heading-l">Confirm transfer</h1>""")

      confirmTransferPageAsString must include(
        """<div class="govuk-notification-banner govuk-!-margin-bottom-4" role="region" aria-labelledby="govuk-notification-banner-title" data-module="govuk-notification-banner">
          |    <div class="govuk-notification-banner__header">
          |        <h2 class="govuk-notification-banner__title" id="govuk-notification-banner-title">
          |            notification.savedProgress.title
          |        </h2>
          |    </div>
          |    <div class="govuk-notification-banner__content">
          |        <h3 class="govuk-notification-banner__heading">
          |            notification.savedProgress.heading
          |        </h3>
          |        <p class="govuk-body">notification.savedProgress.metadataInfo</p>
          |    </div>
          |</div>""".stripMargin
      )

      confirmTransferPageAsString must include("""<p class="govuk-body">Here is a summary of the records you have uploaded.</p>""")

      confirmTransferPageAsString must include(
        """                    <dt class="govuk-summary-list__key govuk-!-width-one-half">
          |                        Series reference
          |                    </dt>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dd class="govuk-summary-list__value">
           |                        ${consignmentSummaryResponse.series.get.code}
           |                    </dd>""".stripMargin
      )

      confirmTransferPageAsString must include(
        """                    <dt class="govuk-summary-list__key">
          |                        Consignment reference
          |                    </dt>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dd class="govuk-summary-list__value">
           |                        ${consignmentSummaryResponse.consignmentReference}
           |                    </dd>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dt class="govuk-summary-list__key">
           |                        Transferring body
           |                    </dt>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dd class="govuk-summary-list__value">
           |                        ${consignmentSummaryResponse.transferringBody.get.name}
           |                    </dd>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dt class="govuk-summary-list__key">
           |                        Files uploaded for transfer
           |                    </dt>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""                    <dd class="govuk-summary-list__value">
           |                        ${consignmentSummaryResponse.totalFiles} files uploaded
           |                    </dd>""".stripMargin
      )

      confirmTransferPageAsString must include(
        s"""<form action="/consignment/$consignmentId/confirm-transfer" method="POST" novalidate="">"""
      )

      confirmTransferPageAsString must include regex (
        s"""<input type="hidden" name="csrfToken" value="[0-9a-z\\-]+"/>"""
      )

      confirmTransferPageAsString must include(
        s"""                    <div class="govuk-button-group">
           |                            <!-- Transfer -->
           |                        <a href="/consignment/$consignmentId/additional-metadata/download-metadata" role="button" draggable="false" class="govuk-button govuk-button--secondary" data-module="govuk-button">
           |                            Back
           |                        </a>
           |                        <button data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button">
           |                            Transfer your records
           |                        </button>
           |                    </div>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(confirmTransferPageAsString, userType = "standard")
      formTester.checkHtmlForOptionAndItsAttributes(confirmTransferPageAsString, Map())
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
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val confirmTransferPage = controller
        .confirmTransfer(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      val failure: Throwable = confirmTransferPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display correct errors when an empty final transfer confirmation form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentSummaryResponse(dataString)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)

      val confirmTransferPageAsString = contentAsString(finalTransferConfirmationSubmitResult)

      playStatus(finalTransferConfirmationSubmitResult) mustBe BAD_REQUEST

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(confirmTransferPageAsString, userType = "standard")
      formTester.checkHtmlForOptionAndItsAttributes(confirmTransferPageAsString, Map(), formStatus = "PartiallySubmitted")
    }

    "add a final transfer confirmation when a valid form is submitted and the api response is successful" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(okJson("{}"))
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest()
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )

      playStatus(finalTransferConfirmationSubmitResult) mustBe SEE_OTHER
      redirectLocation(finalTransferConfirmationSubmitResult) must be(Some(s"/consignment/$consignmentId/transfer-complete"))
    }

    "add a final judgment transfer confirmation when the api response is successful" in {
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val addFinalJudgmentTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalJudgmentTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(okJson("{}"))
      )

      val finalTransferConfirmationSubmitResult = controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest()
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )

      playStatus(finalTransferConfirmationSubmitResult) mustBe SEE_OTHER
      redirectLocation(finalTransferConfirmationSubmitResult) must be(Some(s"/judgment/$consignmentId/transfer-complete"))
    }

    "render an error when a valid form is submitted but there is an error from the api" in {
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue
      failure mustBe an[Exception]
    }

    "render an error when there is an error from the api for judgment" in {
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      stubFinalTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitResult = controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue
      failure mustBe an[Exception]
    }

    "throw an authorisation exception when the user does not have permission to save the transfer confirmation" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gc.Data(None)), List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentStatus"))
          .willReturn(okJson(dataString))
      )
      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
      mockGraphqlConsignmentSummaryResponse()

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "throw an authorisation exception when the user does not have permission to save the transfer confirmation for judgment" in {
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      stubFinalJudgmentTransferConfirmationResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitResult = controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )

      val failure: Throwable = finalTransferConfirmationSubmitResult.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "redirects to the transfer complete page when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(okJson("{}"))
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitResult: Result = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .futureValue

      finalTransferConfirmationSubmitResult.header.status should equal(303)
    }

    "return an error when the call to the export api fails" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(serverError())
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .failed
        .futureValue

      finalTransferConfirmationSubmitError mustBe an[Exception]
    }

    "return an error when the call to the export api fails for judgment" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(serverError())
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitError: Throwable = controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .failed
        .futureValue

      finalTransferConfirmationSubmitError.getMessage should equal(s"Call to export API has returned a non 200 response for consignment $consignmentId")
    }

    "calls the export api when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(okJson("{}"))
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "calls the export api when a valid form is submitted for judgment" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalJudgmentTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(okJson("{}"))
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .futureValue

      wiremockExportServer.getAllServeEvents.size() should equal(1)
    }

    "return an error when the call to the graphql api fails" in {
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentSummary"))
          .willReturn(serverError())
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      val finalTransferConfirmationSubmitError: Throwable = controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .failed
        .futureValue

      finalTransferConfirmationSubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "return an error when the call to the graphql api fails for judgment" in {
      mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentSummary"))
          .willReturn(serverError())
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val finalTransferConfirmationSubmitError: Throwable = controller
        .finalJudgmentTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .failed
        .futureValue

      finalTransferConfirmationSubmitError.getMessage should startWith("Unexpected response from GraphQL API")
    }

    "calls the graphql api four times when a valid form is submitted" in {
      val addFinalTransferConfirmationResponse: aftc.AddFinalTransferConfirmation = createFinalTransferConfirmationResponse
      stubFinalTransferConfirmationResponse(Some(addFinalTransferConfirmationResponse))
      mockUpdateTransferInitiatedResponse
      mockGraphqlConsignmentSummaryResponse()
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      wiremockExportServer.stubFor(
        post(urlEqualTo(s"/export/$consignmentId"))
          .willReturn(ok())
      )

      val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
      controller
        .finalTransferConfirmationSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
            .withFormUrlEncodedBody(completedConfirmTransferForm: _*)
            .withCSRFToken
        )
        .futureValue

      wiremockServer.getAllServeEvents.size() should equal(4)
    }

    forAll(userTypes) { userType =>
      forAll(consignmentStatuses) { consignmentStatus =>
        s"render the confirm transfer 'already confirmed' page with an authenticated $userType user if export status is '$consignmentStatus'" in {
          val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
          val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Export", consignmentStatus, someDateTime, None))
          setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
          setConsignmentReferenceResponse(wiremockServer)
          setConsignmentTypeResponse(wiremockServer, "standard")

          val ctAlreadyConfirmedPage = controller
            .confirmTransfer(consignmentId)
            .apply(FakeRequest(GET, f"/consignment/$consignmentId/confirm-transfer").withCSRFToken)
          val ctAlreadyConfirmedPageAsString = contentAsString(ctAlreadyConfirmedPage)

          playStatus(ctAlreadyConfirmedPage) mustBe OK
          contentType(ctAlreadyConfirmedPage) mustBe Some("text/html")
          headers(ctAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(ctAlreadyConfirmedPageAsString, userType = "standard")
          checkForCommonElementsOnConfirmationPage(ctAlreadyConfirmedPageAsString)
        }

        s"render the confirm transfer 'already confirmed' page with an authenticated user if the $userType user navigates back to the " +
          s"confirmTransfer after previously successfully submitting the transfer and the export status is '$consignmentStatus'" in {
            val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
            val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Export", consignmentStatus, someDateTime, None))
            setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
            setConsignmentReferenceResponse(wiremockServer)
            setConsignmentTypeResponse(wiremockServer, "standard")

            val ctAlreadyConfirmedPage = controller
              .finalTransferConfirmationSubmit(consignmentId)
              .apply(FakeRequest(POST, f"/consignment/$consignmentId/confirm-transfer").withCSRFToken)
            val ctAlreadyConfirmedPageAsString = contentAsString(ctAlreadyConfirmedPage)

            playStatus(ctAlreadyConfirmedPage) mustBe OK
            contentType(ctAlreadyConfirmedPage) mustBe Some("text/html")
            headers(ctAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
            checkPageForStaticElements.checkContentOfPagesThatUseMainScala(ctAlreadyConfirmedPageAsString, userType = "standard")
            checkForCommonElementsOnConfirmationPage(ctAlreadyConfirmedPageAsString)
          }

        s"render the confirm transfer 'already confirmed' page with an authenticated user if the $userType user navigates back to the " +
          s"confirmTransfer after previously submitting an incorrect form and the export status is '$consignmentStatus'" in {
            val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents)
            val incompleteTransferConfirmationForm = Seq()
            val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Export", consignmentStatus, someDateTime, None))
            setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
            setConsignmentReferenceResponse(wiremockServer)
            setConsignmentTypeResponse(wiremockServer, "standard")

            val ctAlreadyConfirmedPage = controller
              .finalTransferConfirmationSubmit(consignmentId)
              .apply(
                FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer")
                  .withFormUrlEncodedBody(incompleteTransferConfirmationForm: _*)
                  .withCSRFToken
              )
            val ctAlreadyConfirmedPageAsString = contentAsString(ctAlreadyConfirmedPage)

            playStatus(ctAlreadyConfirmedPage) mustBe OK
            contentType(ctAlreadyConfirmedPage) mustBe Some("text/html")
            headers(ctAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

            checkPageForStaticElements.checkContentOfPagesThatUseMainScala(ctAlreadyConfirmedPageAsString, userType = "standard")
            checkForCommonElementsOnConfirmationPage(ctAlreadyConfirmedPageAsString)
          }
      }
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val controller = instantiateConfirmTransferController(getAuthorisedSecurityComponents, user)

        val fileChecksPage = url match {
          case "judgment" =>
            mockGraphqlConsignmentSummaryResponse()
            controller
              .finalJudgmentTransferConfirmationSubmit(consignmentId)
              .apply(FakeRequest(POST, s"/judgment/$consignmentId/confirm-transfer").withCSRFToken)
          case "consignment" =>
            mockGraphqlConsignmentSummaryResponse(consignmentType = "judgment")
            controller
              .finalTransferConfirmationSubmit(consignmentId)
              .apply(FakeRequest(POST, s"/consignment/$consignmentId/confirm-transfer").withCSRFToken)
        }
        playStatus(fileChecksPage) mustBe FORBIDDEN
      }
    }
  }

  s"completedConfirmTransferForm length" should {
    s"equal the arity of the FinalTransferConfirmationData case class " in {
      completedConfirmTransferForm.length should equal(FinalTransferConfirmationData(true).productArity)
    }
  }

  private def mockGraphqlConsignmentSummaryResponse(dataString: String = "", consignmentType: String = "standard") = {
    if (dataString.nonEmpty) {
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentSummary"))
          .willReturn(okJson(dataString))
      )
    }
    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }

  private def instantiateConfirmTransferController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val confirmTransferService = new ConfirmTransferService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new ConfirmTransferController(
      securityComponents,
      new GraphQLConfiguration(app.configuration),
      keycloakConfiguration,
      consignmentService,
      confirmTransferService,
      exportService(app.configuration),
      consignmentStatusService,
      langs
    )
  }

  private def getConsignmentSummaryResponse: gcs.GetConsignment = {
    val seriesCode = Some(gcs.GetConsignment.Series("Mock Series 2"))
    val transferringBodyName = Some(gcs.GetConsignment.TransferringBody("MockBody 2"))
    val totalFiles: Int = 4
    val consignmentReference = "TEST-TDR-2021-GB"
    new gcs.GetConsignment(seriesCode, transferringBodyName, totalFiles, consignmentReference)
  }

  private def createFinalTransferConfirmationResponse = new aftc.AddFinalTransferConfirmation(
    consignmentId,
    legalCustodyTransferConfirmed = true
  )

  private def createFinalJudgmentTransferConfirmationResponse = new aftc.AddFinalTransferConfirmation(
    consignmentId,
    legalCustodyTransferConfirmed = true
  )

  private def stubFinalTransferConfirmationResponse(finalTransferConfirmation: Option[aftc.AddFinalTransferConfirmation] = None, errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(finalTransferConfirmation.map(ftc => aftc.Data(ftc)), errors)
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation addFinalTransferConfirmation($$input:AddFinalTransferConfirmationInput!)
                            {addFinalTransferConfirmation(addFinalTransferConfirmationInput:$$input)
                            {consignmentId legalCustodyTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"$consignmentId",
                                 "legalCustodyTransferConfirmed":true
                                }
                       }
                             }""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(query))
        .willReturn(okJson(dataString))
    )
  }

  private def stubFinalJudgmentTransferConfirmationResponse(
      finalJudgmentTransferConfirmation: Option[aftc.AddFinalTransferConfirmation] = None,
      errors: List[GraphQLClient.Error] = Nil
  ): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(finalJudgmentTransferConfirmation.map(ftc => aftc.Data(ftc)), errors)
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
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(query))
        .willReturn(okJson(dataString))
    )
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
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(utQuery))
        .willReturn(okJson(utDataString))
    )
  }

  private def checkForCommonElementsOnConfirmationPage(transferAlreadyConfirmedPageAsString: String, transferType: String = "consignment") = {
    transferAlreadyConfirmedPageAsString must include("""<h1 class="govuk-heading-l">Your transfer has already been completed</h1>""")
    transferAlreadyConfirmedPageAsString must include("""<p class="govuk-body">Click 'Continue' to see the confirmation page again or return to the start.</p>""")

    transferAlreadyConfirmedPageAsString must include(
      s"""href="/$transferType/$consignmentId/transfer-complete">Continue""".stripMargin
    )
  }
}
