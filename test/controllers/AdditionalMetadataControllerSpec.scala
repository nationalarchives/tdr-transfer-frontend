package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient.Error
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import io.circe.syntax._
import io.circe.generic.auto._

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
      val parentFolderId = UUID.randomUUID()
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = Option(parentFolderId))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
      val startPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(startPageAsString, userType = "standard")

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      startPageAsString must include(s"""        <h1 class="govuk-heading-l">Add, edit or delete metadata</h1>""")

      startPageAsString must include(s"""        <h2 class="govuk-heading-s">Folder uploaded: $parentFolder""")
      // scalastyle:off line.size.limit
      startPageAsString must include(
        s"""        <p class="govuk-body">You can now add or edit closure and descriptive metadata to your records. Or you can proceed without by clicking ‘Continue’ at the bottom of this page.</p>""".stripMargin
      )
      startPageAsString must include(
        s"""        <p class="govuk-body">If you'd like to add or edit descriptive metadata to your records, you can do so here.</p>"""
      )
      startPageAsString must include(
        s"""        <p class="govuk-body">If you'd like to add or edit closure metadata to your records, you can do so here.</p>""".stripMargin
      )
      startPageAsString must include(
        s"""        <a href="/consignment/$consignmentId/additional-metadata/download-metadata" """ +
          """role="button" draggable="false" class="govuk-button" data-module="govuk-button">""" + """
                                                                                                     |          Continue
                                                                                                     |        </a>""".stripMargin
      )
      startPageAsString must include(
        s"""<a class="govuk-link govuk-link--no-visited-state" href="/consignment/$consignmentId/additional-metadata/files/descriptive">"""
      )
      startPageAsString must include(
        s"""<a class="govuk-link govuk-link--no-visited-state" href="/consignment/$consignmentId/additional-metadata/files/closure">"""
      )
      // scalastyle:on line.size.limit
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if the parent folder name and id are missing" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = None)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
        .failed
        .futureValue

      response.getMessage mustBe "Parent folder not found"
    }

    "return an error if the parent folder name is missing" in {
      val consignmentId = UUID.randomUUID()
      val parentFolderId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = Option(parentFolderId))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
        .failed
        .futureValue

      response.getMessage mustBe "Parent folder not found"
    }

    "return an error if the parent folder id is missing" in {
      val consignmentId = UUID.randomUUID()
      val parentFolder = "parentFolder"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
        .failed
        .futureValue

      response.getMessage mustBe "Parent folder not found"
    }
  }
}
