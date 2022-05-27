package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
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
import org.scalatest.prop.TableFor1
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{ConsignmentService, ConsignmentStatusService}
import util.FrontEndTestHelper

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class FileChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.fromString("0a3f617c-04e8-41c2-9f24-99622a779528")
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val userTypes: TableFor1[String] = Table(
    "User type",
    "judgment",
    "standard"
  )

  val consignmentStatuses: TableFor1[String] = Table(
    "Consignment status",
    "Completed",
    "InProgress",
    "Failed"
  )

  forAll (userTypes) { userType =>
    "FileChecksResultsController GET" should {

      val (pathName, keycloakConfiguration, expectedTitle, expectedFaqLink, expectedHelpLink, expectedReference) = if(userType == "judgment") {
        ("judgment", getValidJudgmentUserKeycloakConfiguration, "Results of checks", """" href="/judgment/faq">""", """ href="/judgment/help">""",
          "TEST-TDR-2021-GB")
      } else {
        ("consignment", getValidStandardUserKeycloakConfiguration, "Results of your checks", """" href="/faq">""", """href="/help">""", "TEST-TDR-2021-GB")
      }

      s"render the $userType fileChecksResults page with the confirmation box" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("Success")))

        val fileChecksData = gfcp.Data(
          Option(
            GetConsignment(allChecksSucceeded = true, Option("parentFolder"), 1, fileStatus,
              FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
          )
        )

        val filePathData = gcf.Data(
          Option(gcf.GetConsignment(List(Files(Metadata(Some("test file.docx")))))          )
        )

        val getFileChecksProgressClient = graphQLConfiguration.getClient[gfcp.Data, gfcp.Variables ]()
        val getConsignmentFilesClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables ]()
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
          frontEndInfoConfiguration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)}
          else {fileCheckResultsController.fileCheckResultsPage(consignmentId)}
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks").withCSRFToken)
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("has been successfully checked and is ready to be exported")
          resultsPageAsString must include("Export")
        } else {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("has been successfully checked and uploaded")
          resultsPageAsString must include("Click 'Continue' to proceed with your transfer")
          resultsPageAsString must include("Continue")
        }

        status(recordCheckResultsPage) mustBe 200
        contentType(recordCheckResultsPage) mustBe Some("text/html")
        resultsPageAsString must include("success-summary")
        resultsPageAsString must include(expectedFaqLink)
        resultsPageAsString must include(expectedHelpLink)
        resultsPageAsString must include(expectedReference)
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
          frontEndInfoConfiguration
        )
        val recordChecksResultsPage = controller.fileCheckResultsPage(consignmentId)
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
          frontEndInfoConfiguration
        )
        val exampleApiResponse = "{\"fileChecksData\":{" +
          "\"getConsignment\":null}," +
          "\"errors\":[{" +
          "\"message\":\"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '0a3f617c-04e8-41c2-9f24-99622a779528'\"," +
          "\"path\":[\"getConsignment\"],\"locations\":[{" +
          "\"column\":3,\"line\":2}]," +
          "\"extensions\":{" +
          "\"code\":\"NOT_AUTHORISED\"}}]}"

        wiremockServer.stubFor(post(urlEqualTo("/graphql"))
          .willReturn(okJson(exampleApiResponse)))

        val results: Throwable = controller.fileCheckResultsPage(consignmentId).apply(
          FakeRequest(GET, s"consignment/$consignmentId/file-checks-results")
        ).failed.futureValue

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
            GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus,
              FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
          )
        )
        val client = graphQLConfiguration.getClient[Data, Variables ]()
        val fileStatusResponse: String = client.GraphqlData(Option(data), List()).asJson.printWith(Printer(dropNullValues = false, ""))

        mockGraphqlResponse(userType, fileStatusResponse)
        setConsignmentReferenceResponse(wiremockServer)

        val fileCheckResultsController = new FileChecksResultsController(
          getAuthorisedSecurityComponents,
          keycloakConfiguration,
          new GraphQLConfiguration(app.configuration),
          consignmentService,
          consignmentStatusService,
          frontEndInfoConfiguration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)}
          else {fileCheckResultsController.fileCheckResultsPage(consignmentId)}
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("Your file has failed our checks. Please try again.")
        } else {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("One or more files you uploaded have failed our checks")
          resultsPageAsString must include(
            "<a class=\"govuk-link\" href=\"mailto:tdr@nationalachives.gov.uk?subject=Ref: TEST-TDR-2021-GB - Problem with Results of checks\">" +
              "tdr@nationalachives.gov.uk</a>"
          )
        }

        status(recordCheckResultsPage) mustBe OK
        contentAsString(recordCheckResultsPage) must include("There is a problem")
        resultsPageAsString must include("Return to start")
        resultsPageAsString must include(expectedFaqLink)
        resultsPageAsString must include(expectedReference)
      }

      s"return the passwordProtected $userType error page if file checks have failed with PasswordProtected" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("PasswordProtected")))

        val data = Data(
          Option(
            GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus,
              FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
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
          frontEndInfoConfiguration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)}
          else {fileCheckResultsController.fileCheckResultsPage(consignmentId)}
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("Your file has failed our checks. Please try again.")
        } else {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("We cannot accept password protected files. Once removed or replaced, try uploading your folder again.")
        }

        status(recordCheckResultsPage) mustBe OK
        contentAsString(recordCheckResultsPage) must include("There is a problem")
        resultsPageAsString must include("Return to start")
        resultsPageAsString must include(expectedFaqLink)
        resultsPageAsString must include(expectedReference)
      }

      s"return the zip $userType error page if file checks have failed with Zip" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("Zip")))

        val data = Data(
          Option(
            GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus,
              FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
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
          frontEndInfoConfiguration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)}
          else {fileCheckResultsController.fileCheckResultsPage(consignmentId)}
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("Your file has failed our checks. Please try again.")
        } else {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("We cannot accept zip files and similar archival package file formats.")
        }

        status(recordCheckResultsPage) mustBe OK
        contentAsString(recordCheckResultsPage) must include("There is a problem")
        resultsPageAsString must include("Return to start")
        resultsPageAsString must include(expectedFaqLink)
        resultsPageAsString must include(expectedReference)
      }

      s"return the general $userType error page if file checks have failed with PasswordProtected and Zip" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        setConsignmentStatusResponse(app.configuration, wiremockServer)
        val fileStatus = List(gfcp.GetConsignment.Files(Some("PasswordProtected")), gfcp.GetConsignment.Files(Some("Zip")))

        val data = Data(
          Option(
            GetConsignment(allChecksSucceeded = false, Option("parentFolder"), 1, fileStatus,
              FileChecks(AntivirusProgress(1), ChecksumProgress(1), FfidProgress(1)))
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
          frontEndInfoConfiguration
        )

        val recordCheckResultsPage = {
          if (userType == "judgment") {fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)}
          else {fileCheckResultsController.fileCheckResultsPage(consignmentId)}
        }.apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        val resultsPageAsString = contentAsString(recordCheckResultsPage)

        if (userType == "judgment") {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("Your file has failed our checks. Please try again.")
        } else {
          resultsPageAsString must include(expectedTitle)
          resultsPageAsString must include("One or more files you uploaded have failed our checks")
          resultsPageAsString must include(
            "<a class=\"govuk-link\" href=\"mailto:tdr@nationalachives.gov.uk?subject=Ref: TEST-TDR-2021-GB - Problem with Results of checks\">" +
              "tdr@nationalachives.gov.uk</a>"
          )
        }

        status(recordCheckResultsPage) mustBe OK
        contentAsString(recordCheckResultsPage) must include("There is a problem")
        resultsPageAsString must include("Return to start")
        resultsPageAsString must include(expectedFaqLink)
        resultsPageAsString must include(expectedReference)
      }
    }
  }

  forAll(consignmentStatuses) { consignmentStatus =>
    s"render the 'transfer has already been confirmed' page with an authenticated judgment user if export status is '$consignmentStatus'" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val fileCheckResultsController = new FileChecksResultsController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        new GraphQLConfiguration(app.configuration),
        consignmentService,
        consignmentStatusService,
        frontEndInfoConfiguration
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, exportStatus = Some(consignmentStatus))
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val transferAlreadyCompletedPage = fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks").withCSRFToken)

      val transferAlreadyCompletedPageAsString = contentAsString(transferAlreadyCompletedPage)

      status(transferAlreadyCompletedPage) mustBe OK
      contentType(transferAlreadyCompletedPage) mustBe Some("text/html")
      headers(transferAlreadyCompletedPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      transferAlreadyCompletedPageAsString must include(
        s"""href="/judgment/$consignmentId/transfer-complete">Continue""".stripMargin)
      transferAlreadyCompletedPageAsString must include("Your transfer has already been completed")
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
          frontEndInfoConfiguration
        )

        val fileCheckResultsPage = url match {
          case "judgment" =>
            mockGraphqlResponse(consignmentType = "standard")
            fileCheckResultsController.judgmentFileCheckResultsPage(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks-results"))
          case "consignment" =>
            mockGraphqlResponse(consignmentType = "judgment")
            fileCheckResultsController.fileCheckResultsPage(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks-results"))
        }
        status(fileCheckResultsPage) mustBe FORBIDDEN
      }
    }
  }

  private def mockGraphqlResponse(consignmentType: String, fileStatusResponse: String = "", filePathResponse: String= "") = {
    if(consignmentType == "judgment") {
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFiles"))
        .willReturn(okJson(filePathResponse)))
    }
    if(fileStatusResponse.nonEmpty) {
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getFileCheckProgress"))
        .willReturn(okJson(fileStatusResponse)))
    }
    setConsignmentTypeResponse(wiremockServer, consignmentType)
  }
}
