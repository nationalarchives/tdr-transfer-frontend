package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  implicit val ec: ExecutionContext = ExecutionContext.global

  "AdditionalMetadataController" should {
    "render the additional metadata start page" in {
      val parentFolder = "parentFolder"
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
      val startPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(startPageAsString, userType = "standard")

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      startPageAsString.contains(s"Folder uploaded: $parentFolder") mustBe true
      startPageAsString.contains(s"Descriptive metadata") mustBe true
      startPageAsString.contains(s"Closure metadata") mustBe true
      startPageAsString.contains(s"Continue") mustBe true
      // Will change these links when we have the metadata pages to link them to.
      startPageAsString.contains(s"""<a class="nhsuk-card__link" href="#">""") mustBe true
    }

    "will return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FORBIDDEN
    }

    "will redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller.start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "will return an error if the parent folder is missing" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, None)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata")).failed.futureValue

      response.getMessage mustBe "Parent folder not found"
    }
  }
}
