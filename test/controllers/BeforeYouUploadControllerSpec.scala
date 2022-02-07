package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import play.api.Play.materializer
import play.api.i18n.Langs
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.ConsignmentService
import util.{EnglishLang, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class BeforeYouUploadControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val langs: Langs = new EnglishLang

  "render the before you upload page for judgments" in {
    val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
    val beforeYouUploadController = instantiateBeforeYouUploadController
    setConsignmentTypeResponse(wiremockServer, "judgment")

    val beforeYouUploadPage = beforeYouUploadController.beforeYouUpload(consignmentId)
      .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-you-upload").withCSRFToken)
    val beforeYouUploadPageAsString = contentAsString(beforeYouUploadPage)

    playStatus(beforeYouUploadPage) mustBe OK
    contentType(beforeYouUploadPage) mustBe Some("text/html")
    beforeYouUploadPageAsString must include("Please check the court judgment contains the following information:")
    beforeYouUploadPageAsString must include(s"""<a href="/judgment/$consignmentId/upload"""" +
      """ role="button" draggable="false" class="govuk-button" data-module="govuk-button">""")
  }

  forAll(userChecks) { (user, url) =>
    s"The $url transfer agreement page" should {
      s"return 403 if the GET is accessed by an incorrect user" in {
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val beforeYouUploadController: BeforeYouUploadController = instantiateBeforeYouUploadController

        val beforeYouUpload = {
          setConsignmentTypeResponse(wiremockServer, "standard")
          beforeYouUploadController.beforeYouUpload(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/before-you-upload").withCSRFToken)
        }

        playStatus(beforeYouUpload) mustBe FORBIDDEN
      }
    }
  }

  private def instantiateBeforeYouUploadController = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new BeforeYouUploadController(
      getAuthorisedSecurityComponents,
      new GraphQLConfiguration(app.configuration),
      getValidJudgmentUserKeycloakConfiguration,
      consignmentService, langs
    )
  }
}
