package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import org.scalatest.concurrent.ScalaFutures._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import testUtils.DefaultMockFormOptions.{MockInputOption, expectedComplianceOptions}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper, TransferAgreementTestHelper}

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class TransferAgreementComplianceControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val taHelper = new TransferAgreementTestHelper(wiremockServer)
  val checkPageForStaticElements = new CheckPageForStaticElements

  val blockAdditionalMetadataOptions: List[Boolean] = List(true, false)
  blockAdditionalMetadataOptions.foreach { blockAdditionalMetadata =>
    val inputOptions = if (blockAdditionalMetadata) {
      MockInputOption(
        "openRecords",
        "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
        value = "true",
        errorMessage = "All records must be open",
        fieldType = "inputCheckbox"
      ) :: expectedComplianceOptions
    } else {
      expectedComplianceOptions
    }

    val formOptions: FormTester = new FormTester(inputOptions, "")
    val optionsToSelectToGenerateFormErrors: Seq[Seq[(String, String)]] =
      formOptions.generateOptionsToSelectToGenerateFormErrors()

    s"TransferAgreementComplianceController GET with blockAdditionalMetadata $blockAdditionalMetadata" should {

      "render the series page with an authenticated user if series status is not 'Completed'" in {
        val consignmentId = UUID.randomUUID()

        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer)
        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

        playStatus(transferAgreementPage) mustBe SEE_OTHER
        redirectLocation(transferAgreementPage).get must equal(s"/consignment/$consignmentId/series")
      }

      "redirect to the transfer agreement page with an authenticated user if consignment status is 'None'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"))
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued").withCSRFToken)

        playStatus(transferAgreementPage) mustBe SEE_OTHER
        headers(transferAgreementPage) mustBe TreeMap(
          "Location" -> s"/consignment/$consignmentId/transfer-agreement",
          "Cache-Control" -> "no-store, must-revalidate"
        )
        redirectLocation(transferAgreementPage) must be(Some(s"/consignment/$consignmentId/transfer-agreement"))
      }

      "render the transfer agreement (continued) page with an authenticated user if consignment status is 'InProgress'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("InProgress"))
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued").withCSRFToken)
        val transferAgreementPageAsString = contentAsString(transferAgreementPage)

        playStatus(transferAgreementPage) mustBe OK
        contentType(transferAgreementPage) mustBe Some("text/html")
        headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
        formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map())
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        checkForExpectedTACompliancePageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
      }

      "return a redirect to the auth server with an unauthenticated user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getUnauthorisedSecurityComponents, getConfig(blockAdditionalMetadata))
        val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement-continued"))

        redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
        playStatus(transferAgreementPage) mustBe FOUND
      }

      "throw an authorisation exception when the user does not have permission to see a consignment's compliance transfer agreement" in {
        taHelper.mockGetConsignmentGraphqlResponse(getConfig(blockAdditionalMetadata))

        val consignmentId = UUID.randomUUID()
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata))

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)

        val failure: Throwable = transferAgreementPage.failed.futureValue

        failure mustBe an[AuthorisationException]
      }

      "create a transfer agreement (continued) when a valid form is submitted and the api response is successful" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

        val addTransferAgreementResponse: atac.AddTransferAgreementCompliance = new atac.AddTransferAgreementCompliance(
          consignmentId,
          true,
          true,
          Option(true)
        )
        taHelper.stubTAComplianceResponse(Some(addTransferAgreementResponse), getConfig(blockAdditionalMetadata))

        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, transferAgreementStatus = Some("InProgress"))

        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm: _*).withCSRFToken)
        playStatus(transferAgreementSubmit) mustBe SEE_OTHER
        redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
      }

      "render an error when a valid (compliance) form is submitted but there is an error from the api" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        taHelper.stubTAComplianceResponse(config = getConfig(blockAdditionalMetadata), errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(
            FakeRequest(POST, f"/consignment/$consignmentId/upload")
              .withFormUrlEncodedBody(completedTransferAgreementForm: _*)
              .withCSRFToken
          )

        val failure: Throwable = transferAgreementSubmit.failed.futureValue
        failure mustBe an[Exception]
      }

      "throw an authorisation exception when the user does not have permission to save the compliance transfer agreement" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        taHelper.stubTAComplianceResponse(
          config = getConfig(blockAdditionalMetadata),
          errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
        )

        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata))
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(
            FakeRequest(POST, f"/consignment/$consignmentId/upload")
              .withFormUrlEncodedBody(completedTransferAgreementForm: _*)
              .withCSRFToken
          )

        val failure: Throwable = transferAgreementSubmit.failed.futureValue

        failure mustBe an[AuthorisationException]
      }

      "display errors when an empty compliance form is submitted" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("InProgress"))
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val incompleteTransferAgreementForm: Seq[(String, String)] = Seq()

        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(
            FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        checkForExpectedTACompliancePageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
      }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        s"display errors when a partially complete compliance form (only these options: $optionsAsString selected) is submitted" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller = taHelper.instantiateTransferAgreementComplianceController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
          setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("InProgress"))
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentReferenceResponse(wiremockServer)

          val incompleteTransferAgreementForm: Seq[(String, String)] = optionsToSelect

          val transferAgreementSubmit = controller
            .transferAgreementSubmit(consignmentId)
            .apply(
              FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
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
          taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
          checkForExpectedTACompliancePageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        }
      }

      "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata), getValidStandardUserKeycloakConfiguration)
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("Completed"))
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued").withCSRFToken)
        val transferAgreementPageAsString = contentAsString(transferAgreementPage)

        playStatus(transferAgreementPage) mustBe OK
        contentType(transferAgreementPage) mustBe Some("text/html")
        headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAgreementPageAsString, userType = taHelper.userType)
        formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map(), formStatus = "Submitted")
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString)
        checkForExpectedTACompliancePageContent(transferAgreementPageAsString)
      }

      "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
        "after successfully submitting transfer agreement form having previously submitted an empty form" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller =
            taHelper.instantiateTransferAgreementComplianceController(
              getAuthorisedSecurityComponents,
              getConfig(blockAdditionalMetadata),
              getValidStandardUserKeycloakConfiguration
            )
          setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("Completed"))
          setConsignmentTypeResponse(wiremockServer, taHelper.userType)
          setConsignmentReferenceResponse(wiremockServer)

          val taAlreadyConfirmedPage = controller
            .transferAgreementSubmit(consignmentId)
            .apply(FakeRequest(POST, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)
          val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

          playStatus(taAlreadyConfirmedPage) mustBe OK
          contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
          headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(taAlreadyConfirmedPageAsString, userType = taHelper.userType)
          formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
          taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString)
          checkForExpectedTACompliancePageContent(taAlreadyConfirmedPageAsString)
        }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
          "after successfully submitting transfer agreement form having previously submitted a partially complete form " +
          s"(only these options: $optionsAsString selected)" in {
            val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
            val controller = taHelper.instantiateTransferAgreementComplianceController(
              getAuthorisedSecurityComponents,
              getConfig(blockAdditionalMetadata),
              getValidStandardUserKeycloakConfiguration
            )
            setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("Completed"))
            setConsignmentTypeResponse(wiremockServer, taHelper.userType)
            setConsignmentReferenceResponse(wiremockServer)

            val incompleteTransferAgreementForm: Seq[(String, String)] = optionsToSelect

            val taAlreadyConfirmedPage = controller
              .transferAgreementSubmit(consignmentId)
              .apply(
                FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
                  .withFormUrlEncodedBody(incompleteTransferAgreementForm: _*)
                  .withCSRFToken
              )
            val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

            playStatus(taAlreadyConfirmedPage) mustBe OK
            contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
            headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

            checkPageForStaticElements.checkContentOfPagesThatUseMainScala(taAlreadyConfirmedPageAsString, userType = taHelper.userType)
            formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
            taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString)
            checkForExpectedTACompliancePageContent(taAlreadyConfirmedPageAsString)
          }
      }
    }

    s"The consignment transfer agreement continued page with blockAdditionalMetadata $blockAdditionalMetadata" should {
      s"return 403 if the GET is accessed by a non-standard user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val transferAgreementComplianceController =
          taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, getConfig(blockAdditionalMetadata))

        val transferAgreement = {
          setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
          transferAgreementComplianceController
            .transferAgreement(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)
        }
        playStatus(transferAgreement) mustBe FORBIDDEN
      }
    }
  }

  private def checkForExpectedTACompliancePageContent(pageAsString: String, taAlreadyConfirmed: Boolean = true): Unit = {
    pageAsString must include("<title>Transfer agreement continued</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Transfer agreement continued</h1>""")
    if (taAlreadyConfirmed) {
      pageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin
      )
    } else {
      pageAsString must include(
        """<form action="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued" method="POST" novalidate="">"""
      )
    }
  }
}
