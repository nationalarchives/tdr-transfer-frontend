package controllers
import scala.jdk.CollectionConverters._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}
import configuration.{ApplicationConfig, GraphQLConfiguration}
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
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class FileChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val wiremockServer = new WireMockServer(9006)
  val configuration: ApplicationConfig = {
    val config: Map[String, ConfigValue] = ConfigFactory
      .load()
      .withValue("featureAccessBlock.closureMetadata", ConfigValueFactory.fromAnyRef("false"))
      .withValue("featureAccessBlock.descriptiveMetadata", ConfigValueFactory.fromAnyRef("false"))
      .entrySet()
      .asScala
      .map(e => e.getKey -> e.getValue)
      .toMap
    new ApplicationConfig(Configuration.from(config))
  }

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  val expectedSuccessSummaryTitle: String =
    """                <h2 class="success-summary__title" id="success-summary-title">
      |                    Success
      |                </h2>""".stripMargin
  val expectedFailureReturnButton: String =
    """      <a href="/homepage" role="button" draggable="false" class="govuk-button govuk-button--primary">
      |          Return to start
      |      </a>""".stripMargin

  val expectedFailureTitle: String =
    """          <h2 class="govuk-error-summary__title" id="error-summary-title">
      |              There is a problem
      |          </h2>""".stripMargin

  forAll(userTypes) { userType =>
    "FileChecksResultsController GET" should {

      val (pathName, keycloakConfiguration, expectedTitle, expectedHeading, expectedSuccessMessage, buttonToProgress, expectedGenericErrorMessage) = if (userType == "judgment") {
        (
          "judgment",
          getValidJudgmentUserKeycloakConfiguration,
          "<title>Results of checks</title>",
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
        // scalastyle:off line.size.limit
        (
          "consignment",
          getValidStandardUserKeycloakConfiguration,
          "<title>Results of your checks</title>",
          """<h1 class="govuk-heading-l">Results of your checks</h1>""",
          """                    <p class="govuk-body">Your folder 'parentFolder' containing 1 item has been successfully checked and uploaded.</p>
            |                    <p class="govuk-body">Click 'Continue' to proceed with your transfer.</p>""".stripMargin,
          s"""                <a class="govuk-button" href="/consignment/0a3f617c-04e8-41c2-9f24-99622a779528/additional-metadata" role="button" draggable="false" data-module="govuk-button">
             |                    Continue
             |                </a>""".stripMargin,
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
            |</ul>""".stripMargin
        )
        // scalastyle:on line.size.limit
      }

      s"render the $userType fileChecksResults page with the confirmation box and the continue button link to 'confirm-transfer' page if addition metadata features are blocked" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("Success")))

        val confirmTransferButton =
          s"""                <a class="govuk-button" href="/consignment/0a3f617c-04e8-41c2-9f24-99622a779528/confirm-transfer" role="button" draggable="false" data-module="govuk-button">
             |                    Continue
             |                </a>""".stripMargin

        val fileChecksData = gfcp.Data(
          Option(
            GetConsignment(allChecksSucceeded = true, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
          )
        )

        val filePathData = gcf.Data(
          Option(gcf.GetConsignment(List(Files(UUID.randomUUID(), Option(""), Option(""), Option(UUID.randomUUID()), Metadata(Some("test file.docx"))))))
        )

        val getFileChecksProgressClient = graphQLConfiguration.getClient[gfcp.Data, gfcp.Variables]()
        val getConsignmentFilesClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables]()
        val fileStatusResponse: String =
          getFileChecksProgressClient.GraphqlData(Option(fileChecksData), List()).asJson.printWith(Printer(dropNullValues = false, ""))
        val filePathResponse: String =
          getConsignmentFilesClient.GraphqlData(Option(filePathData), List()).asJson.printWith(Printer(dropNullValues = false, ""))

        mockGraphqlResponse(userType, fileStatusResponse, filePathResponse)
        setConsignmentReferenceResponse(wiremockServer)
        val config: Map[String, ConfigValue] = ConfigFactory
          .load()
          .entrySet()
          .asScala
          .map(e => e.getKey -> e.getValue)
          .toMap
        val applicationConfig = new ApplicationConfig(Configuration.from(config))

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          applicationConfig
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId) }
          else { fileCheckResultsController.fileCheckResultsPage(consignmentId) }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks").withCSRFToken)
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        status(recordCheckResultsPage) mustBe 200
        contentType(recordCheckResultsPage) mustBe Some("text/html")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        if (userType == "standard") {
          resultsPageAsString must include(expectedSuccessSummaryTitle)
          resultsPageAsString must include(confirmTransferButton)
        }

        if (userType == "judgment") {
          resultsPageAsString must include regex (buttonToProgress)
        }

        resultsPageAsString must include(expectedSuccessMessage)
      }

      s"render the $userType fileChecksResults page with the confirmation box" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("Success")))

        val fileChecksData = gfcp.Data(
          Option(
            GetConsignment(allChecksSucceeded = true, Option("parentFolder"), 1, fileStatus, FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
          )
        )

        val filePathData = gcf.Data(
          Option(gcf.GetConsignment(List(Files(UUID.randomUUID(), Option(""), Option(""), Option(UUID.randomUUID()), Metadata(Some("test file.docx"))))))
        )

        val getFileChecksProgressClient = graphQLConfiguration.getClient[gfcp.Data, gfcp.Variables]()
        val getConsignmentFilesClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables]()
        val fileStatusResponse: String =
          getFileChecksProgressClient.GraphqlData(Option(fileChecksData), List()).asJson.printWith(Printer(dropNullValues = false, ""))
        val filePathResponse: String =
          getConsignmentFilesClient.GraphqlData(Option(filePathData), List()).asJson.printWith(Printer(dropNullValues = false, ""))

        mockGraphqlResponse(userType, fileStatusResponse, filePathResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {
            fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)
          } else {
            fileCheckResultsController.fileCheckResultsPage(consignmentId)
          }
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks").withCSRFToken)
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        status(recordCheckResultsPage) mustBe 200
        contentType(recordCheckResultsPage) mustBe Some("text/html")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(resultsPageAsString, userType = userType)
        resultsPageAsString must include(expectedTitle)
        resultsPageAsString must include(expectedHeading)
        if (userType != "judgment") {
          resultsPageAsString must include(expectedSuccessSummaryTitle)
        }
        resultsPageAsString must include(expectedSuccessMessage)
        resultsPageAsString must include regex (buttonToProgress)
      }

      s"return a redirect to the auth server if an unauthenticated user tries to access the $userType file checks page" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val controller = new FileChecksResultsController(
          getUnauthorisedSecurityComponents,
          getValidKeycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )
        val recordChecksResultsPage = controller
          .fileCheckResultsPage(consignmentId)
          .apply(FakeRequest(GET, s"consignment/$consignmentId/file-checks-results"))

        status(recordChecksResultsPage) mustBe FOUND
        redirectLocation(recordChecksResultsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      }

      s"return an error if an authenticated user tries to get information for a consignment they don't own from the $userType file checks page" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val controller = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          getValidKeycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )
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

        val results: Throwable = controller
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
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
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

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId) }
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
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
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

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId) }
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
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
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

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId) }
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
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
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

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") { fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId) }
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
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val userType = "judgment"
      val fileCheckResultsController = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        consignmentStatusService,
        configuration
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, exportStatus = Some(consignmentStatus))
      setConsignmentTypeResponse(wiremockServer, userType)
      setConsignmentReferenceResponse(wiremockServer)

      val transferAlreadyCompletedPage = fileCheckResultsController
        .judgmentFileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/$userType/$consignmentId/file-checks").withCSRFToken)

      val transferAlreadyCompletedPageAsString = contentAsString(transferAlreadyCompletedPage)

      status(transferAlreadyCompletedPage) mustBe OK
      contentType(transferAlreadyCompletedPage) mustBe Some("text/html")
      headers(transferAlreadyCompletedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(transferAlreadyCompletedPageAsString, userType = userType)
      transferAlreadyCompletedPageAsString must include("<title>Transfer Already Completed</title>")
      transferAlreadyCompletedPageAsString must include(
        """                <h1 class="govuk-heading-l">Your transfer has already been completed</h1>
        |                <p class="govuk-body">Click 'Continue' to see the confirmation page again or return to the start.</p>""".stripMargin
      )
      transferAlreadyCompletedPageAsString must include(
        s"""                    <a role="button" data-prevent-double-click="true" class="govuk-button" data-module="govuk-button"
           |                        href="/$userType/$consignmentId/transfer-complete">Continue
           |                    </a>""".stripMargin
      )
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          user,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          configuration
        )

        val fileCheckResultsPage = url match {
          case "judgment" =>
            mockGraphqlResponse(consignmentType = "standard")
            fileCheckResultsController
              .judgmentFileCheckResultsPage(consignmentId)
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
}
