package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures._
import play.api.Play.materializer
import play.api.i18n.Langs
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import services.TransferAgreementService
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import util.{EnglishLang, FrontEndTestHelper}

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class TransferAgreementControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val langs: Langs = new EnglishLang

  "TransferAgreementController GET" should {

    "render the transfer agreement page with an authenticated user if consignment status is not 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(None, None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(s"""<form action="/consignment/$consignmentId/transfer-agreement" method="POST" novalidate="">""")
      checkHtmlContentForDefaultText(transferAgreementPageAsString)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController = instantiateTransferAgreementController(getUnauthorisedSecurityComponents)
      val transferAgreementPage = controller.transferAgreement(consignmentId).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }

    "throws an authorisation exception when the user does not have permission to see a consignment's transfer agreement" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
      val data: client.GraphqlData = client.GraphqlData(
        Some(gc.Data(None)),
        List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val consignmentId = UUID.randomUUID()
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-agreement").withCSRFToken)

      val failure: Throwable = transferAgreementPage.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "create a transfer agreement when a valid form is submitted and the api response is successful" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val addTransferAgreementResponse: ata.AddTransferAgreement = new ata.AddTransferAgreement(
        consignmentId,
        true,
        true,
        true,
        true,
        true,
        true
      )
      stubTransferAgreementResponse(Some(addTransferAgreementResponse))

      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)
      val completedTransferAgreementForm: Seq[(String, String)] = getTransferAgreementForm()
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest().withFormUrlEncodedBody(completedTransferAgreementForm:_*).withCSRFToken)
      playStatus(transferAgreementSubmit) mustBe SEE_OTHER
      redirectLocation(transferAgreementSubmit) must be(Some("/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload"))
    }

    "render an error when a valid form is submitted but there is an error from the api" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      stubTransferAgreementResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)
      val completedTransferAgreementForm: Seq[(String, String)] = getTransferAgreementForm()
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue
      failure mustBe an[Exception]
    }

    "throws an authorisation exception when the user does not have permission to save the transfer agreement" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      stubTransferAgreementResponse(errors = List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)
      val completedTransferAgreementForm: Seq[(String, String)] = getTransferAgreementForm()
      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(completedTransferAgreementForm:_*)
          .withCSRFToken)

      val failure: Throwable = transferAgreementSubmit.failed.futureValue

      failure mustBe an[AuthorisationException]
    }

    "display errors when an invalid form (empty) is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(None, None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      checkHtmlContentForDefaultText(transferAgreementPageAsString)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      checkHtmlContentForErrorSummary(transferAgreementPageAsString, Set())
    }

    "display errors when an invalid form (partially complete) is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(None, None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))
      val incompleteTransferAgreementForm: Seq[(String, String)] = getTransferAgreementForm(4)

      val transferAgreementSubmit = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
          .withFormUrlEncodedBody(incompleteTransferAgreementForm:_*)
          .withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementSubmit)
      val pageOptions: Set[String] = incompleteTransferAgreementForm.map{
        case (pageOption: String, _: String) => pageOption
      }.toSet

      playStatus(transferAgreementSubmit) mustBe BAD_REQUEST
      checkHtmlContentForDefaultText(transferAgreementPageAsString)
      transferAgreementPageAsString must include("govuk-error-message")
      transferAgreementPageAsString must include("error")
      checkHtmlContentForErrorSummary(transferAgreementPageAsString, pageOptions)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if consignment status is 'Completed'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller: TransferAgreementController = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))
      val transferAgreementPage = controller.transferAgreement(consignmentId)
        .apply(FakeRequest(GET, "/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/transfer-agreement").withCSRFToken)
      val transferAgreementPageAsString = contentAsString(transferAgreementPage)

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      headers(transferAgreementPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAgreementPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin)
      transferAgreementPageAsString must include("You have already confirmed all statements")
      checkHtmlContentForDefaultText(transferAgreementPageAsString)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to TA page" +
      "after successfully submitting TA form that had been incorrectly submitted (empty) prior" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement").withCSRFToken)
      val taAlreadyConfirmedPageAsString = contentAsString(taAlreadyConfirmedPage)

      playStatus(taAlreadyConfirmedPage) mustBe OK
      contentType(taAlreadyConfirmedPage) mustBe Some("text/html")
      headers(taAlreadyConfirmedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      taAlreadyConfirmedPageAsString must include(
        s"""href="/consignment/c2efd3e6-6664-4582-8c28-dcf891f60e68/upload">
           |                Continue""".stripMargin)
      taAlreadyConfirmedPageAsString must include("You have already confirmed all statements")
      checkHtmlContentForDefaultText(taAlreadyConfirmedPageAsString)
    }

    "render the transfer agreement 'already confirmed' page with an authenticated user if user navigates back to TA page" +
      "after successfully submitting TA form that had been incorrectly submitted (partially) prior" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateTransferAgreementController(getAuthorisedSecurityComponents)

      val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
      val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(Some("Completed"), None))))
      val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))
      val incompleteTransferAgreementForm: Seq[(String, String)] = getTransferAgreementForm(3)

      val taAlreadyConfirmedPage = controller.transferAgreementSubmit(consignmentId)
        .apply(FakeRequest(POST, f"/consignment/$consignmentId/transfer-agreement")
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
      checkHtmlContentForDefaultText(taAlreadyConfirmedPageAsString)
    }
  }

  private def getTransferAgreementForm(numberOfValuesToRemove: Int=0): Seq[(String, String)] = {
    val value = "true"
    Seq(
      ("publicRecord", value),
      ("crownCopyright", value),
      ("english", value),
      ("droAppraisalSelection", value),
      ("droSensitivity", value),
      ("openRecords", value)
    ).dropRight(numberOfValuesToRemove)
  }

  private def checkHtmlContentForDefaultText(htmlAsString: String) = {
    val defaultLinesOfTextOnPage = Set(
      "Transfer agreement",
      "I confirm that the records are Public Records.",
      "I confirm that the records are all Crown Copyright.",
      "I confirm that the records are all in English.",
      "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection",
      "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review.",
      "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records."
    )

    defaultLinesOfTextOnPage.foreach(defaultLineOfTextOnPage => htmlAsString must include(defaultLineOfTextOnPage))
  }

  private def checkHtmlContentForErrorSummary(htmlAsString: String, optionsSelected: Set[String]): Unit = {
    val potentialErrorsOnPage = Map(
      ("publicRecord" -> "All records must be confirmed as public before proceeding"),
      ("crownCopyright" -> "All records must be confirmed Crown Copyright before proceeding"),
      ("english" -> "All records must be confirmed as English language before proceeding"),
      ("droAppraisalSelection" -> "Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records"),
      ("droSensitivity" -> "Departmental Records Officer (DRO) must have signed off sensitivity review"),
      ("openRecords" -> "All records must be open")
    )

    val errorsThatShouldBeOnPage: Map[String, String] = potentialErrorsOnPage.filter {
      case (errorName, _) => !optionsSelected.contains(errorName)
    }

    errorsThatShouldBeOnPage.values.foreach(error => htmlAsString must include(error))
  }

  private def instantiateTransferAgreementController(securityComponents: SecurityComponents) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)

    new TransferAgreementController(securityComponents, new GraphQLConfiguration(app.configuration),
      transferAgreementService, getValidKeycloakConfiguration, langs)
  }

  private def stubTransferAgreementResponse(transferAgreement: Option[ata.AddTransferAgreement] = None, errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[ata.Data, ata.Variables]()

    val data: client.GraphqlData =
      client.GraphqlData(
        transferAgreement.map(ta => ata.Data(ta)),  // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
        errors
      )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))
  }
}
