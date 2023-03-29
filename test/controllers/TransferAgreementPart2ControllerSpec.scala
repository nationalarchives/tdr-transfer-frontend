package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.prop.TableFor2
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import testUtils.DefaultMockFormOptions.{MockInputOption, expectedPart2Options}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper, TransferAgreementTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class TransferAgreementPart2ControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

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
  val expectedConfirmationMessage = "You must confirm all statements before proceeding. If you cannot, please close your browser and contact your transfer advisor."

  val additionalMetadataBlocks: TableFor2[Boolean, Boolean] = Table(
    ("blockClosureMetadata", "blockDescriptiveMetadata"),
    (true, true),
    (false, true),
    (true, false),
    (false, false)
  )

  forAll(additionalMetadataBlocks) { (blockClosureMetadata, blockDescriptiveMetadata) =>
    val inputOptions = if (blockClosureMetadata && blockDescriptiveMetadata) {
      MockInputOption(
        "openRecords",
        "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
        value = "true",
        errorMessage = "All records must be open",
        fieldType = "inputCheckbox"
      ) :: expectedPart2Options
    } else {
      expectedPart2Options
    }

    val formOptions: FormTester = new FormTester(inputOptions, "")
    val optionsToSelectToGenerateFormErrors: Seq[Seq[(String, String)]] =
      formOptions.generateOptionsToSelectToGenerateFormErrors()

    s"TransferAgreementPart2Controller GET with blockAdditionalMetadata $blockClosureMetadata $blockDescriptiveMetadata" should {

      "render the series page with an authenticated user if series status is not 'Completed'" in {
        val consignmentId = UUID.randomUUID()

        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        setConsignmentStatusResponse(getConfig(blockClosureMetadata, blockDescriptiveMetadata), wiremockServer)
        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentReferenceResponse(wiremockServer)

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

        playStatus(transferAgreementPage) mustBe SEE_OTHER
        redirectLocation(transferAgreementPage).get must equal(s"/consignment/$consignmentId/series")
      }

      "redirect to the transfer agreement (part 2) page with an authenticated user if consignment status is 'None'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )

        val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))
        setConsignmentStatusResponse(getConfig(blockClosureMetadata, blockDescriptiveMetadata), wiremockServer, consignmentStatuses = consignmentStatuses)
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

      "render the transfer agreement (part 2) page with an authenticated user if consignment status is 'InProgress'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None)
        )
        setConsignmentStatusResponse(
          getConfig(blockClosureMetadata, blockDescriptiveMetadata),
          wiremockServer,
          consignmentStatuses = consignmentStatuses
        )
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
        checkForExpectedTAPart2PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
      }

      "return a redirect to the auth server with an unauthenticated user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(getUnauthorisedSecurityComponents, getConfig(blockClosureMetadata, blockDescriptiveMetadata))
        val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement-continued"))

        redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
        playStatus(transferAgreementPage) mustBe FOUND
      }

      "throw an authorisation exception when the user does not have permission to see a consignment's part2 transfer agreement" in {
        taHelper.mockGetConsignmentGraphqlResponse(getConfig(blockClosureMetadata, blockDescriptiveMetadata))

        val consignmentId = UUID.randomUUID()
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(getAuthorisedSecurityComponents, getConfig(blockClosureMetadata, blockDescriptiveMetadata))

        val transferAgreementPage = controller
          .transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)

        val failure: Throwable = transferAgreementPage.failed.futureValue

        failure mustBe an[AuthorisationException]
      }

      "create a transfer agreement (part 2) when a valid form is submitted and the api response is successful" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

        val addTransferAgreementResponse: atac.AddTransferAgreementCompliance = new atac.AddTransferAgreementCompliance(
          consignmentId,
          true,
          true,
          Option(true)
        )
        taHelper.stubTAPart2Response(Some(addTransferAgreementResponse), getConfig(blockClosureMetadata, blockDescriptiveMetadata))

        val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None))
        setConsignmentTypeResponse(wiremockServer, taHelper.userType)
        setConsignmentStatusResponse(getConfig(blockClosureMetadata, blockDescriptiveMetadata), wiremockServer, consignmentStatuses = consignmentStatuses)

        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part2)
        val transferAgreementSubmit = controller
          .transferAgreementSubmit(consignmentId)
          .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm: _*).withCSRFToken)
        playStatus(transferAgreementSubmit) mustBe SEE_OTHER
        redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
      }

      "render an error when a valid (part2) form is submitted but there is an error from the api" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        taHelper.stubTAPart2Response(config = getConfig(blockClosureMetadata, blockDescriptiveMetadata), errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part2)
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

      "throw an authorisation exception when the user does not have permission to save the part2 transfer agreement" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        taHelper.stubTAPart2Response(
          config = getConfig(blockClosureMetadata, blockDescriptiveMetadata),
          errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
        )

        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(getAuthorisedSecurityComponents, getConfig(blockClosureMetadata, blockDescriptiveMetadata))
        val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.part2)
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

      "display errors when an empty part2 form is submitted" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )

        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None)
        )

        setConsignmentStatusResponse(
          getConfig(blockClosureMetadata, blockDescriptiveMetadata),
          wiremockServer,
          consignmentStatuses = consignmentStatuses
        )
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
        checkForExpectedTAPart2PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
      }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        s"display errors when a partially complete part2 form (only these options: $optionsAsString selected) is submitted" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller = taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )

          val consignmentStatuses = List(
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None)
          )

          setConsignmentStatusResponse(
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            wiremockServer,
            consignmentStatuses = consignmentStatuses
          )
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
          taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, taAlreadyConfirmed = false, expectedConfirmationMessage)
          checkForExpectedTAPart2PageContent(transferAgreementPageAsString, taAlreadyConfirmed = false)
        }
      }

      "render the transfer agreement (part 2) 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(
            getAuthorisedSecurityComponents,
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            getValidStandardUserKeycloakConfiguration
          )

        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
        )

        setConsignmentStatusResponse(
          getConfig(blockClosureMetadata, blockDescriptiveMetadata),
          wiremockServer,
          consignmentStatuses = consignmentStatuses
        )
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
        taHelper.checkForExpectedTAPageContent(transferAgreementPageAsString, confirmationMessage = expectedConfirmationMessage)
        checkForExpectedTAPart2PageContent(transferAgreementPageAsString)
      }

      "render the transfer agreement (part 2) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page " +
        "after successfully submitting transfer agreement form having previously submitted an empty form" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val controller =
            taHelper.instantiateTransferAgreementPart2Controller(
              getAuthorisedSecurityComponents,
              getConfig(blockClosureMetadata, blockDescriptiveMetadata),
              getValidStandardUserKeycloakConfiguration
            )

          val consignmentStatuses = List(
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
            ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
          )

          setConsignmentStatusResponse(
            getConfig(blockClosureMetadata, blockDescriptiveMetadata),
            wiremockServer,
            consignmentStatuses = consignmentStatuses
          )
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
          taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString, confirmationMessage = expectedConfirmationMessage)
          checkForExpectedTAPart2PageContent(taAlreadyConfirmedPageAsString)
        }

      optionsToSelectToGenerateFormErrors.foreach { optionsToSelect =>
        val optionsAsString: String = optionsToSelect.map(optionAndValue => optionAndValue._1).mkString(", ")
        "render the transfer agreement (part 2) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
          "after successfully submitting transfer agreement form having previously submitted a partially complete form " +
          s"(only these options: $optionsAsString selected)" in {
            val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
            val controller = taHelper.instantiateTransferAgreementPart2Controller(
              getAuthorisedSecurityComponents,
              getConfig(blockClosureMetadata, blockDescriptiveMetadata),
              getValidStandardUserKeycloakConfiguration
            )

            val consignmentStatuses = List(
              ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None),
              ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None)
            )

            setConsignmentStatusResponse(
              getConfig(blockClosureMetadata, blockDescriptiveMetadata),
              wiremockServer,
              consignmentStatuses = consignmentStatuses
            )
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
            taHelper.checkForExpectedTAPageContent(taAlreadyConfirmedPageAsString, confirmationMessage = expectedConfirmationMessage)
            checkForExpectedTAPart2PageContent(taAlreadyConfirmedPageAsString)
          }
      }
    }

    s"The consignment transfer agreement (part 2) page with blockAdditionalMetadata $blockClosureMetadata $blockDescriptiveMetadata" should {
      s"return 403 if the GET is accessed by a non-standard user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val TransferAgreementPart2Controller =
          taHelper.instantiateTransferAgreementPart2Controller(getAuthorisedSecurityComponents, getConfig(blockClosureMetadata, blockDescriptiveMetadata))

        val transferAgreement = {
          setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
          TransferAgreementPart2Controller
            .transferAgreement(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)
        }
        playStatus(transferAgreement) mustBe FORBIDDEN
      }
    }
  }

  private def checkForExpectedTAPart2PageContent(pageAsString: String, taAlreadyConfirmed: Boolean = true): Unit = {
    pageAsString must include("<title>Transfer agreement (part 2) - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Transfer agreement (part 2)</h1>""")
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
