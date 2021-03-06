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

class FileChecksControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val totalFiles: Int = 40
  val consignmentId: UUID = UUID.fromString("b5bbe4d6-01a7-4305-99ef-9fce4a67917a")

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "FileChecksController GET" should {

    "render the fileChecks page with a hidden notification banner and disabled button if the checks are incomplete" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val filesProcessedWithAntivirus = 6

      val filesProcessedWithChecksum = 12
      val filesProcessedWithFFID = 8
      val dataString: String = progressData(filesProcessedWithAntivirus, filesProcessedWithChecksum, filesProcessedWithFFID, allChecksSucceeded = false)

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val recordsController = new FileChecksController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )

      val recordsPage = recordsController.recordProcessingPage(consignmentId).apply(FakeRequest(GET, s"consignment/$consignmentId/records"))
      val recordsPageAsString = contentAsString(recordsPage)

      playStatus(recordsPage) mustBe OK
      contentType(recordsPage) mustBe Some("text/html")
      recordsPageAsString must include("Checking records")
      recordsPageAsString must include("Checking records")
      recordsPageAsString must include("progress")
      recordsPageAsString must include("data-module=\"govuk-notification-banner\" hidden>")
      recordsPageAsString must include("govuk-button--disabled")

    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new FileChecksController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, consignmentService, frontEndInfoConfiguration)
      val recordsPage = controller.recordProcessingPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/records"))

      playStatus(recordsPage) mustBe FOUND
      redirectLocation(recordsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "render the notification banner and enable the button if the file checks are complete and all checks are successful" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val dataString: String = progressData(40, 40, 40, allChecksSucceeded = true)

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )
      val recordsPage = controller.recordProcessingPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/records"))
      playStatus(recordsPage) mustBe OK
      contentAsString(recordsPage) must include("""data-module="govuk-notification-banner"""")
      contentAsString(recordsPage) must not include("govuk-button--disabled")
    }

    "render the notification banner and enable the button if the file checks are complete and all checks are not successful" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val dataString: String = progressData(40, 40, 40, allChecksSucceeded = false)

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )
      val recordsPage = controller.recordProcessingPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/records"))
      playStatus(recordsPage) mustBe OK
      contentAsString(recordsPage) must include("data-module=\"govuk-notification-banner\"")
      contentAsString(recordsPage) must not include("govuk-button--disabled")
    }
  }

  private def progressData(filesProcessedWithAntivirus: Int, filesProcessedWithChecksum: Int, filesProcessedWithFFID: Int, allChecksSucceeded: Boolean): String = {
    val client = new GraphQLConfiguration(app.configuration).getClient[fileCheck.Data, fileCheck.Variables]()
    val antivirusProgress = fileCheck.GetConsignment.FileChecks.AntivirusProgress(filesProcessedWithAntivirus)
    val checksumProgress = fileCheck.GetConsignment.FileChecks.ChecksumProgress(filesProcessedWithChecksum)
    val ffidProgress = fileCheck.GetConsignment.FileChecks.FfidProgress(filesProcessedWithFFID)
    val fileChecks = fileCheck.GetConsignment.FileChecks(antivirusProgress, checksumProgress, ffidProgress)
    val data: client.GraphqlData = client.GraphqlData(Some(fileCheck.Data(Some(fileCheck.GetConsignment(allChecksSucceeded, Option(""), totalFiles, fileChecks)))))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    dataString
  }
}
