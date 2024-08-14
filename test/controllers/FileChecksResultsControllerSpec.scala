package controllers
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files.Metadata
import graphql.codegen.GetConsignmentFiles.{getConsignmentFiles => gcf}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
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
import services.Statuses.{CompletedValue, CompletedWithIssuesValue}
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class FileChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val wiremockServer = new WireMockServer(9006)
  val configuration: Configuration = mock[Configuration]
  val twoOrMoreSpaces = "\\s{2,}"

  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
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
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks").withCSRFToken)

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

    "return a redirect to transfer complete for a judgements user when transfer is completed" in {
      val fileCheckResultsController = setUpFileChecksController("judgment", getValidJudgmentUserKeycloakConfiguration)
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val recordCheckResultsPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId, Some(CompletedValue.value))
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks").withCSRFToken)
      status(recordCheckResultsPage) mustBe 303
      redirectLocation(recordCheckResultsPage).get must be(s"/judgment/$consignmentId/transfer-complete")
    }

    "return a file checks failed page for a judgements user when transfer is completed with issues" in {
      val fileCheckResultsController = setUpFileChecksController("judgment", getValidJudgmentUserKeycloakConfiguration)
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val recordCheckResultsPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId, Some(CompletedWithIssuesValue.value))
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks").withCSRFToken)
      status(recordCheckResultsPage) mustBe 200
      val resultsPageAsString = contentAsString(recordCheckResultsPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = "judgment")
      resultsPageAsString must include("<title>Results of checks - Transfer Digital Records - GOV.UK</title>")
      resultsPageAsString must include("""<h1 class="govuk-heading-l">Results of checks</h1>""")
      resultsPageAsString must include(expectedFailureTitle)
      resultsPageAsString must include("""              <p class="govuk-body">Your file has failed our checks. Please try again. If this continues, contact us at
                                         |                <a class="govuk-link" href="mailto:nationalArchives.email" data-hsupport="email">
                                         |                  nationalArchives.email
                                         |                </a>
                                         |              </p>""".stripMargin)
      resultsPageAsString must include(expectedFailureReturnButton)
    }

    "return a redirect to judgments checked passed url for a judgements user" in {
      val fileCheckResultsController = setUpFileChecksController("judgment", getValidJudgmentUserKeycloakConfiguration)

      // Export status not set
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", "Completed", someDateTime, None))
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val recordCheckResultsPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId, None)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks").withCSRFToken)
      status(recordCheckResultsPage) mustBe 303
      redirectLocation(recordCheckResultsPage).get must be(s"/judgment/$consignmentId/transfer-complete")
    }

    "render an error when export status is unknown value" in {
      val fileCheckResultsController = setUpFileChecksController("judgment", getValidJudgmentUserKeycloakConfiguration)

      // Export status not set
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Export", "aaaa", someDateTime, None))
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val recordCheckResultsPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId, None)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks").withCSRFToken)

      val failure: Throwable = recordCheckResultsPage.failed.futureValue
      failure mustBe an[Exception]
    }
  }

  forAll(userTypes) { userType =>
    "FileChecksResultsController GET" should {

      val (pathName, keycloakConfiguration, expectedTitle, expectedHeading, expectedSuccessMessage, buttonToProgress, expectedGenericErrorMessage) = if (userType == "judgment") {
        (
          "judgment",
          getValidJudgmentUserKeycloakConfiguration,
          "<title>Results of checks - Transfer Digital Records - GOV.UK</title>",
          """<h1 class="govuk-heading-l">Results of checks</h1>""",
          s"""                    <p class="govuk-body">Your uploaded file 'test file.docx' has now been validated.</p>
          |                    <p class="govuk-body">Click 'Continue' to transfer it to The National Archives.</p>""".stripMargin,
          s"""                <form method="post" action="/judgment/$consignmentId/file-checks-results">
            |                    <input type="hidden" name="csrfToken" value="[0-9a-z\\-]+"/>
            |                    <button class="govuk-button" type="submit" role="button" draggable="false">
            |                        Continue
            |                    </button>
            |                </form>""".stripMargin,
          """              <p class="govuk-body">Your file has failed our checks. Please try again. If this continues, contact us at
            |                <a class="govuk-link" href="mailto:nationalArchives.email" data-hsupport="email">
            |                  nationalArchives.email
            |                </a>
            |              </p>""".stripMargin
        )
      } else {
        (
          "consignment",
          getValidStandardUserKeycloakConfiguration,
          "<title>Results of your checks - Transfer Digital Records - GOV.UK</title>",
          """<h1 class="govuk-heading-l">Results of your checks</h1>""",
          """                    <h3 class="govuk-notification-banner__heading">
            |                        Your folder 'parentFolder' containing 1 record has been uploaded and checked.
            |                    </h3>
            |                    <p class="govuk-body">You can leave and return to this upload at any time from the <a class="govuk-notification-banner__link" href="/view-transfers">View transfers</a> page.</p>""".stripMargin,
          s"""            <a class="govuk-button" href="/consignment/$consignmentId/additional-metadata/entry-method" role="button" draggable="false" data-module="govuk-button">
             |                Next
             |            </a>""".stripMargin,
          """              <p class="govuk-body">
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
        )
      }

      s"return a redirect to the auth server if an unauthenticated user tries to access the $userType file checks page" in {
        val fileCheckResultsController = instantiateController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration)
        val recordChecksResultsPage = fileCheckResultsController
          .fileCheckResultsPage(consignmentId)
          .apply(FakeRequest(GET, s"consignment/$consignmentId/file-checks-results"))

        status(recordChecksResultsPage) mustBe FOUND
        redirectLocation(recordChecksResultsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      }

      s"return an error if an authenticated user tries to get information for a consignment they don't own from the $userType file checks page" in {
        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
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

      s"return the $userType error page if some file checks have failed" in {
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

        mockGraphqlResponse(userType, fileStatusResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, keycloakConfiguration)
        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId, None) }
          else { fileCheckResultsController.fileCheckResultsPage(consignmentId) }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        status(recordCheckResultsPage) mustBe OK

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        resultsPageAsString must include(expectedFailureTitle)
        resultsPageAsString must include(expectedGenericErrorMessage)
        resultsPageAsString must include(expectedFailureReturnButton)
      }

      s"return the passwordProtected $userType error page if file checks have failed with PasswordProtected" in {
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

        mockGraphqlResponse(userType, fileStatusResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, keycloakConfiguration)
        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId, None) }
          else { fileCheckResultsController.fileCheckResultsPage(consignmentId) }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedGenericErrorMessage)
        } else {
          resultsPageAsString must include(
            """              <p class="govuk-body govuk-!-font-weight-bold">Your folder contains one or more password protected files.</p>""" +
              """<p>We cannot accept password protected files. Once removed or replaced, try uploading your folder again.</p>"""
          )
        }

        status(recordCheckResultsPage) mustBe OK

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        resultsPageAsString must include(expectedFailureTitle)
        resultsPageAsString must include(expectedFailureReturnButton)
      }

      s"return the zip $userType error page if file checks have failed with Zip" in {
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

        mockGraphqlResponse(userType, fileStatusResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, keycloakConfiguration)
        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId, None) }
          else { fileCheckResultsController.fileCheckResultsPage(consignmentId) }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedGenericErrorMessage)
        } else {
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
        }

        status(recordCheckResultsPage) mustBe OK

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        resultsPageAsString must include(expectedFailureTitle)
        resultsPageAsString must include(expectedFailureReturnButton)
      }

      s"return the general $userType error page if file checks have failed with PasswordProtected and Zip" in {
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

        mockGraphqlResponse(userType, fileStatusResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, keycloakConfiguration)
        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId, None) }
          else { fileCheckResultsController.fileCheckResultsPage(consignmentId) }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        status(recordCheckResultsPage) mustBe OK

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        resultsPageAsString must include(expectedFailureTitle)
        resultsPageAsString must include(expectedGenericErrorMessage)
        resultsPageAsString must include(expectedFailureReturnButton)
      }
    }
  }

  forAll(consignmentStatuses) { consignmentStatus =>
    s"render the 'transfer has already been confirmed' page with an authenticated judgment user if export status is '$consignmentStatus'" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val userType = "judgment"
      val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Export", consignmentStatus, someDateTime, None))
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAlreadyCompletedPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId, None)
        .apply(FakeRequest(GET, s"/$userType/$consignmentId/file-checks").withCSRFToken)

      val transferAlreadyCompletedPageAsString = contentAsString(transferAlreadyCompletedPage)

      status(transferAlreadyCompletedPage) mustBe OK
      contentType(transferAlreadyCompletedPage) mustBe Some("text/html")
      headers(transferAlreadyCompletedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAlreadyCompletedPageAsString, userType = userType)
      transferAlreadyCompletedPageAsString must include("<title>Your transfer has already been completed - Transfer Digital Records - GOV.UK</title>")
      transferAlreadyCompletedPageAsString must include("""<h1 class="govuk-heading-l">Your transfer has already been completed</h1>""")
      transferAlreadyCompletedPageAsString must include("""<p class="govuk-body">Click 'Continue' to see the confirmation page again or return to the start.</p>""")
      transferAlreadyCompletedPageAsString must include(s"""href="/$userType/$consignmentId/transfer-complete">Continue""")
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val fileCheckResultsController = instantiateController(getAuthorisedSecurityComponents, user)
        val fileCheckResultsPage = url match {
          case "judgment" =>
            mockGraphqlResponse(consignmentType = "standard")
            fileCheckResultsController
              .judgmentFileCheckResultsPage(consignmentId, None)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks-results"))
          case "consignment" =>
            mockGraphqlResponse(consignmentType = "judgment")
            fileCheckResultsController
              .fileCheckResultsPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
        }
        status(fileCheckResultsPage) mustBe FORBIDDEN
      }
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
