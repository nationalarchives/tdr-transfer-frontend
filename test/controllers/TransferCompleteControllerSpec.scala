package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.http.Status.FORBIDDEN
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, status}
import services.ConsignmentService
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TransferCompleteControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val wiremockExportServer = new WireMockServer(9007)

  override def beforeEach(): Unit = {
    wiremockServer.start()
    wiremockExportServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockExportServer.resetAll()
    wiremockServer.stop()
    wiremockExportServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully" in {
      setConsignmentReferenceResponse(wiremockServer)
      val transferCompletePage = callTransferComplete("consignment")
      val transferCompletePageAsString = contentAsString(transferCompletePage)

      transferCompletePageAsString must include(
      """                        <div class="govuk-panel__body govuk-!-font-size-27">
        |                        Your records have now been transferred to The National Archives.
        |                        </div>""".stripMargin
      )
      transferCompletePageAsString must include(
      """                        <div class="govuk-panel__body govuk-!-font-size-27 govuk-!-margin-top-5">
        |                            Consignment reference: <span class="govuk-!-font-weight-bold">TEST-TDR-2021-GB</span>
        |                        </div>""".stripMargin
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">We have now received your records. Please do not delete the original files you uploaded""" +
        " until you are notified that your records have been preserved.</p>"
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">We will contact you via email within 90 days.""" +
        " If you do not receive an email, please contact us.</p>"
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferCompletePageAsString)
      checkTransferCompletePageForCommonElements(transferCompletePageAsString)
    }

    "render the success page if the export was triggered successfully for a judgment user" in {
      setConsignmentReferenceResponse(wiremockServer)
      val transferCompletePage = callTransferComplete("judgment")
      val transferCompletePageAsString = contentAsString(transferCompletePage)

      transferCompletePageAsString must include(
        """                            <div class="transfer-complete">
          |                                <p>Your file has now been transferred</p>
          |                                <p>to The National Archives.</p>
          |                            </div>""".stripMargin
      )
      transferCompletePageAsString must include(
        """                        <div class="govuk-panel__body govuk-!-font-size-27 govuk-!-margin-top-5">
          |                            Transfer Reference: <span class="govuk-!-font-weight-bold">TEST-TDR-2021-GB</span>
          |                        </div>""".stripMargin
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">You will be notified by email once the judgment has been received and published.</p>"""
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">Do not delete the original file you uploaded until you have been notified.</p>"""
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferCompletePageAsString)
      checkTransferCompletePageForCommonElements(transferCompletePageAsString, survey="5YDPSA")
    }
  }

  forAll(userChecks) { (_, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        setConsignmentReferenceResponse(wiremockServer)
        val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, url)
        val consignmentId = UUID.randomUUID()
        setConsignmentTypeResponse(wiremockServer, url)

        val transferCompleteSubmit = if (url.equals("judgment")) {
          setConsignmentTypeResponse(wiremockServer, "judgment")
          controller.transferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-complete").withCSRFToken)
        } else {
          setConsignmentTypeResponse(wiremockServer, "standard")
          controller.judgmentTransferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/transfer-complete").withCSRFToken)
        }
        status(transferCompleteSubmit) mustBe FORBIDDEN
      }
    }
  }

  private def instantiateTransferCompleteController(securityComponents: SecurityComponents, path: String) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    if (path.equals("judgment")) {
      new TransferCompleteController(securityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService)
    } else {
      new TransferCompleteController(securityComponents, getValidKeycloakConfiguration, consignmentService)
    }
  }

  private def callTransferComplete(path: String): Future[Result] = {
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, path)
    val consignmentId = UUID.randomUUID()
    if (path.equals("judgment")) {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      controller.judgmentTransferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    } else {
      setConsignmentTypeResponse(wiremockServer, "standard")
      controller.transferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    }
  }

  private def checkTransferCompletePageForCommonElements(transferCompletePageAsString: String, survey: String="tdr-feedback") = {
    transferCompletePageAsString must include("<title>Transfer complete</title>")
    transferCompletePageAsString must include(
      """                        <h1 class="govuk-panel__title">
        |                            Transfer complete
        |                        </h1>""".stripMargin
    )
    transferCompletePageAsString must include(
      """                    <h2 class="govuk-heading-m govuk-!-margin-top-2">What happens next</h2>""".stripMargin
    )
    transferCompletePageAsString must include(
      s"""    <a href="https://www.smartsurvey.co.uk/s/$survey/" class="govuk-link" rel="noreferrer noopener" target="_blank">
        |        What did you think of this service? (opens in new tab)""".stripMargin
    )
    transferCompletePageAsString must include (
      """                    <a href="/homepage" role="button" draggable="false" class="govuk-button ">
        |    Return to start
        |</a>""".stripMargin
    )
  }
}
