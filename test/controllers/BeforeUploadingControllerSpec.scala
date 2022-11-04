package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
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
      val beforeUploadingController = instantiateBeforeUploadingController
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val beforeUploadingPage = beforeUploadingController
        .beforeUploading(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-uploading").withCSRFToken)
      val beforeUploadingPageAsString = contentAsString(beforeUploadingPage)

      playStatus(beforeUploadingPage) mustBe OK
      contentType(beforeUploadingPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(beforeUploadingPageAsString, userType = "judgment")
      beforeUploadingPageAsString must include("""<h1 class="govuk-heading-l">Check your file before uploading</h1>""")
      beforeUploadingPageAsString must include(
        """<p class="govuk-body govuk-!-margin-bottom-1">Your upload must contain the following information:</p>"""
      )
      beforeUploadingPageAsString must include(
        """<p class="govuk-body govuk-!-margin-bottom-1">neutral citation, name(s) of judge(s), name(s) of parties, court and judgment date.</p>"""
      )
      // scalastyle:off line.size.limit
      beforeUploadingPageAsString must include(
        """<h2 class="govuk-heading-s">Select <a href="mailto:judgments@nationalarchives.gov.uk?subject=Ref: TEST-TDR-2021-GB">judgments@nationalarchives.gov.uk</a>"""
      )
      // scalastyle:on line.size.limit
      beforeUploadingPageAsString must include(
        """    <ul class="govuk-list govuk-list--bullet">
          |        <li>Attach and send supplementary material for this judgment.</li>
          |        <li>Flag when your judgment is a new version; quote the details of the original document being replaced.</li>
          |        <li>Flag when your judgment is subject to an anonymisation order.</li>
          |    </ul>""".stripMargin
      )
      // scalastyle:off line.size.limit
      beforeUploadingPageAsString must include(s"""<a href="/judgment/$consignmentId/upload" role="button" draggable="false" class="govuk-button" data-module="govuk-button">""")
      // scalastyle:on line.size.limit
    }
  }

  s"The judgment before uploading page" should {
    s"return 403 if the GET is accessed by a non-judgment user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val beforeUploadingController: BeforeUploadingController = instantiateBeforeUploadingController

      val beforeUploading = {
        setConsignmentTypeResponse(wiremockServer, "standard")
        beforeUploadingController
          .beforeUploading(consignmentId)
          .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-uploading").withCSRFToken)
      }

      playStatus(beforeUploading) mustBe FORBIDDEN
    }
  }

  private def instantiateBeforeUploadingController = {
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
