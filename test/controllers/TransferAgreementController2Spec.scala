package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
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

class TransferAgreementController2Spec extends FrontEndTestHelper {
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

  "TransferAgreementController GET" should {

    "redirect to the TA not-compliance page with an authenticated user if consignment status is 'None'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(None, None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement2").withCSRFToken)

      playStatus(transferAgreementPage) mustBe SEE_OTHER
      headers(transferAgreementPage) mustBe TreeMap(
        "Location" -> s"/consignment/$consignmentId/transfer-agreement1",
        "Cache-Control" -> "no-store, must-revalidate"
      )
      redirectLocation(transferAgreementPage) must be(Some(s"/consignment/$consignmentId/transfer-agreement1"))
    }

    "render the TA (compliance) page with an authenticated user if consignment status is 'InProgress'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("InProgress"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement2").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(s"""<form action="/consignment/$consignmentId/transfer-agreement2" method="POST" novalidate="">""")
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user (compliance)" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getUnauthorisedSecurityComponents, app.configuration)
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement2"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "throws an authorisation exception when the user does not have permission to see a consignment's compliance transfer agreement" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val data: client.GraphqlData = client.GraphqlData(
        Some(gc.Data(None)),
        List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val consignmentId = UUID.randomUUID()
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement2").withCSRFToken)

      val failure: Throwable = transferAgreementPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "create a TA (compliance) when a valid form is submitted and the api response is successful" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val addTransferAgreementResponse: atac.AddTransferAgreementCompliance = new atac.AddTransferAgreementCompliance(
        consignmentId,
        true,
        true,
        true
      )
      taHelper.stubTAComplianceResponse(Some(addTransferAgreementResponse), app.configuration)

      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm:_*).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
    }

    "render an error when a valid (compliance) form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAComplianceResponse(config=app.configuration, errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/upload")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue
      failure mustBe an[Exception]
    }

    "throws an authorisation exception when the user does not have permission to save the compliance transfer agreement" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      taHelper.stubTAComplianceResponse(
        config=app.configuration,
        errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED")))))
      )
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)
      val completedTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm(taHelper.compliance)
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/upload")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an invalid (compliance) form (empty) is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("InProgress"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val incompleteTransferAgreementForm: Seq[(String, String)] = Seq()

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement2")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      taHelper.checkHtmlOfComplianceFormOptions.checkForOptionAndItsAttributes(transferAgreementPageAsString, incompleteTransferAgreementForm.toMap)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      taHelper.checkHtmlContentForErrorSummary(transferAgreementPageAsString, taHelper.compliance, Set())
    }

    "display errors when an invalid (compliance) form (partially complete) is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("InProgress"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("notCompliance", 2)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement2")
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

    "render the TA (compliance) 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement2").withCSRFToken)
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

    "render the TA (compliance) 'already confirmed' page with an authenticated user if user navigates back to TA page" +
      "after successfully submitting TA form that had been incorrectly submitted (empty) prior" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement2").withCSRFToken)
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

    "render the TA (compliance) 'already confirmed' page with an authenticated user if user navigates back to TA page" +
      "after successfully submitting TA form that had been incorrectly submitted (partially) prior" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = taHelper.instantiateTransferAgreement2Controller(getAuthorisedSecurityComponents, app.configuration)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      taHelper.mockGraphqlResponse(dataString)
      val incompleteTransferAgreementForm: Seq[(String, String)] = taHelper.getTransferAgreementForm("notCompliance", 1)

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement2")
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

  forAll(userChecks) { (user, url) =>
    s"The $url transfer agreement page" should {
      s"return 403 if the GET is accessed by an incorrect user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller: TransferAgreementController2 = taHelper.instantiateTransferAgreement2Controller(
          getAuthorisedSecurityComponents,
          app.configuration,
          user
        )

        val transferAgreementPage = {
          taHelper.mockGraphqlResponse(consignmentType = "judgment")
          controller.transferAgreement(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement2").withCSRFToken)
        }
        playStatus(transferAgreementPage) mustBe FORBIDDEN
      }
    }
  }
}
