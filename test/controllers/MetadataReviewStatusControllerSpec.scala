package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcs}
import io.circe.Printer
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext

class MetadataReviewStatusControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)
  val messagingService: MessagingService = mock[MessagingService]
  val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  "metadataReviewStatusPage" should {
    "render the default metadata review status page with an authenticated user when 'blockMetadataReview' set to 'false'" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "MetadataReview", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

      /*
      val consignmentSummaryResponse: gcs.GetConsignment = getConsignmentSummaryResponse
      val data: client.GraphqlData = client.GraphqlData(Some(gcs.Data(Some(consignmentSummaryResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      mockGraphqlConsignmentSummaryResponse(dataString)
      */

      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      val metadataReviewStatusPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      metadataReviewStatusPageAsString must include("<title>Metadata review - Transfer Digital Records - GOV.UK</title>")
      metadataReviewStatusPageAsString must include(s"""<a href="/consignment/$consignmentId/additional-metadata/download-metadata" class="govuk-back-link">Back</a>""")
      metadataReviewStatusPageAsString must include(
        s"""
           |          <div class="da-alert__content">
           |            <h2 class="da-alert__heading">Your review is in progress</h2>
           |            <p>When the review is complete you will receive an email to <strong>@email</strong> with further instructions.</p>
           |          </div>
           |""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewStatusPageAsString, userType = "standard")

    }

    "render page not found error when 'blockMetadataReview' set to 'true'" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      val requestMetadataReviewPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden if the page is accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      playStatus(page) mustBe FORBIDDEN
    }
  }

  private def mockGraphqlConsignmentSummaryResponse(dataString: String = "", consignmentType: String = "standard") = {
    if (dataString.nonEmpty) {
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentSummary"))
          .willReturn(okJson(dataString))
      )
    }
    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }

  private def instantiateMetadataReviewStatusController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockMetadataReview: Boolean = false
  ) = {
    when(configuration.get[Boolean]("featureAccessBlock.blockMetadataReview")).thenReturn(blockMetadataReview)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    new MetadataReviewStatusController(securityComponents, consignmentService, consignmentStatusService, keycloakConfiguration, applicationConfig)
  }

}
