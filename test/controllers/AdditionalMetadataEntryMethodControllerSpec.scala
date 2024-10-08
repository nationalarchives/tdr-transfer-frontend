package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, postRequestedFor, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment => gcs}
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.Statuses.DraftMetadataType
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.FrontEndTestHelper

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala
import org.scalatest.concurrent.ScalaFutures._

class AdditionalMetadataEntryMethodControllerSpec extends FrontEndTestHelper {
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

  "AdditionalMetadataEntryMethodController GET" should {
    "render 'additional metadata entry method' page when 'blockDraftMetadataUpload' set to 'false'" in {

      val controller = instantiateController(blockDraftMetadataUpload = false)
      val additionalMetadataEntryMethodPage = controller
        .additionalMetadataEntryMethodPage(consignmentId)
        .apply(FakeRequest(GET, "/additional-metadata/entry-method").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>How would you like to enter record metadata? - Transfer Digital Records - GOV.UK</title>")
      verifyForm(pageAsString)
    }

    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateController()
      val additionalMetadataEntryMethodPage = controller.additionalMetadataEntryMethodPage(consignmentId).apply(FakeRequest(GET, "/additional-metadata/entry-method").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden for a TNA user" in {
      val controller = instantiateController(keycloakConfiguration = getValidTNAUserKeycloakConfiguration())
      val additionalMetadataEntryMethodPage = controller.additionalMetadataEntryMethodPage(consignmentId).apply(FakeRequest(GET, "/additional-metadata/entry-method").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")
      playStatus(additionalMetadataEntryMethodPage) mustBe FORBIDDEN
    }
  }

  "AdditionalMetadataEntryMethodController POST" should {
    "show an error on the page when the user submits the form without selecting any options" in {

      val controller = instantiateController(blockDraftMetadataUpload = false)

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val additionalMetadataEntryMethodPage = controller
        .submitAdditionalMetadataEntryMethod(consignmentId)
        .apply(
          FakeRequest(POST, "/additional-metadata/entry-method")
            .withFormUrlEncodedBody(Seq(): _*)
            .withCSRFToken
        )

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)
      verifyForm(pageAsString, true)
    }

    "redirect to additional metadata page when the user chooses 'manual' option to enter metadata" in {
      val controller = instantiateController(blockDraftMetadataUpload = false)

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val additionalMetadataEntryMethodPage: Result = controller
        .submitAdditionalMetadataEntryMethod(consignmentId)
        .apply(
          FakeRequest(POST, "/additional-metadata/entry-method")
            .withFormUrlEncodedBody(Seq(("metadataRoute", "manual")): _*)
            .withCSRFToken
        )
        .futureValue

      val redirectLocation = additionalMetadataEntryMethodPage.header.headers.getOrElse("Location", "")

      additionalMetadataEntryMethodPage.header.status should equal(303)
      redirectLocation must include(s"/consignment/$consignmentId/additional-metadata")
    }

    "redirect to download metadata page when the user chooses 'none' option to enter metadata" in {
      val controller = instantiateController(blockDraftMetadataUpload = false)

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val additionalMetadataEntryMethodPage: Result = controller
        .submitAdditionalMetadataEntryMethod(consignmentId)
        .apply(
          FakeRequest(POST, "/additional-metadata/entry-method")
            .withFormUrlEncodedBody(Seq(("metadataRoute", "none")): _*)
            .withCSRFToken
        )
        .futureValue

      val redirectLocation = additionalMetadataEntryMethodPage.header.headers.getOrElse("Location", "")

      additionalMetadataEntryMethodPage.header.status should equal(303)
      redirectLocation must include(s"/consignment/$consignmentId/additional-metadata/download-metadata")
    }

    "redirect to draft metadata upload page when the user chooses 'csv' option to enter metadata" in {
      val controller = instantiateController(blockDraftMetadataUpload = false)
      val someDateTime = ZonedDateTime.now()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      setAddConsignmentStatusResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = Nil)

      val additionalMetadataEntryMethodPage: Result = controller
        .submitAdditionalMetadataEntryMethod(consignmentId)
        .apply(
          FakeRequest(POST, "/additional-metadata/entry-method")
            .withFormUrlEncodedBody(Seq(("metadataRoute", "csv")): _*)
            .withCSRFToken
        )
        .futureValue

      val redirectLocation = additionalMetadataEntryMethodPage.header.headers.getOrElse("Location", "")

      additionalMetadataEntryMethodPage.header.status should equal(303)
      redirectLocation must include(s"/consignment/$consignmentId/draft-metadata/upload")

      val events = wiremockServer.getAllServeEvents
      val addConsignmentStatusEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("addConsignmentStatus")).get

      val expectedInput = s"""{"addConsignmentStatusInput":{"consignmentId":"$consignmentId","statusType":"DraftMetadata","statusValue":"InProgress"}}"""
      addConsignmentStatusEvent.getRequest.getBodyAsString must include(expectedInput)
    }

    "redirect to draft metadata upload page when the user chooses 'csv' option to enter metadata and 'draftMetadata' status already exists" in {
      val controller = instantiateController(blockDraftMetadataUpload = false)
      val someDateTime = ZonedDateTime.now()
      val consignmentStatuses = List(gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, InProgress.value, someDateTime, None))

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setUpdateConsignmentStatus(wiremockServer)

      val additionalMetadataEntryMethodPage: Result = controller
        .submitAdditionalMetadataEntryMethod(consignmentId)
        .apply(
          FakeRequest(POST, "/additional-metadata/entry-method")
            .withFormUrlEncodedBody(Seq(("metadataRoute", "csv")): _*)
            .withCSRFToken
        )
        .futureValue

      val redirectLocation = additionalMetadataEntryMethodPage.header.headers.getOrElse("Location", "")

      additionalMetadataEntryMethodPage.header.status should equal(303)
      redirectLocation must include(s"/consignment/$consignmentId/draft-metadata/upload")

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing(s"""{"updateConsignmentStatusInput":{"consignmentId":"$consignmentId","statusType":"DraftMetadata","statusValue":"InProgress"}}"""))
      )
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true
  ): AdditionalMetadataEntryMethodController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new AdditionalMetadataEntryMethodController(securityComponents, keycloakConfiguration, consignmentStatusService, consignmentService, applicationConfig)
  }

  def verifyForm(pageAsString: String, hasError: Boolean = false): Unit = {

    if (hasError) {
      pageAsString must include("""<div class="govuk-error-summary" aria-labelledby="error-summary-title" role="alert" tabindex="-1" data-module="govuk-error-summary">""")
      pageAsString must include("""<a href="#error-metadataRoute">Choose a way of entering metadata</a>""")
      pageAsString must include("""    <p class="govuk-error-message" id="error-metadataRoute">
                                  |        <span class="govuk-visually-hidden">Error:</span>
                                  |        Choose a way of entering metadata
                                  |    </p>""".stripMargin)
    }
    pageAsString must include(s"""<a href="/consignment/$consignmentId/file-checks-results" class="govuk-back-link">Results of your record checks</a>""")
    pageAsString must include("""<h1 class="govuk-fieldset__heading">
                                |                            How would you like to enter record metadata?
                                |                        </h1>""".stripMargin)
    pageAsString must include(s"""<form action="/consignment/$consignmentId/additional-metadata/entry-method" method="POST" novalidate="">""")
    pageAsString must include(
      """<input class="govuk-radios__input" id="metadata-route-manual" name="metadataRoute" type="radio" value="manual" aria-describedby="metadata-route-manual-hint">"""
    )
    pageAsString must include(
      """<input class="govuk-radios__input" id="metadata-route-csv" name="metadataRoute" type="radio" value="csv" aria-describedby="metadata-route-csv-hint">"""
    )
    pageAsString must include("""<input class="govuk-radios__input" id="metadata-route-none" name="metadataRoute" type="radio" value="none">""")
    pageAsString must include("""<button class="govuk-button" data-module="govuk-button">
                                |                Continue
                                |            </button>""".stripMargin)
  }
}
