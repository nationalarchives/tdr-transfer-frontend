package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.Metadata
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gcfe}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status.FORBIDDEN
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, status}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
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
        s"""                    <p class="govuk-body">Download a printable report of the
           |                        <a class="govuk-link" href=/consignment/${consignmentId}/download-report?ref=TEST-TDR-2021-GB>
           |                            records that you have transferred with the metadata included.
           |                        </a>
           |                    </p>""".stripMargin)
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">We have now received your records. Please do not delete the original files you uploaded""" +
        " until you are notified that your records have been preserved.</p>"
      )
      transferCompletePageAsString must include(
        """                    <p class="govuk-body">We will contact you via email within 90 days.""" +
        " If you do not receive an email, please contact us.</p>"
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(
        transferCompletePageAsString,
        userType = "standard",
        transferStillInProgress=false
      )
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

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(
        transferCompletePageAsString,
        userType = "judgment",
        transferStillInProgress=false
      )
      checkTransferCompletePageForCommonElements(transferCompletePageAsString, survey="5YDPSA")
    }

    "downloadReport should have the correct headers and rows" in {
      val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, "standard")
      val consignmentId = UUID.randomUUID()
      mockGetConsignmentForExport()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val downloadReport = controller.downloadReport(consignmentId, "TEST-TDR-2021-GB")
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/download-report").withCSRFToken)

      contentAsString(downloadReport) must include(
        "Filepath,FileName,FileType,Filesize,RightsCopyright,LegalStatus,HeldBy,Language,FoiExemptionCode,LastModified,TransferInitiatedDatetime"
      )
      contentAsString(downloadReport) must include("Filepath/SomeFile,SomeFile,File,1,Crown Copyright,Public Record,TNA,English,Open")
    }

    "throw an authorisation exception when the user does not have permission to download the report" in {
      val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, "standard")
      setConsignmentTypeResponse(wiremockServer, "standard")
      val consignmentId = UUID.randomUUID()
      val client = new GraphQLConfiguration(app.configuration).getClient[gcfe.Data, gcfe.Variables]()
      val data: client.GraphqlData = client.GraphqlData(
        Some(gcfe.Data(None)),
        List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentForExport"))
        .willReturn(okJson(dataString)))

      val downloadReport = controller.downloadReport(consignmentId, "TEST-TDR-2021-GB")
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/download-report").withCSRFToken)

      val failure: Throwable = downloadReport.failed.futureValue

      failure mustBe an[AuthorisationException]
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
      new TransferCompleteController(securityComponents, getValidStandardUserKeycloakConfiguration, consignmentService)
    }
  }

  private def callTransferComplete(path: String, consignmentId: UUID = UUID.randomUUID()): Future[Result] = {
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, path)
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

  private def mockGetConsignmentForExport() = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcfe.Data, gcfe.Variables]()
    val consignmentResponse = gcfe.Data(Option(GetConsignment(UUID.randomUUID(), None, None, None, "TDR-2022", None, None, None,
      List(
        Files(UUID.randomUUID(), Some("File"), Some("SomeFile"),
          Metadata(Some(1L), None, Some("Filepath/SomeFile"), Some("Open"), Some("TNA"), Some("English"), Some("Public Record"), Some("Crown Copyright"), None),
          None, None))))
    )
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentForExport"))
      .willReturn(okJson(dataString)))
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
