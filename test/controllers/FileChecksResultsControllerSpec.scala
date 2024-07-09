package controllers
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, ok, okJson, post, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files.Metadata
import graphql.codegen.GetConsignmentFiles.{getConsignmentFiles => gcf}
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.GetConsignment.FileChecks
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.GetConsignment.FileChecks.{AntivirusProgress, ChecksumProgress, FfidProgress}
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress.{Data, GetConsignment, Variables}
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => gfcp}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.test.WsTestClient.InternalWSClient
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext

class FileChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val wiremockServer = new WireMockServer(9006)
  val wiremockExportServer = new WireMockServer(9007)
  val configuration: Configuration = mock[Configuration]
  val twoOrMoreSpaces = "\\s{2,}"

  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  override def beforeEach(): Unit = {
    wiremockServer.start()
    wiremockExportServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
    wiremockExportServer.resetAll()
    wiremockExportServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  val warningMsg =
    "Now that your records have been uploaded you can proceed with the transfer. In the next step you will be given the opportunity to add metadata to your records before transferring them."
  val expectedSuccessSummaryTitle: String =
    """                    <h2 class="govuk-notification-banner__title" id="govuk-notification-banner-title">
      |                        Success
      |                    </h2>""".stripMargin
  val expectedSuccessWarningText: String => String = (warningMsg: String) => s"""            <div class="govuk-warning-text">
      |                <span class="govuk-warning-text__icon" aria-hidden="true">!</span>
      |                <strong class="govuk-warning-text__text">
      |                    <span class="govuk-warning-text__assistive">Warning</span>
      |                    $warningMsg</strong>
      |            </div>""".stripMargin
  val expectedFailureReturnButton: String =
    """      <a href="/homepage" role="button" draggable="false" class="govuk-button govuk-button--primary">
      |          Return to start
      |      </a>""".stripMargin

  val expectedFailureTitle: String =
    """          <h2 class="govuk-error-summary__title" id="error-summary-title">
      |              There is a problem
      |          </h2>""".stripMargin
  val expectedTitle = "<title>Results of your checks - Transfer Digital Records - GOV.UK</title>"
  val expectedHeading = """<h1 class="govuk-heading-l">Results of your checks</h1>"""
  val expectedGenericErrorMessage = """              <p class="govuk-body">
                                      |    One or more files you uploaded have failed our checks. Contact us at
                                      |    <a class="govuk-link" href="mailto:nationalArchives.email?subject=Ref: TEST-TDR-2021-GB - Problem with Results of checks">nationalArchives.email</a>
                                      |    if the problem persists.
                                      |</p>
                                      |<p class="govuk-body">Possible failure causes:</p>
                                      |<ul class="govuk-list govuk-list--bullet">
                                      |    <li>Password protected files</li>
                                      |    <li>Zip files</li>
                                      |    <li>Corrupted files</li>
                                      |    <li>Ambiguous naming of redacted files</li>
                                      |</ul>""".stripMargin

  "FileChecksResultsController GET after file check success" should {
    "render the fileChecksResults page with the confirmation box for a standard user" in {

      val expectedSuccessMessage: String =
        s"""                    <h3 class="govuk-notification-banner__heading">
           |                        Your folder 'parentFolder' containing 1 record has been uploaded and checked.
           |                    </h3>
           |                    <p class="govuk-body">You can leave and return to this upload at any time from the <a class="govuk-notification-banner__link" href="/view-transfers">View transfers</a> page.</p>""".stripMargin

      val buttonToProgress: String =
        s"""            <a class="govuk-button" href="/consignment/$consignmentId/additional-metadata/entry-method" role="button" draggable="false" data-module="govuk-button">
           |                Next
           |            </a>""".stripMargin

      val fileCheckResultsController = setUpFileChecksController("standard", getValidStandardUserKeycloakConfiguration)

      val recordCheckResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results").withCSRFToken)

      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      status(recordCheckResultsPage) mustBe 200
      contentType(recordCheckResultsPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "standard")
      resultsPageAsString must include("<title>Results of your checks - Transfer Digital Records - GOV.UK</title>")
      resultsPageAsString must include("""<h1 class="govuk-heading-l">Results of your checks</h1>""")
      resultsPageAsString must include(expectedSuccessSummaryTitle)
      resultsPageAsString.replaceAll(twoOrMoreSpaces, "") must include(expectedSuccessWarningText(warningMsg).replaceAll(twoOrMoreSpaces, ""))

      resultsPageAsString must include(expectedSuccessMessage)
      resultsPageAsString must include regex buttonToProgress
    }
  }

  "FileChecksResultsController GET" should {

    s"return a redirect to the auth server if an unauthenticated user tries to access the standard file checks page" in {
      val fileCheckResultsController = instantiateController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val recordChecksResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"consignment/$consignmentId/file-checks-results"))

      status(recordChecksResultsPage) mustBe FOUND
      redirectLocation(recordChecksResultsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    s"return an error if an authenticated user tries to get information for a consignment they don't own from the standard file checks page" in {
      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val exampleApiResponse = "{\"fileChecksData\":{" +
        "\"getConsignment\":null}," +
        "\"errors\":[{" +
        "\"message\":\"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '0a3f617c-04e8-41c2-9f24-99622a779528'\"," +
        "\"path\":[\"getConsignment\"],\"locations\":[{" +
        "\"column\":3,\"line\":2}]," +
        "\"extensions\":{" +
        "\"code\":\"NOT_AUTHORISED\"}}]}"

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(exampleApiResponse))
      )

      val results: Throwable = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(
          FakeRequest(GET, s"consignment/$consignmentId/file-checks-results")
        )
        .failed
        .futureValue

      results.getMessage mustBe "User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '0a3f617c-04e8-41c2-9f24-99622a779528'"
    }

    s"return the standard error page if some file checks have failed" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)

      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val fileStatus = List(gfcp.GetConsignment.Files(Some("fileStatusValue")))

      val data = Data(
        Option(
          GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
        )
      )
      val client = graphQLConfiguration.getClient[Data, Variables]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      mockGraphqlResponse("standard", fileStatusResponse)
      setConsignmentReferenceResponse(wiremockServer)

      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val recordCheckResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      status(recordCheckResultsPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "standard")
      resultsPageAsString must include(expectedTitle)
      resultsPageAsString must include(expectedHeading)
      resultsPageAsString must include(expectedFailureTitle)
      resultsPageAsString must include(expectedGenericErrorMessage)
      resultsPageAsString must include(expectedFailureReturnButton)
    }

    s"return the passwordProtected standard error page if file checks have failed with PasswordProtected" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val fileStatus = List(gfcp.GetConsignment.Files(Some("PasswordProtected")))

      val data = Data(
        Option(
          GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
        )
      )
      val client = graphQLConfiguration.getClient[Data, Variables]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      mockGraphqlResponse("standard", fileStatusResponse)
      setConsignmentReferenceResponse(wiremockServer)

      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val recordCheckResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      resultsPageAsString must include(
        """              <p class="govuk-body govuk-!-font-weight-bold">Your folder contains one or more password protected files.</p>""" +
          """<p>We cannot accept password protected files. Once removed or replaced, try uploading your folder again.</p>"""
      )

      status(recordCheckResultsPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "standard")
      resultsPageAsString must include(expectedTitle)
      resultsPageAsString must include(expectedHeading)
      resultsPageAsString must include(expectedFailureTitle)
      resultsPageAsString must include(expectedFailureReturnButton)
    }

    s"return the zip standard error page if file checks have failed with Zip" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val fileStatus = List(gfcp.GetConsignment.Files(Some("Zip")))

      val data = Data(
        Option(
          GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
        )
      )
      val client = graphQLConfiguration.getClient[Data, Variables]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      mockGraphqlResponse("standard", fileStatusResponse)
      setConsignmentReferenceResponse(wiremockServer)

      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val recordCheckResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      resultsPageAsString must include(
        """              <p class="govuk-body govuk-!-font-weight-bold">Your folder contains one or more zip files.</p><p>
            |                We cannot accept zip files and similar archival package file formats.
            |                These commonly have file extensions such as .zip, .iso, .7z, .rar and others.
            |                please see our
            |                <a class="govuk-link" href="/faq" target="_blank" rel="noreferrer noopener">
            |                FAQ(Opens in new tab)
            |                </a>
            |                for a full list.
            |                Either remove or unpack your zip and archival package files and try uploading again.
            |                </p>""".stripMargin
      )

      status(recordCheckResultsPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "standard")
      resultsPageAsString must include(expectedTitle)
      resultsPageAsString must include(expectedHeading)
      resultsPageAsString must include(expectedFailureTitle)
      resultsPageAsString must include(expectedFailureReturnButton)
    }

    s"return the general standard error page if file checks have failed with PasswordProtected and Zip" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      val fileStatus = List(gfcp.GetConsignment.Files(Some("PasswordProtected")), gfcp.GetConsignment.Files(Some("Zip")))

      val data = Data(
        Option(
          GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
        )
      )
      val client = graphQLConfiguration.getClient[Data, Variables]()
      val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

      mockGraphqlResponse("standard", fileStatusResponse)
      setConsignmentReferenceResponse(wiremockServer)

      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val recordCheckResultsPage = fileCheckResultsController
        .fileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      status(recordCheckResultsPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "standard")
      resultsPageAsString must include(expectedTitle)
      resultsPageAsString must include(expectedHeading)
      resultsPageAsString must include(expectedFailureTitle)
      resultsPageAsString must include(expectedGenericErrorMessage)
      resultsPageAsString must include(expectedFailureReturnButton)
    }

    s"return 403 if the url doesn't match the consignment type" in {
      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      mockGraphqlResponse(consignmentType = "judgment")
      val fileCheckResultsPage =
        fileCheckResultsController
          .fileCheckResultsPage(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))

      status(fileCheckResultsPage) mustBe FORBIDDEN
    }
  }

  private def mockGraphqlResponse(consignmentType: String, fileStatusResponse: String = "", filePathResponse: String = "") = {
    if (consignmentType == "judgment") {
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFiles"))
          .willReturn(okJson(filePathResponse))
      )
    }
    if (fileStatusResponse.nonEmpty) {
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getFileCheckProgress"))
          .willReturn(okJson(fileStatusResponse))
      )
    }
    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }

  private def instantiateController(
      securityComponent: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = false
  ) = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val confirmTransferService = new ConfirmTransferService(graphQLConfiguration)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    new FileChecksResultsController(
      securityComponent,
      keycloakConfiguration,
      graphQLConfiguration,
      consignmentService,
      confirmTransferService,
      exportService(app.configuration),
      consignmentStatusService,
      applicationConfig
    )
  }
  def exportService(configuration: Configuration): ConsignmentExportService = {
    val wsClient = new InternalWSClient("http", 9007)
    new ConsignmentExportService(wsClient, configuration, new GraphQLConfiguration(configuration))
  }
  def setUpFileChecksController(consignmentType: String, keyCloakConfig: KeycloakConfiguration): FileChecksResultsController = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)

    setConsignmentStatusResponse(app.configuration, wiremockServer)
    val fileStatus = List(gfcp.GetConsignment.Files(Some("Success")))

    val fileChecksData = gfcp.Data(
      Option(
        GetConsignment(allChecksSucceeded = true, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
      )
    )

    val filePathData = gcf.Data(
      Option(gcf.GetConsignment(List(Files(UUID.randomUUID(), Option(""), Option(""), Option(UUID.randomUUID()), Metadata(Some("test file.docx")), Nil))))
    )

    val getFileChecksProgressClient = graphQLConfiguration.getClient[gfcp.Data, gfcp.Variables]()
    val getConsignmentFilesClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables]()
    val fileStatusResponse: String =
      getFileChecksProgressClient.GraphqlData(Option(fileChecksData), List()).asJson.printWith(Printer(dropNullValues = false, ""))
    val filePathResponse: String =
      getConsignmentFilesClient.GraphqlData(Option(filePathData), List()).asJson.printWith(Printer(dropNullValues = false, ""))

    mockGraphqlResponse(consignmentType, fileStatusResponse, filePathResponse)
    setConsignmentReferenceResponse(wiremockServer)

    instantiateController(getAuthorisedSecurityComponents, keyCloakConfig)

  }
}
