package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, redirectLocation, status, _}
import util.FrontEndTestHelper

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

  "UploadController GET upload" should {
    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been signed" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, frontEndInfoConfiguration)

      stubGetConsignmentStatusResponse()
      stubGetTransferAgreementResponse(Option.empty)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    // This is unlikely but it's possible that they've bypassed the checks and partially agreed to things
    "redirect to the transfer agreement page if the transfer agreement for that consignment has been partially agreed to" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, frontEndInfoConfiguration)

      stubGetConsignmentStatusResponse()
      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(false)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "show the upload page if the transfer agreement for that consignment has been agreed to in full" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, frontEndInfoConfiguration)

      stubGetConsignmentStatusResponse()
      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(true)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      contentAsString(uploadPage) must include("Uploading records")
      contentAsString(uploadPage) must include("You can only upload one folder to be transferred")
    }

    "render the upload in progress page if the upload is in progress" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, frontEndInfoConfiguration)

      stubGetConsignmentStatusResponse(Option("InProgress"))
      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(true)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      contentAsString(uploadPage) must include("Uploading records")
      contentAsString(uploadPage) must include("Your upload was interrupted and could not be completed.")
    }

    "render the upload is complete page if the upload has completed" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, frontEndInfoConfiguration)

      stubGetConsignmentStatusResponse(Option("Completed"))
      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(true)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)

      status(uploadPage) mustBe OK
      contentAsString(uploadPage) must include("Uploading records")
      contentAsString(uploadPage) must include("Your upload is complete and has been saved")
    }
  }

  private def stubGetTransferAgreementResponse(agreement: Option[itac.GetTransferAgreement])(implicit ec: ExecutionContext) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[itac.Data, itac.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(itac.Data(agreement)), List())
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val body = "{\"query\":\"query isTransferAgreementComplete($consignmentId:UUID!)" +
      "{getTransferAgreement(consignmentid:$consignmentId){isAgreementComplete}}\"," +
      "\"variables\":{\"consignmentId\":\"c2efd3e6-6664-4582-8c28-dcf891f60e68\"}}"
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))

      .withRequestBody(equalToJson(body))
      .willReturn(okJson(dataString)))
  }

  private def stubGetConsignmentStatusResponse(uploadStatus: Option[String] = None)(implicit ec: ExecutionContext) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
    val data = client.GraphqlData(Option(gcs.Data(Option(gcs.GetConsignment(CurrentStatus(uploadStatus))))), List())
    val dataString = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val body = "{\"query\":\"query getConsignmentStatus($consignmentId:UUID!){getConsignment(consignmentid:$consignmentId)" +
      "{currentStatus{upload}}}\",\"variables\":{\"consignmentId\":\"c2efd3e6-6664-4582-8c28-dcf891f60e68\"}}"

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(equalToJson(body))
      .willReturn(okJson(dataString))
    )
  }

}
