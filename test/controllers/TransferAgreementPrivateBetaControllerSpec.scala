package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import org.scalatest.concurrent.ScalaFutures._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import testUtils.DefaultMockFormOptions.{MockInputOption, expectedPrivateBetaOptions}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper, TransferAgreementTestHelper}

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class TransferAgreementPrivateBetaControllerSpec extends FrontEndTestHelper {
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
    s"TransferAgreementPrivateBetaController GET with blockAdditionalMetadata $blockAdditionalMetadata" should {
      val inputOptions = if(blockAdditionalMetadata) {
        MockInputOption(
          "english",
          "I confirm that the records are all in English.",
          value = "true",
          errorMessage = "All records must be confirmed as English language before proceeding",
          fieldType = "inputCheckbox"
        ) :: expectedPrivateBetaOptions
      } else {
        expectedPrivateBetaOptions
      }
      val formOptions: FormTester = new FormTester(inputOptions, "")
      val optionsToSelectToGenerateFormErrors: Seq[Seq[(String, String)]] =
        formOptions.generateOptionsToSelectToGenerateFormErrors()
      "render the series page with an authenticated user if series status is not 'Completed'" in {
        val consignmentId = UUID.randomUUID()

        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer)
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

        playStatus(transferAgreementPage) mustBe SEE_OTHER
        redirectLocation(transferAgreementPage).get must equal(s"/consignment/$consignmentId/series")
      }

      "render the transfer agreement page with an authenticated user if consignment status is not 'InProgress' or not 'Completed'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"))
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        checkForExpectedTAPrivateBetaPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        formOptions.checkHtmlForOptionAndItsAttributes(transferAgreementPageAsString, Map())
      }

      "return a redirect to the auth server with an unauthenticated user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getUnauthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata)
          )
        val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

        redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
        playStatus(transferAgreementPage) mustBe FOUND
      }

      "throw an authorisation exception when the user does not have permission to see a consignment's private beta transfer agreement" in {
        taHelper.mockGetConsignmentGraphqlResponse(getConfig(blockAdditionalMetadata))

        val consignmentId = UUID.randomUUID()
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
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
          true,
          true,
          Option(true)
        )
        taHelper.stubTAPrivateBetaResponse(Some(addTransferAgreementResponse), getConfig(blockAdditionalMetadata))

        setConsignmentTypeResponse(wiremockServer, taHelper.userType)

        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm: _*).withCSRFToken)
        playStatus(transferAgreementSubmit) mustBe SEE_OTHER
        redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued"))
      }

      "render an error when a valid (private beta) form is submitted but there is an error from the api" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        taHelper.stubTAPrivateBetaResponse(config = getConfig(blockAdditionalMetadata), errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
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
        taHelper.stubTAPrivateBetaResponse(
          config = getConfig(blockAdditionalMetadata),
          errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
        )
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
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
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"))
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        checkForExpectedTAPrivateBetaPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
      }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        s"display errors when a partially complete private beta form (only these options: $optionsAsString selected) is submitted" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller: TransferAgreementPrivateBetaController =
            taHelper.instantiateTransferAgreementPrivateBetaController(
              getAuthorisedSecurityComponents,
              getConfig(blockAdditionalMetadata),
              getValidStandardUserKeycloakConfiguration
            )
          setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"))
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
          taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
          checkForExpectedTAPrivateBetaPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        }
      }

      "render the transfer agreement 'already confirmed' page with an authenticated user if consignment status is 'InProgress'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("InProgress"))
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString)
        checkForExpectedTAPrivateBetaPageContent(transferAgreementPageAsString)
      }

      "render the transfer agreement 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("Completed"))
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString)
        checkForExpectedTAPrivateBetaPageContent(transferAgreementPageAsString)
      }

      "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
        "after successfully submitting transfer agreement form having previously submitted an empty form" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockAdditionalMetadata), wiremockServer, seriesStatus = Some("Completed"), transferAgreementStatus = Some("Completed"))
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
        taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString)
        checkForExpectedTAPrivateBetaPageContent(taAlreadyConfirmedPageAsString)
        formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
      }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
          "after successfully submitting transfer agreement form having previously submitted a partially complete form " +
          s"(only these options: $optionsAsString selected)" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller =
            taHelper.instantiateTransferAgreementPrivateBetaController(
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
              FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
                .withFormUrlEncodedBody(incompleteTransferAgreementForm: _*)
                .withCSRFToken
            )
          val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

          playStatus(taAlreadyConfirmedPage) mustBe OK
          contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
          headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(taAlreadyConfirmedPageAsString, userType = taHelper.userType)
          taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString)
          checkForExpectedTAPrivateBetaPageContent(taAlreadyConfirmedPageAsString)
          formOptions.checkHtmlForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, Map(), formStatus = "Submitted")
        }
      }
    }

    s"The consignment transfer agreement page with blockAdditionalMetadata $blockAdditionalMetadata" should {
      s"return 403 if the GET is accessed by a non-standard user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val transferAgreementPrivateBetaController =
          taHelper.instantiateTransferAgreementPrivateBetaController(
            getAuthorisedSecurityComponents,
            getConfig(blockAdditionalMetadata),
            getValidStandardUserKeycloakConfiguration
          )

        val transferAgreement = {
          setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
          transferAgreementPrivateBetaController
            .transferAgreement(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
        }
        playStatus(transferAgreement) mustBe FORBIDDEN
      }
    }
  }


  private def checkForExpectedTAPrivateBetaPageContent(pageAsString: String, taAlreadyConfirmed: Boolean = true): Unit = {
    pageAsString must include("<title>Transfer agreement</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Transfer agreement</h1>""")
    pageAsString must include(
      """|    <strong class="govuk-warning-text__text">
         |      <span class="govuk-warning-text__assistive">Warning</span>
         |      transferAgreement.warning
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
}
