package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, postRequestedFor, urlEqualTo}
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, status => playStatus, _}
import services.Statuses.{DraftMetadataType, InProgressValue}
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.FrontEndTestHelper

import java.util.UUID
import scala.concurrent.ExecutionContext

class PrepareMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "PrepareMetadataController prepareMetadata" should {
    "return OK and render the prepareMetadata view" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiatePrepareMetadataController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val prepareMetadataPage = controller
        .prepareMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/prepare-metadata").withCSRFToken)
      val prepareMetadataPageAsString = contentAsString(prepareMetadataPage)

      playStatus(prepareMetadataPage) mustBe OK
      contentType(prepareMetadataPage) mustBe Some("text/html")

      prepareMetadataPageAsString must include(s"""<a href="/consignment/$consignmentId/file-checks-results" class="govuk-back-link">Results of your checks</a>""")
      prepareMetadataPageAsString must include("""<h2 class="govuk-heading-m">Download your metadata template</h2>""")
      prepareMetadataPageAsString must include(
        s"""<a class="govuk-button govuk-button--secondary download-metadata" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">"""
      )
      prepareMetadataPageAsString must include("""<h2 class="govuk-heading-m">Saving your spreadsheet as a CSV</h2>""")
      prepareMetadataPageAsString must include("""<span class="govuk-details__summary-text">How to save an Excel file as CSV</span>""")
      prepareMetadataPageAsString must include("""                             <li>Save your file as Excel first (File > Save) before you save as CSV</li>
                                                 |                             <li>Click File > Save As
                                                 |                             <li>From the ‘Save as type’ dropdown, choose <span class="govuk-!-font-weight-bold">CSV UTF-8 (Comma delimited) (*.csv)</span></li>
                                                 |                             <li>Click Save</li>
                                                 |                             <li>Close the file, you are ready to upload</li>""".stripMargin)
      prepareMetadataPageAsString must include("""<h2 class="da-alert__heading da-alert__heading--s">
                                                 |            Leaving and returning to this transfer
                                                 |        </h2>""".stripMargin)
      prepareMetadataPageAsString must include(s"""<form action="/consignment/$consignmentId/draft-metadata/prepare-metadata" method="POST" novalidate="">""")
      prepareMetadataPageAsString must include(
        s"""<a href="/consignment/$consignmentId/additional-metadata/download-metadata" role="button" draggable="false" class="govuk-button govuk-button--secondary" data-module="govuk-button">
                                                 |                            I don't need to provide any metadata
                                                 |                        </a>""".stripMargin
      )
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiatePrepareMetadataController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val prepareMetadataPage = controller
        .prepareMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/prepare-metadata").withCSRFToken)
      redirectLocation(prepareMetadataPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(prepareMetadataPage) mustBe FOUND
    }

    "return 403 if the prepare your metadata page is accessed by a non Standard user" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiatePrepareMetadataController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val prepareMetadataPage = controller
        .prepareMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/prepare-metadata").withCSRFToken)

      status(prepareMetadataPage) mustBe FORBIDDEN
    }
  }

  "PrepareMetadataController prepareMetadataSubmit" should {
    "redirect to the draft metadata upload page and draftMetadata status is added" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAddConsignmentStatusResponse(wiremockServer, consignmentId, DraftMetadataType, InProgressValue)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val controller = instantiatePrepareMetadataController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val request = controller
        .prepareMetadataSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/prepare-metadata").withCSRFToken)

      status(request) shouldBe SEE_OTHER
      redirectLocation(request) shouldBe Some(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId).url)
      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("addConsignmentStatus"))
      )
    }
  }

  private def instantiatePrepareMetadataController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new PrepareMetadataController(keycloakConfiguration, securityComponents, consignmentService, consignmentStatusService)
  }
}
