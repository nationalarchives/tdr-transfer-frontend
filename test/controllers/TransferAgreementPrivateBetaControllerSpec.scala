package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
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

  "TransferAgreementPrivateBetaController GET" should {

    "render the transfer agreement page with an authenticated user if consignment status is not 'InProgress' or not 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(s"""<form action="/consignment/$consignmentId/transfer-agreement" method="POST" novalidate="">""")
      transferAgreementPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getUnauthorisedSecurityComponents, app.configuration)
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "throw an authorisation exception when the user does not have permission to see a consignment's private beta transfer agreement" in {
      taHelper.mockGetConsignmentGraphqlResponse(app.configuration)

      val consignmentId = UUID.randomUUID()
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
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
        true
      )
      taHelper.stubTAPrivateBetaResponse(Some(addTransferAgreementResponse), app.configuration)

      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm:_*).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued"))
    }

    "render an error when a valid (private beta) form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAPrivateBetaResponse(config = app.configuration, errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))

      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue
      failure mustBe an[Exception]
    }

    "throw an authorisation exception when the user does not have permission to save the private beta transfer agreement" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAPrivateBetaResponse(
        config = app.configuration,
        errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
      )
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.privateBeta)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an empty private beta form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration)

      val incompleteTransferAgreementForm: Seq[(String, String)] = Seq()

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, incompleteTransferAgreementForm.toMap)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      transferAgreementPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlContentForErrorSummary(transferAgreementPageAsString, taHelper.privateBeta, Set())
    }

    "display errors when a partially complete private form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration)

      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("compliance", 2)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)
      val pageOptions: Set[String] = incompleteTransferAgreementForm.map{
        case (pageOption: String, _: String) => pageOption
      }.toSet

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, incompleteTransferAgreementForm.toMap)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      transferAgreementPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlContentForErrorSummary(transferAgreementPageAsString, taHelper.privateBeta, pageOptions)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if consignment status is 'InProgress'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("InProgress"))

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued">
           |                Continue""".stripMargin)
      transferAgreementPageAsString must include("You have already confirmed all statements")
      transferAgreementPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, formSuccessfullySubmitted = true)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued">
           |                Continue""".stripMargin)
      transferAgreementPageAsString must include("You have already confirmed all statements")
      transferAgreementPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, formSuccessfullySubmitted = true)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
      "after successfully submitting transfer agreement form having previously submitted an empty form" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

      playStatus(taAlreadyConfirmedPage) mustBe OK
      contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
      headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      taAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued">
           |                Continue""".stripMargin)
      taAlreadyConfirmedPageAsString must include("You have already confirmed all statements")
      taAlreadyConfirmedPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, formSuccessfullySubmitted = true)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to transfer agreement page" +
      "after successfully submitting transfer agreement form having previously submitted a partially complete form" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)
      taHelper.mockGetConsignmentStatusGraphqlResponse(app.configuration, Some("Completed"))

      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("compliance", 1)

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

      playStatus(taAlreadyConfirmedPage) mustBe OK
      contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
      headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      taAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement-continued">
           |                Continue""".stripMargin)
      taAlreadyConfirmedPageAsString must include("You have already confirmed all statements")
      taAlreadyConfirmedPageAsString must include (s"""" href="/faq">""")
      taHelper.checkHtmlOfPrivateBetaFormOptions.checkForOptionAndItsAttributes(taAlreadyConfirmedPageAsString, formSuccessfullySubmitted = true)
    }
  }

  s"The consignment transfer agreement page" should {
    s"return 403 if the GET is accessed by a non-standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val transferAgreementPrivateBetaController =
        taHelper.instantiateTransferAgreementPrivateBetaController(getAuthorisedSecurityComponents, app.configuration)

      val transferAgreement = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
        transferAgreementPrivateBetaController.transferAgreement(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      }
      playStatus(transferAgreement) mustBe FORBIDDEN
    }
  }
}
