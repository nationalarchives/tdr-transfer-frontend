package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, status, redirectLocation, _}
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

  "UploadController GET" should {
    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been signed" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = 1
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)

      stubGetTransferAgreementResponse(Option.empty)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    // This is unlikely but it's possible that they've bypassed the checks and partially agreed to things
    "redirect to the transfer agreement page if the transfer agreement for that consignment has been partially agreed to" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = 1
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)

      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(false)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "show the upload page if the transfer agreement for that consignment has been agreed to in full" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val consignmentId = 1
      val controller = new UploadController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)

      stubGetTransferAgreementResponse(Some(new itac.GetTransferAgreement(true)))

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe OK
    }
  }

  private def stubGetTransferAgreementResponse(agreement: Option[itac.GetTransferAgreement])(implicit ec: ExecutionContext) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[itac.Data, itac.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(itac.Data(agreement)), List())
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))
  }
}
