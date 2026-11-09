package controllers

import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import testUtils.FrontEndTestHelper

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FileChecksFailuresControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val configuration: Configuration = mock[Configuration]
  val path = s"/admin/file-checks-failures/$consignmentId"

  "FileChecksFailuresController fileChecksFailures" should {
    "render the page with a link to download the error report for a TNA user" in {
      val result = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
        .fileChecksFailures(consignmentId)
        .apply(FakeRequest(GET, path).withCSRFToken)

      status(result) mustBe OK
      val page = contentAsString(result)
      page must include("Download the error report to identify the problem files:")
      page must include("Download error report (Excel)")
      page must include(s"$path/download")
    }

    "return forbidden for a non TNA user" in {
      val result = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
        .fileChecksFailures(consignmentId)
        .apply(FakeRequest(GET, path).withCSRFToken)

      status(result) mustBe FORBIDDEN
    }
  }

  "FileChecksFailuresController downloadFileCheckFailuresReport" should {
    "return an Excel file named with the consignment ID for a TNA user" in {
      val service = mock[FileCheckFailureService]
      val failures = List(
        FileCheckFailure(
          originalPath = "folder/test-file.txt",
          filename = "test-file.txt",
          matches = List(FileFormatMatch(Some("fmt/111"), Some("Plain Text File"))),
          statusActions = List(FileCheckStatusAction("Antivirus", "VirusDetected", "Contact TNA", "Virus detected"))
        )
      )
      when(service.getFileCheckFailures(consignmentId)).thenReturn(Future.successful(failures))

      val result = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), service)
        .downloadFileCheckFailuresReport(consignmentId)
        .apply(FakeRequest(GET, s"$path/download").withCSRFToken)

      status(result) mustBe OK
      contentType(result) mustBe Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      header("Content-Disposition", result).getOrElse("") must include(s"attachment; filename=$consignmentId-file-check-failures-")
      contentAsBytes(result).nonEmpty mustBe true
    }

    "return forbidden for a non TNA user" in {
      val result = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
        .downloadFileCheckFailuresReport(consignmentId)
        .apply(FakeRequest(GET, s"$path/download").withCSRFToken)

      status(result) mustBe FORBIDDEN
    }
  }

  private def instantiateController(
      securityComponent: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration,
      fileCheckFailureService: FileCheckFailureService = mock[FileCheckFailureService]
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    new FileChecksFailuresController(
      securityComponent,
      keycloakConfiguration,
      graphQLConfiguration,
      new ConsignmentService(graphQLConfiguration),
      new ConfirmTransferService(graphQLConfiguration),
      mock[ConsignmentExportService],
      new ConsignmentStatusService(graphQLConfiguration),
      fileCheckFailureService,
      new ApplicationConfig(configuration)
    )
  }
}
