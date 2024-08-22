package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class BeforeUploadingControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "BeforeUploadingController GET" should {
    "render the before uploading page for judgments" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val beforeUploadingController = instantiateBeforeUploadingController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val beforeUploadingPage = beforeUploadingController
        .beforeUploading(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-uploading").withCSRFToken)
      val beforeUploadingPageAsString = contentAsString(beforeUploadingPage)

      playStatus(beforeUploadingPage) mustBe OK
      contentType(beforeUploadingPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(beforeUploadingPageAsString, userType = "judgment")
      beforeUploadingPageAsString must include("""<h1 class="govuk-heading-l">Before you upload a document</h1>""")
      beforeUploadingPageAsString must include("""<h2 class="govuk-heading-m">What your document must contain</h2>""")
      beforeUploadingPageAsString must include(
        """<p class="govuk-body">We can only accept a single document in Microsoft Word (docx) format. It must contain the following information:</p>"""
      )
      beforeUploadingPageAsString must include(
        """              <ul class="govuk-list govuk-list--number">
        |                    <li>neutral citation</li>
        |                    <li>name(s) of judge(s)</li>
        |                    <li>name(s) of parties</li>
        |                    <li>court and judgment date</li>
        |                </ul>""".stripMargin
      )
      beforeUploadingPageAsString must include("""<h2 class="govuk-heading-m">How to format your document</h2>""")
      beforeUploadingPageAsString must include(
        """<p class="govuk-body">After you submit a Microsoft Word document we convert the content into a web page and PDF. To ensure the information is converted correctly, we ask you to follow some formatting guidelines and rules in the original document.</p>""".stripMargin
      )
      beforeUploadingPageAsString must include(
        """<p class="govuk-body govuk-!-margin-bottom-6">Read our <a target="_blank" class="govuk-link" href="https://nationalarchives.github.io/ds-caselaw-judiciary-guidance/">guidance on formatting your document</a>.</p>""".stripMargin
      )
      beforeUploadingPageAsString must include(
        """              <details class="govuk-details govuk-!-margin-bottom-4">
        |                    <summary class="govuk-details__summary">
        |                        <span class="govuk-details__summary-text">
        |                            How will my document look once it's published?
        |                        </span>
        |                    </summary>
        |                    <div class="govuk-details__text">
        |                        <p class="govuk-body">The web page view may look slightly different to the original document but will contain the same information. This is to make sure the document meets basic web accessibility standards.</p>
        |                        <p class="govuk-body">The PDF will be as close a copy of the original document as possible.</p>
        |                    </div>
        |                </details>""".stripMargin
      )
      beforeUploadingPageAsString must include(s"""<a href="/judgment/$consignmentId/upload" role="button" draggable="false" class="govuk-button" data-module="govuk-button">""")
    }
  }

  s"The judgment before uploading page" should {
    s"return 403 if the GET is accessed for a non-judgment consignment" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val beforeUploadingController: BeforeUploadingController = instantiateBeforeUploadingController()

      val beforeUploading = {
        setConsignmentTypeResponse(wiremockServer, "standard")
        beforeUploadingController
          .beforeUploading(consignmentId)
          .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-uploading").withCSRFToken)
      }

      playStatus(beforeUploading) mustBe FORBIDDEN
    }

    s"return forbidden for a TNA user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val beforeUploadingController: BeforeUploadingController =
        instantiateBeforeUploadingController(keycloakConfiguration = getValidTNAUserKeycloakConfiguration)

      val beforeUploading = {
        setConsignmentTypeResponse(wiremockServer, "standard")
        beforeUploadingController
          .beforeUploading(consignmentId)
          .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-uploading").withCSRFToken)
      }

      playStatus(beforeUploading) mustBe FORBIDDEN
    }
  }

  private def instantiateBeforeUploadingController(
      keycloakConfiguration: KeycloakConfiguration = getValidJudgmentUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new BeforeUploadingController(
      getAuthorisedSecurityComponents,
      new GraphQLConfiguration(app.configuration),
      getValidJudgmentUserKeycloakConfiguration,
      consignmentService
    )
  }
}
