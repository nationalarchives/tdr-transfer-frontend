package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.http.Status.FORBIDDEN
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, status}
import services.{ConsignmentService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

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
      setConsignmentSummaryResponse(wiremockServer)
      val consignmentId = UUID.randomUUID()
      val transferCompletePage = callTransferComplete("consignment", consignmentId)
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
        """<p class="govuk-body">Your records are not yet preserved so you must not delete the original records.</p>""".stripMargin
      )
      transferCompletePageAsString must include(downloadLinkHTML(consignmentId))
      transferCompletePageAsString must include(
        """<p class="govuk-body">We will contact you via email within 90 days. If you do not receive an email, contact <a href="mailto:nationalArchives.email">nationalArchives.email</a>.</p>"""
      )
      transferCompletePageAsString must include(
        """<a href="/homepage" role="button" draggable="false" class="govuk-button ">
          |    Return to homepage
          |</a>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(
        transferCompletePageAsString,
        userType = "standard",
        transferStillInProgress = false
      )
      checkTransferCompletePageForCommonElements(transferCompletePageAsString)
      checkForNonInsetSurveyLink(transferCompletePageAsString)
    }

    "render the success page if the export was triggered successfully for a judgment user" in {
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentSummaryResponse(wiremockServer)
      val transferCompletePage = callTransferComplete("judgment")
      val transferCompletePageAsString = contentAsString(transferCompletePage)

      transferCompletePageAsString must include(
        """                            <div class="transfer-complete">
          |                                <p>Your file has now been transferred to The National Archives.</p>
          |                            </div>""".stripMargin
      )
      transferCompletePageAsString must include(
        """                        <div class="govuk-panel__body govuk-!-font-size-27 govuk-!-margin-top-5">
          |                            Transfer reference: <span class="govuk-!-font-weight-bold">TEST-TDR-2021-GB</span>
          |                        </div>""".stripMargin
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">You will be notified by email once the judgment/update has been reviewed and published.</p>"""
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">Do not delete the original file you uploaded until you have been notified.</p>"""
      )
      transferCompletePageAsString must include(
        """<a href="/homepage" role="button" draggable="false" class="govuk-button ">
          |    Return to start
          |</a>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(
        transferCompletePageAsString,
        userType = "judgment",
        transferStillInProgress = false
      )
      checkTransferCompletePageForCommonElements(transferCompletePageAsString)
      checkForSurveyLink((transferCompletePageAsString), survey = "5YDPSA")
    }
  }

  forAll(userChecks) { (_, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        setConsignmentReferenceResponse(wiremockServer)
        setConsignmentSummaryResponse(wiremockServer)
        val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, url)
        val consignmentId = UUID.randomUUID()
        setConsignmentTypeResponse(wiremockServer, url)

        val transferCompleteSubmit = if (url.equals("judgment")) {
          setConsignmentTypeResponse(wiremockServer, "judgment")
          controller
            .transferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-complete").withCSRFToken)
        } else {
          setConsignmentTypeResponse(wiremockServer, "standard")
          controller
            .judgmentTransferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/transfer-complete").withCSRFToken)
        }
        status(transferCompleteSubmit) mustBe FORBIDDEN
      }
    }
  }

  "return forbidden for a TNA user" in {
    setConsignmentReferenceResponse(wiremockServer)
    setConsignmentSummaryResponse(wiremockServer)
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, "admin")
    val consignmentId = UUID.randomUUID()
    setConsignmentTypeResponse(wiremockServer, "standard")
    controller
      .transferComplete(consignmentId)
      .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-complete").withCSRFToken)
  }

  private def instantiateTransferCompleteController(securityComponents: SecurityComponents, path: String) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val messagingService = mock[MessagingService]
    val config = new ApplicationConfig(app.configuration)

    val keycloakConfiguration = path match {
      case "judgment" => getValidJudgmentUserKeycloakConfiguration
      case "admin"    => getValidTNAUserKeycloakConfiguration()
      case _          => getValidStandardUserKeycloakConfiguration
    }
    new TransferCompleteController(securityComponents, keycloakConfiguration, consignmentService, messagingService, config)
  }

  private def callTransferComplete(path: String, consignmentId: UUID = UUID.randomUUID()): Future[Result] = {
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, path)
    if (path.equals("judgment")) {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      controller
        .judgmentTransferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    } else {
      setConsignmentTypeResponse(wiremockServer, "standard")
      controller
        .transferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    }
  }

  private def checkTransferCompletePageForCommonElements(transferCompletePageAsString: String) = {
    transferCompletePageAsString must include("<title>Transfer complete - Transfer Digital Records - GOV.UK</title>")
    transferCompletePageAsString must include(
      """                        <h1 class="govuk-panel__title">
        |                            Transfer complete
        |                        </h1>""".stripMargin
    )
    transferCompletePageAsString must include(
      """                    <h2 class="govuk-heading-m">What happens next</h2>""".stripMargin
    )
  }

  private def checkForSurveyLink(transferCompletePageAsString: String, survey: String = "tdr-feedback") = {
    transferCompletePageAsString must include(s"""<div class="govuk-inset-text">""")
    transferCompletePageAsString must include(
      s"""<a href="https://www.smartsurvey.co.uk/s/$survey/" class="govuk-link" rel="noreferrer noopener" target="_blank" title="Give your feedback on this service">
         |        Give your feedback on this service</a>
         |        (opens in new tab)""".stripMargin
    )
  }

  private def checkForNonInsetSurveyLink(transferCompletePageAsString: String, survey: String = "tdr-feedback") = {
    transferCompletePageAsString must include(
      s"""<a href="https://www.smartsurvey.co.uk/s/$survey/" class="govuk-link" rel="noreferrer noopener" target="_blank" title="What did you think of this service? (opens in new tab)">
         |    What did you think of this service? (opens in new tab)
         |    </a>""".stripMargin
    )
  }
}
