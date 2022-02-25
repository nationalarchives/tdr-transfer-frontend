package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, redirectLocation, status, _}
import services.ConsignmentService
import util.FrontEndTestHelper

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext


class UploadControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  implicit val ec: ExecutionContext = ExecutionContext.global

  "UploadController GET upload" should {
    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been signed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has been partially agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("InProgress"))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement-continued")
    }

    "show the upload page if the transfer agreement for that consignment has been agreed to in full" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("Completed"))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      contentAsString(uploadPage) must include("Uploading records")
      contentAsString(uploadPage) must include("You can only upload one folder to be transferred")
    }

    "render the upload in progress page if the upload is in progress" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("Completed"), Some("InProgress"))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      contentAsString(uploadPage) must include("Uploading records")
      contentAsString(uploadPage) must include("Your upload was interrupted and could not be completed.")
    }

    "render the upload is complete page if the upload has completed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("Completed"), Some("Completed"))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      uploadPageAsString must include("Uploading records")
      uploadPageAsString must include(
        s"""      <a href="/consignment/$consignmentId/records" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin)
    }

    "show the judgment upload page for judgments" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse()
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      contentAsString(uploadPage) must include("Upload a court judgment")
      contentAsString(uploadPage) must include("You may now upload the court judgment you wish to transfer. You can only upload one file.")
    }

    "render the judgment upload in progress page if the upload is in progress" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("Completed"), Some("InProgress"))
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      contentAsString(uploadPage) must include("Uploading court judgment")
      contentAsString(uploadPage) must include("Your upload was interrupted and could not be completed.")
    }

    "render the judgment upload is complete page if the upload has completed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService)

      stubGetConsignmentStatusResponse(Some("Completed"), Some("Completed"))
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      uploadPageAsString must include("Uploading court judgment")
      uploadPageAsString must include("Your upload is complete and has been saved")
      uploadPageAsString must include(
        s"""      <a href="/judgment/$consignmentId/records" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin)
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService)

        stubGetConsignmentStatusResponse(Some("Completed"))

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload in progress page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService)

        stubGetConsignmentStatusResponse(Some("Completed"), Some("InProgress"))

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload has completed page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService)

        stubGetConsignmentStatusResponse(Some("Completed"), Some("Completed"))

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }
  }

  private def stubGetConsignmentStatusResponse(transferAgreementStatus: Option[String] = None, uploadStatus: Option[String] = None,
                                               confirmTransferStatus: Option[String] = None)
                                              (implicit ec: ExecutionContext) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
    val data = client.GraphqlData(Option(gcs.Data(Option(gcs.GetConsignment(
      CurrentStatus(transferAgreementStatus, uploadStatus, confirmTransferStatus))))), List())
    val dataString = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val formattedJsonBody =
      """{"query":"query getConsignmentStatus($consignmentId:UUID!){
                                                       getConsignment(consignmentid:$consignmentId){
                                                         currentStatus{transferAgreement upload confirmTransfer}
                                                       }
                                                }",
                                                "variables":{
                                                  "consignmentId":"c2efd3e6-6664-4582-8c28-dcf891f60e68"
                                                }
                                       }"""
    val unformattedJsonBody = removeNewLinesAndIndentation(formattedJsonBody)

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(equalToJson(unformattedJsonBody))
      .willReturn(okJson(dataString))
    )
  }

  private def removeNewLinesAndIndentation(formattedJsonBody: String) = {
    formattedJsonBody.replaceAll("\n\\s*", "")
  }
}
