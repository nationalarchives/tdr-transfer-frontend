package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import testUtils.FrontEndTestHelper

import java.util.UUID
import scala.concurrent.ExecutionContext

class DraftMetadataChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)

  private val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataChecksResultsControllerSpec GET" should {
    "render 'draft metadata checks results' page when 'blockDraftMetadataUpload' set to 'false'" in {

      val controller = instantiateController(blockDraftMetadataUpload = false)
      val additionalMetadataEntryMethodPage = controller
        .draftMetadataChecksResultsPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>[WIP] Draft Metadata - Transfer Digital Records - GOV.UK</title>")
    }

    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateController()
      val additionalMetadataEntryMethodPage = controller.draftMetadataChecksResultsPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true
  ): DraftMetadataChecksResultsController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new DraftMetadataChecksResultsController(securityComponents, keycloakConfiguration, frontEndInfoConfiguration, consignmentService, applicationConfig)
  }
}
