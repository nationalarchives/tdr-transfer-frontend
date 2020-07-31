package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => fileCheck}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class RecordsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val totalFiles: Int = 15
  val consignmentId: UUID = UUID.fromString("b5bbe4d6-01a7-4305-99ef-9fce4a67917a")

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "RecordsController GET" should {

    "render the records page with progress bar after initial upload is complete and user clicks continue" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val client = new GraphQLConfiguration(app.configuration).getClient[fileCheck.Data, fileCheck.Variables]()

      val filesProcessed = 1
      val antivirusProgress = fileCheck.GetConsignment.FileChecks.AntivirusProgress(filesProcessed);
      val fileChecks = fileCheck.GetConsignment.FileChecks(antivirusProgress)
      val data: client.GraphqlData = client.GraphqlData(Some(fileCheck.Data(Some(fileCheck.GetConsignment(totalFiles, fileChecks)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val recordsController = new RecordsController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration,
        consignmentService
      )

      val recordsPage = recordsController.recordsPage(consignmentId).apply(FakeRequest(GET, s"consignment/${consignmentId}/records"))

      playStatus(recordsPage) mustBe OK
      contentType(recordsPage) mustBe Some("text/html")
      contentAsString(recordsPage) must include("checkingRecords.header")
      contentAsString(recordsPage) must include("checkingRecords.title")
      contentAsString(recordsPage) must include("progress")
    }


    "return a redirect to the auth server with an unauthenticated user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new RecordsController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, consignmentService)
      val recordsPage = controller.recordsPage(UUID.randomUUID()).apply(FakeRequest(GET, s"/consignment/${consignmentId}/records"))
      redirectLocation(recordsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(recordsPage) mustBe FOUND
    }

  }
}
