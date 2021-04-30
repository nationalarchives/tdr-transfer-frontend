package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.GetConsignment.FileChecks
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.GetConsignment.FileChecks.{AntivirusProgress, ChecksumProgress, FfidProgress}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.ConsignmentService
import util.FrontEndTestHelper
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.{Data, GetConsignment, Variables}
import io.circe.Printer
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext

class FileChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "FileChecksResultsController fileCheckResultsPage GET" should {

    "render the fileChecksResults page with the confirmation box" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)

      val data = Data(Option(GetConsignment(allChecksSucceeded = true, Option("parentFolder"), 1, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))))
      val client = graphQLConfiguration.getClient[Data, Variables ]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(fileStatusResponse)))

      val fileCheckResultsController = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        frontEndInfoConfiguration
      )

      val recordCheckResultsPage = fileCheckResultsController.fileCheckResultsPage(consignmentId).apply(
        FakeRequest(GET, s"consignment/$consignmentId/records-results")
      )
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      status(recordCheckResultsPage) mustBe 200
      contentType(recordCheckResultsPage) mustBe Some("text/html")
      resultsPageAsString must include("fileChecksResults.header")
      resultsPageAsString must include("fileChecksResults.title")
      resultsPageAsString must include("govuk-panel--confirmation")
      resultsPageAsString must include("fileChecksResults.description")
      resultsPageAsString must include("fileChecksResults.dashboard")
      resultsPageAsString must include("fileChecksResults.continueLink")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new FileChecksResultsController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        frontEndInfoConfiguration
      )
      val recordChecksResultsPage = controller.fileCheckResultsPage(consignmentId).apply(FakeRequest(GET, s"consignment/$consignmentId/records-results"))

      status(recordChecksResultsPage) mustBe FOUND
      redirectLocation(recordChecksResultsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if an authenticated user tries to get information for a consignment they don't own" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        frontEndInfoConfiguration
      )
      val exampleApiResponse = "{\"data\":{" +
        "\"getConsignment\":null}," +
        "\"errors\":[{" +
        "\"message\":\"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '0a3f617c-04e8-41c2-9f24-99622a779528'\"," +
        "\"path\":[\"getConsignment\"],\"locations\":[{" +
        "\"column\":3,\"line\":2}]," +
        "\"extensions\":{" +
        "\"code\":\"NOT_AUTHORISED\"}}]}"

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(exampleApiResponse)))

      val results: Throwable = controller.fileCheckResultsPage(consignmentId).apply(
        FakeRequest(GET, s"consignment/$consignmentId/records-results")
      ).failed.futureValue

      results.getMessage mustBe("User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '0a3f617c-04e8-41c2-9f24-99622a779528'")
    }

    "return a redirect to the error page if some file checks have failed" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)

      val data = Data(Option(GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))))
      val client = graphQLConfiguration.getClient[Data, Variables ]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(fileStatusResponse)))

      val fileCheckResultsController = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        frontEndInfoConfiguration
      )

      val recordCheckResultsPage = fileCheckResultsController.fileCheckResultsPage(consignmentId).apply(
        FakeRequest(GET, s"consignment/$consignmentId/records-results")
      )

      status(recordCheckResultsPage) mustBe SEE_OTHER
      redirectLocation(recordCheckResultsPage).get must equal(s"/consignment/$consignmentId/checks-failed")
    }
  }

  "FileChecksResultsController fileCheckResultsPage GET" should {
    "render the file checks failure page" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)

      val fileCheckResultsController = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        frontEndInfoConfiguration
      )
      val recordCheckFailurePage = fileCheckResultsController.fileCheckFailurePage(consignmentId).apply(
        FakeRequest(GET, s"consignment/$consignmentId/checks-failed")
      )
      contentAsString(recordCheckFailurePage) must include("fileChecksFailure.error.title")
    }
  }
}
