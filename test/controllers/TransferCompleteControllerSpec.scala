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
import util.FrontEndTestHelper

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

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully" in {
      setConsignmentReferenceResponse(wiremockServer)
      val transferCompleteSubmit = callTransferComplete("consignment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
      contentAsString(transferCompleteSubmit) must include("Your records have now been transferred to The National Archives.")
      contentAsString(transferCompleteSubmit) must include(
      """    <a href="https://www.smartsurvey.co.uk/s/tdr-feedback/" class="govuk-link" rel="noreferrer noopener" target="_blank">
        What did you think of this service? (opens in new tab)""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/faq">""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/help">""")
    }

    "render the success page if the export was triggered successfully for a judgment user" in {
      setConsignmentReferenceResponse(wiremockServer)
      val transferCompleteSubmit = callTransferComplete("judgment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
      contentAsString(transferCompleteSubmit) must include("Your file has now been transferred")
      contentAsString(transferCompleteSubmit) must include("to The National Archives.")
      contentAsString(transferCompleteSubmit) must include(
        """    <a href="https://www.smartsurvey.co.uk/s/5YDPSA/" class="govuk-link" rel="noreferrer noopener" target="_blank">
        What did you think of this service? (opens in new tab)""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/judgment/faq">""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/judgment/help">""")
    }

    "downloadReport should have the correct headers and rows" in {
      val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, "standard")
      val consignmentId = UUID.randomUUID()
      mockGetConsignmentForExport()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val downloadReport = controller.downloadReport(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/download-report").withCSRFToken)

      contentAsString(downloadReport) must include(
        "Filepath,FileName,FileType,Filesize,RightsCopyright,LegalStatus,HeldBy,Language,FoiExemptionCode,LastModified"
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

      val downloadReport = controller.downloadReport(consignmentId)
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
}
