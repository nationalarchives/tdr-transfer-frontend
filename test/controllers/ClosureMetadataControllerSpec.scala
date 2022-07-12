package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import errors.GraphQlException
import graphql.codegen.GetConsignment.{getConsignment => gs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class ClosureMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val rootFolderId: UUID = UUID.randomUUID()
  val userId: UUID = UUID.fromString("327068c7-a650-4f40-8f7d-c650d5acd7b0")
  val parentFolder: Option[String] = Some("parent folder")
  val consignmentRef: String = "TEST-TDR-2021-GB"
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "ClosureMetadataController GET" should {

    "render the closure metadat page with parent folder name and consignment reference" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(
        gs.Data(Some(gs.GetConsignment(userId, None, parentFolder, consignmentRef)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(dataString)))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller = instantiateClosureMetadataController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val closureMetadatPage = controller.getClosureMetadata(consignmentId, rootFolderId).apply(FakeRequest(GET, "/closure").withCSRFToken)

      val seriesDetailsPageAsString = contentAsString(closureMetadatPage)

      playStatus(closureMetadatPage) mustBe OK
      contentType(closureMetadatPage) mustBe Some("text/html")
      seriesDetailsPageAsString must include("<title>Closure metadata</title>")

      checkForExpectedClosureMetadatPageContent(seriesDetailsPageAsString, parentFolder)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(seriesDetailsPageAsString, transferStillInProgress = false, userType = "standard")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the closure metadat page with parent folder name as blank when it is not present in the DB" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(
        gs.Data(Some(gs.GetConsignment(userId, None, None, consignmentRef)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(dataString)))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller = instantiateClosureMetadataController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val closureMetadatPage = controller.getClosureMetadata(consignmentId, rootFolderId).apply(FakeRequest(GET, "/closure").withCSRFToken)

      val seriesDetailsPageAsString = contentAsString(closureMetadatPage)

      playStatus(closureMetadatPage) mustBe OK
      contentType(closureMetadatPage) mustBe Some("text/html")
      seriesDetailsPageAsString must include("<title>Closure metadata</title>")

      checkForExpectedClosureMetadatPageContent(seriesDetailsPageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(seriesDetailsPageAsString, transferStillInProgress = false, userType = "standard")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateClosureMetadataController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val closureMetadatPage = controller.getClosureMetadata(consignmentId, rootFolderId).apply(FakeRequest(GET, "/closure").withCSRFToken)
      redirectLocation(closureMetadatPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(closureMetadatPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateClosureMetadataController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val closureMetadatPage = controller.getClosureMetadata(consignmentId, rootFolderId).apply(FakeRequest(GET, "/closure"))

      val failure = closureMetadatPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "render the error page if the token is invalid" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Body does not match", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateClosureMetadataController(getAuthorisedSecurityComponents, getInvalidKeycloakConfiguration)
      val closureMetadatPage = controller.getClosureMetadata(consignmentId, rootFolderId).apply(FakeRequest(GET, "/closure"))

      val failure = closureMetadatPage.failed.futureValue

      failure.getMessage should include("Token not provided")
    }
  }

  private def instantiateClosureMetadataController(securityComponents: SecurityComponents,
                                                   keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new ClosureMetadataController(securityComponents, keycloakConfiguration, consignmentService)
  }

  private def checkForExpectedClosureMetadatPageContent(pageAsString: String, parentFolder: Option[String] = None): Unit = {
    pageAsString must include("Folder uploaded: " + parentFolder.getOrElse(""))
    pageAsString must include(s"<a class=\"nhsuk-card__link\" href=\"/consignment/$consignmentId/additional-metadata/closure/selected-summary\">" +
      "All files and folders</a>")
    pageAsString must include(s"<a class=\"nhsuk-card__link\" href=\"/consignment/$consignmentId/additional-metadata/$rootFolderId\">" +
      "Individual files and folders</a>")
    pageAsString must include(s"href=\"/consignment/$consignmentId/additional-metadata\" role=\"button\"")
    pageAsString must include("Back to overview")
  }
}
