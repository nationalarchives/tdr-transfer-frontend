package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import util.{FrontEndTestHelper, TransferAgreementTestHelper}

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

  "TransferAgreementComplianceController GET" should {

    "redirect to the transfer agreement page with an authenticated user if consignment status is 'None'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
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
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("InProgress"))

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(s"""<form action="/consignment/$consignmentId/transfer-agreement-continued" method="POST" novalidate="">""")
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getUnauthorisedSecurityComponents, app.configuration)
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement-continued"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "throw an authorisation exception when the user does not have permission to see a consignment's compliance transfer agreement" in {
      taHelper.mockGetConsignmentGraphqlResponse(app.configuration)

      val consignmentId = UUID.randomUUID()
      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
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
        true
      )
      taHelper.stubTAComplianceResponse(Some(addTransferAgreementResponse), app.configuration)

      setConsignmentTypeResponse(wiremockServer, "standard")

      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("InProgress"))

      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm:_*).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
    }

    "render an error when a valid (compliance) form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAComplianceResponse(config=app.configuration, errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/upload")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue
      failure mustBe an[Exception]
    }

    "throw an authorisation exception when the user does not have permission to save the compliance transfer agreement" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAComplianceResponse(
        config=app.configuration,
        errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
      )

      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/upload")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an empty compliance form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("InProgress"))

      val incompleteTransferAgreementForm: Seq[(String, String)] = Seq()

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, incompleteTransferAgreementForm.toMap)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      taHelper.checkHtmlContentForErrorSummary(transferAgreementPageAsString, taHelper.compliance, Set())
    }

    "display errors when a partially complete compliance form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("InProgress"))

      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("privateBeta", 2)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)
      val pageOptions: Set[String] = incompleteTransferAgreementForm.map{
        case (pageOption: String, _: String) => pageOption
      }.toSet

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, incompleteTransferAgreementForm.toMap)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      taHelper.checkHtmlContentForErrorSummary(transferAgreementPageAsString, taHelper.compliance, pageOptions)
    }

    "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin)
      transferAgreementPageAsString must include("You have already confirmed all statements")
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, formSuccessfullySubmitted = true)
    }

    "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
      "after successfully submitting transfer agreement form having previously submitted an empty form" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)
      val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

      playStatus(taAlreadyConfirmedPage) mustBe OK
      contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
      headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      taAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin)
      taAlreadyConfirmedPageAsString must include("You have already confirmed all statements")
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, formSuccessfullySubmitted = true)
    }

    "render the transfer agreement (continued) 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
      "after successfully submitting transfer agreement form having previously submitted a partially complete form" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("privateBeta", 1)

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement-continued")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

      playStatus(taAlreadyConfirmedPage) mustBe OK
      contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
      headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      taAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin)
      taAlreadyConfirmedPageAsString must include("You have already confirmed all statements")
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, formSuccessfullySubmitted = true)
    }
  }

  s"The consignment transfer agreement continued page" should {
    s"return 403 if the GET is accessed by a non-standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val transferAgreementComplianceController =
        taHelper.instantiateTransferAgreementComplianceController(getAuthorisedSecurityComponents, app.configuration)

      val transferAgreement = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
        transferAgreementComplianceController.transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement-continued").withCSRFToken)
      }
      playStatus(transferAgreement) mustBe FORBIDDEN
    }
  }
}
