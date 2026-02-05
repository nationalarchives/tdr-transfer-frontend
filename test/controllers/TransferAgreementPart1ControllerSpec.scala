package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.scalatest.concurrent.ScalaFutures._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.Statuses.{InProgressValue, TransferAgreementType}
import services.{ConsignmentMetadataService, ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import testUtils.DefaultMockFormOptions.expectedPart1Options
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper, TransferAgreementTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema.legal_status

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementPart1ControllerSpec extends FrontEndTestHelper with TableDrivenPropertyChecks {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val consignmentMetadataService: ConsignmentMetadataService = mock[ConsignmentMetadataService]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
  val taHelper = new TransferAgreementTestHelper(wiremockServer)
  val checkPageForStaticElements = new CheckPageForStaticElements
  val expectedConfirmationMessage = "You must confirm all statements before proceeding."

  s"TransferAgreementPart1Controller GET" should {
    val inputOptions = expectedPart1Options
    val expectedWarningMessage = "transferAgreement.warning"
    val formOptions: FormTester = new FormTester(inputOptions, "")
    val optionsToSelectToGenerateFormErrors: Seq[Seq[(String, String)]] =
      formOptions.generateOptionsToSelectToGenerateFormErrors()

    "render the series page with an authenticated user if series status is not 'Completed'" in {
      val consignmentId = UUID.randomUUID()

      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementPage) mustBe SEE_OTHER
      redirectLocation(transferAgreementPage).get must equal(s"/consignment/$consignmentId/series")
    }

    "render the series page with an authenticated user if series status is not 'Completed' when blockLegalStatus is 'false'" in {
      val consignmentId = UUID.randomUUID()

      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration,
          blockLegalStatus = false
        )
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

      playStatus(transferAgreementPage) mustBe SEE_OTHER
      redirectLocation(transferAgreementPage).get must equal(s"/consignment/$consignmentId/series")
    }

    "render the transfer agreement (part 1) page with an authenticated user if consignment status is not 'InProgress' or not 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
      checkForExpectedTAPart1PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedWarningMessage)
      formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map())
    }

    "render the transfer agreement (part 1) page with legal status for an authenticated user when blockLegalStatus is 'false'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration,
          blockLegalStatus = false
        )

      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      checkForExpectedTAPart1WithLegalStatusPageContent(transferAgreementPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getUnauthorisedSecurityComponents,
          app.configuration
        )
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "throw an authorisation exception when the user does not have permission to see a consignment's private beta transfer agreement" in {
      taHelper.mockGetConsignmentGraphqlResponse(app.configuration)

      val consignmentId = UUID.randomUUID()
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

      val failure: Throwable = transferAgreementPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "create a transfer agreement when a valid form is submitted and the api response is successful" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val addTransferAgreementResponse: atapb.AddTransferAgreementPrivateBeta = new atapb.AddTransferAgreementPrivateBeta(
        consignmentId,
        true
      )
      taHelper.stubTAPart1Response(Some(addTransferAgreementResponse), app.configuration)

      setConsignmentTypeResponse(wiremockServer, taHelper.userType)

      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part1)
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm: _*).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued"))
    }

    "create a transfer agreement when a valid form is submitted and set TransferAgreement status to InProgress when blockLegalStatus is 'false'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentReferenceResponse(wiremockServer)
      setAddConsignmentStatusResponse(wiremockServer, consignmentId, TransferAgreementType, InProgressValue)

      val legalStatus = "Public Record(s)"
      val metadata = Map(legal_status -> legalStatus)
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))


      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration,
          blockLegalStatus = false
        )
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(("legalStatus", legalStatus)).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued"))
      metadataRowCaptor.getValue mustBe metadata
    }

    "create a transfer agreement when a valid form is submitted and don't set the TransferAgreement status is already present in DB when blockLegalStatus is 'false'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), TransferAgreementType.id, InProgress.value, someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val legalStatus = "Public Record(s)"
      val metadata = Map(legal_status -> legalStatus)
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))


      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration,
          blockLegalStatus = false
        )
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(("legalStatus", legalStatus)).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued"))
      metadataRowCaptor.getValue mustBe metadata
    }

    "Display an error when the form is submitted without selection an option when blockLegalStatus is 'false'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), TransferAgreementType.id, InProgress.value, someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration,
          blockLegalStatus = false
        )
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody().withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      checkForExpectedTAPart1WithLegalStatusPageContent(transferAgreementPageAsString, hasError = true)
    }

    "render an error when a valid (private beta) form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAPart1Response(config = app.configuration, errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part1)
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
            .withFormUrlEncodedBody(completedTransferAgreementForm: _*)
            .withCSRFToken
        )

      val failure: Throwable = transferAgreementSubmit.failed.futureValue
      failure mustBe an[Exception]
    }

    "throw an authorisation exception when the user does not have permission to save the private beta transfer agreement" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAPart1Response(
        config = app.configuration,
        errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
      )
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part1)
      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
            .withFormUrlEncodedBody(completedTransferAgreementForm: _*)
            .withCSRFToken
        )

      val failure: Throwable = transferAgreementSubmit.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an empty private beta form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val incompleteTransferAgreementForm: Seq[(String, String)] = Seq()

      val transferAgreementSubmit = controller
        .transferAgreementSubmit(consignmentId)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
            .withFormUrlEncodedBody(incompleteTransferAgreementForm: _*)
            .withCSRFToken
        )
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      formOptions.checkHtmlForOptionAndItsAttributes(
        transferAgreementPageAsString,
        incompleteTransferAgreementForm.toMap,
        "PartiallySubmitted"
      )
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
      checkForExpectedTAPart1PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedWarningMessage)
    }

    optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
      val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
      s"display errors when a partially complete private beta form (only these options: $optionsAsString selected) is submitted" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart1Controller =
          instantiateTransferAgreementPart1Controller(
            getAuthorisedSecurityComponents,
            app.configuration,
            getValidStandardUserKeycloakConfiguration
          )

        val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))

        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val incompleteTransferAgreementForm: Seq[(String, String)] = optionsToSelect

        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(
            FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
              .withFormUrlEncodedBody(incompleteTransferAgreementForm: _*)
              .withCSRFToken
          )
        val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

        playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
        formOptions.checkHtmlForOptionAndItsAttributes(
          transferAgreementPageAsString,
          incompleteTransferAgreementForm.toMap,
          "PartiallySubmitted"
        )
        transferAgreementPageAsString must include("govuk-error-message")
        transferAgreementPageAsString must include("error")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
        checkForExpectedTAPart1PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedWarningMessage)
      }
    }

    "render the transfer agreement (part 1) 'already confirmed' page with an authenticated user if consignment status is 'InProgress'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None)
      )

      setConsignmentStatusResponse(
        app.configuration,
        wiremockServer,
        consignmentStatuses = consignmentStatuses
      )
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map(), formStatus = "Submitted")
      taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, confirmationMessage = expectedConfirmationMessage)
      checkForExpectedTAPart1PageContent(transferAgreementPageAsString, expectedWarning = expectedWarningMessage)
    }

    "render the transfer agreement (part 1) 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
      )

      setConsignmentStatusResponse(
        app.configuration,
        wiremockServer,
        consignmentStatuses = consignmentStatuses
      )
      setConsignmentTypeResponse(wiremockServer, taHelper.userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAgreementPage = controller
        .transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
      formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map(), formStatus = "Submitted")
      taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, confirmationMessage = expectedConfirmationMessage)
      checkForExpectedTAPart1PageContent(transferAgreementPageAsString, expectedWarning = expectedWarningMessage)
    }

    "render the transfer agreement (part 1) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
      "after successfully submitting transfer agreement form having previously submitted an empty form" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller =
          instantiateTransferAgreementPart1Controller(
            getAuthorisedSecurityComponents,
            app.configuration,
            getValidStandardUserKeycloakConfiguration
          )

        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
        )

        setConsignmentStatusResponse(
          app.configuration,
          wiremockServer,
          consignmentStatuses = consignmentStatuses
        )
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val taAlreadyConfirmedPage = controller
          .transferAgreementSubmit(consignmentId)
          .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
        val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

        playStatus(taAlreadyConfirmedPage) mustBe OK
        contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
        headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(taAlreadyConfirmedPageAsString, userType = taHelper.userType)
        taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString, confirmationMessage = expectedConfirmationMessage)
        checkForExpectedTAPart1PageContent(taAlreadyConfirmedPageAsString, expectedWarning = expectedWarningMessage)
        formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
      }

    optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
      val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
      "render the transfer agreement (part 1) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
        "after successfully submitting transfer agreement form having previously submitted a partially complete form " +
        s"(only these options: $optionsAsString selected)" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller =
            instantiateTransferAgreementPart1Controller(
              getAuthorisedSecurityComponents,
              app.configuration,
              getValidStandardUserKeycloakConfiguration
            )

          val consignmentStatuses = List(
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
          )

          setConsignmentStatusResponse(
            app.configuration,
            wiremockServer,
            consignmentStatuses = consignmentStatuses
          )
          setConsignmentTypeResponse(wiremockServer, taHelper.userType)
          setConsignmentReferenceResponse(wiremockServer)

          val incompleteTransferAgreementForm: Seq[(String, String)] = optionsToSelect

          val taAlreadyConfirmedPage = controller
            .transferAgreementSubmit(consignmentId)
            .apply(
              FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
                .withFormUrlEncodedBody(incompleteTransferAgreementForm: _*)
                .withCSRFToken
            )
          val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

          playStatus(taAlreadyConfirmedPage) mustBe OK
          contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
          headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(taAlreadyConfirmedPageAsString, userType = taHelper.userType)
          taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString, confirmationMessage = expectedConfirmationMessage)
          checkForExpectedTAPart1PageContent(taAlreadyConfirmedPageAsString, expectedWarning = expectedWarningMessage)
          formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
        }
    }
  }

  s"The consignment transfer agreement (part 1) page" should {
    s"return 403 if the GET is accessed for a non-standard consignment type" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidStandardUserKeycloakConfiguration
        )

      val transferAgreement = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
        TransferAgreementPart1Controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      }
      playStatus(transferAgreement) mustBe FORBIDDEN
    }

    s"return forbidden for a TNA user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val TransferAgreementPart1Controller =
        instantiateTransferAgreementPart1Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          getValidTNAUserKeycloakConfiguration()
        )

      val transferAgreement = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "standard")
        TransferAgreementPart1Controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      }
      playStatus(transferAgreement) mustBe FORBIDDEN
    }
  }

  private def checkForExpectedTAPart1PageContent(pageAsString: String, taAlreadyConfirmed: Boolean = true, expectedWarning: String): Unit = {
    pageAsString must include("<title>Transfer agreement (part 1) - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l govuk-!-margin-bottom-3">Transfer agreement (part 1)</h1>""")
    pageAsString must include(
      s"""|    <strong class="govuk-warning-text__text">
         |      <span class="govuk-warning-text__assistive">Warning</span>
         |      $expectedWarning
         |    </strong>""".stripMargin
    )

    if (taAlreadyConfirmed) {
      pageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued">
           |                Continue""".stripMargin
      )

    } else {
      pageAsString must include(
        """<form action="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement" method="POST" novalidate="">"""
      )
    }
  }

  private def checkForExpectedTAPart1WithLegalStatusPageContent(pageAsString: String, hasError: Boolean = false): Unit = {
    pageAsString must include("<title>Transfer agreement (part 1) - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l govuk-!-margin-bottom-3">Transfer agreement (part 1)</h1>""")
    pageAsString must include("Select the legal status of these records:")

    List("Public Record(s)", "Welsh Public Record(s)", "Not Public Record(s)").foreach { option =>
      pageAsString must include(s"""<input  class="govuk-radios__input" id="legalStatus-${option.toLowerCase.replaceAll(" ", "-")}" name="legalStatus" type="radio" value="$option"/>""")
      pageAsString must include(s"""<label class="govuk-label govuk-radios__label" for="legalStatus-${option.toLowerCase.replaceAll(" ", "-")}">
                                   |       ${option}
                                   |    </label>""".stripMargin)
    }
    pageAsString must include("""  <button data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                |    Agree and continue
                                |  </button>""".stripMargin)
    pageAsString must include("""<a class="govuk-button govuk-button--secondary" href="/homepage">Cancel transfer</a>""")

    if (hasError) {
      pageAsString must include("govuk-error-message")
      pageAsString must include("Select the legal status of these records")
    }
  }

  def instantiateTransferAgreementPart1Controller(
                                                   securityComponents: SecurityComponents,
                                                   config: Configuration,
                                                   keycloakConfiguration: KeycloakConfiguration = getValidKeycloakConfiguration,
                                                   blockLegalStatus: Boolean = true
                                                 ): TransferAgreementPart1Controller = {

    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val applicationConfig: ApplicationConfig = mock[ApplicationConfig]

    when(applicationConfig.blockLegalStatus).thenReturn(blockLegalStatus)
    new TransferAgreementPart1Controller(
      securityComponents,
      graphQLConfiguration,
      transferAgreementService,
      keycloakConfiguration,
      consignmentService,
      consignmentStatusService,
      consignmentMetadataService,
      applicationConfig
    )
  }
}
