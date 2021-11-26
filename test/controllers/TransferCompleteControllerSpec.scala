package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import io.circe.Printer
import org.pac4j.play.scala.SecurityComponents
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout}
import services.ConsignmentService
import util.FrontEndTestHelper
import graphql.codegen.GetConsignmentReference.{getConsignmentReference => gcr}
import io.circe.syntax.EncoderOps
import io.circe.generic.auto._
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

class TransferCompleteControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val wiremockExportServer = new WireMockServer(9007)

  override def beforeEach(): Unit = {
    wiremockServer.start()
    wiremockExportServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockExportServer.resetAll()
    wiremockServer.stop()
    wiremockExportServer.stop()
  }

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully" in {
      val transferCompleteSubmit = callTransferComplete("consignment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
    }
  }

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully for a judgment user" in {
      val transferCompleteSubmit = callTransferComplete("judgment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
    }
  }

  private def callTransferComplete(path: String): Future[Result] = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcr.Data, gcr.Variables]()
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents)

    val consignmentReferenceResponse: gcr.GetConsignment = getConsignmentReferenceResponse
    val data: client.GraphqlData = client.GraphqlData(Some(gcr.Data(Some(consignmentReferenceResponse))), List())
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))

    val consignmentId = UUID.randomUUID()
    controller.transferComplete(consignmentId)
      .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
  }

  private def instantiateTransferCompleteController(securityComponents: SecurityComponents) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new TransferCompleteController(securityComponents, getValidKeycloakConfiguration, consignmentService)
  }

  private def getConsignmentReferenceResponse: gcr.GetConsignment = {
    val consignmentReference = "TEST-TDR-2021-GB"
    new gcr.GetConsignment(consignmentReference)
  }
}
