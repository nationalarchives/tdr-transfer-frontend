package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => fileCheck}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import util.FrontEndTestHelper

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class FileChecksControllerSpec extends FrontEndTestHelper with TableDrivenPropertyChecks {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val totalFiles: Int = 40
  val consignmentId: UUID = UUID.fromString("b5bbe4d6-01a7-4305-99ef-9fce4a67917a")

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val fileChecks: TableFor1[String] = Table(
    "User type",
    "judgment",
    "standard"
  )

  forAll (fileChecks) { userType =>
    "FileChecksController GET" should {
      val (pathName, keycloakConfiguration, expectedTitle, expectedText, expectedFaqLink) = if(userType == "judgment") {
        ("judgment",
          getValidJudgmentUserKeycloakConfiguration,
          "Checking your upload",
          "Your judgment is being checked for errors",
          s"""" href="/judgment/faq">""")
      } else {
        ("consignment",
          getValidStandardUserKeycloakConfiguration,
          "Checking your records",
          "Please wait while your records are being checked. This may take a few minutes.",
          s"""" href="/faq">""")
      }

      s"render the $userType fileChecks page if the checks are incomplete" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val filesProcessedWithAntivirus = 6

        val filesProcessedWithChecksum = 12
        val filesProcessedWithFFID = 8
        val dataString: String = progressData(filesProcessedWithAntivirus, filesProcessedWithChecksum, filesProcessedWithFFID, allChecksSucceeded = false)

        mockGraphqlResponse(dataString, userType)
        setConsignmentReferenceResponse(wiremockServer)

        val recordsController = new FileChecksController(
          getAuthorisedSecurityComponents,
          new GraphQLConfiguration(app.configuration),
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration
        )

        val recordsPage = if (userType == "judgment") {
          recordsController.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        } else {
          recordsController.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        }

        val recordsPageAsString = contentAsString(recordsPage)

        playStatus(recordsPage) mustBe OK
        contentType(recordsPage) mustBe Some("text/html")
        headers(recordsPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
        recordsPageAsString must include(expectedTitle)
        recordsPageAsString must include(expectedText)
        recordsPageAsString must include(expectedFaqLink)
//        recordsPageAsString must include("TEST-TDR-2021-GB")
      }

      s"return a redirect to the auth server with an unauthenticated $userType user" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val controller = new FileChecksController(getUnauthorisedSecurityComponents,
          new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration, consignmentService, frontEndInfoConfiguration)
        val recordsPage = controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))

        playStatus(recordsPage) mustBe FOUND
        redirectLocation(recordsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      }

    s"render the $userType file checks complete page if the file checks are complete and all checks are successful" in {
      print("\n\nblah", userType, pathName, expectedTitle)
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val dataString: String = progressData(40, 40, 40, allChecksSucceeded = true)

      mockGraphqlResponse(dataString, userType)

      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )

      val fileChecksCompletePage = if (userType == "judgment") {
        controller.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
      } else {
        controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
      }

      playStatus(fileChecksCompletePage) mustBe OK
      contentAsString(fileChecksCompletePage) must include(expectedTitle)
      contentAsString(fileChecksCompletePage) must include("Your upload and checks have been completed.")
      contentAsString(fileChecksCompletePage) must not include(
        s"""                <a role="button" data-prevent-double-click="true" class="govuk-button" data-module="govuk-button"
                                                                                 href="/$pathName/$consignmentId/file-checks">
        Continue""")
      contentAsString(fileChecksCompletePage) must include(expectedFaqLink)
    }

    s"render the $userType file checks complete page if the file checks are complete and all checks are not successful" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val dataString: String = progressData(40, 40, 40, allChecksSucceeded = false)

      mockGraphqlResponse(dataString, userType)

      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration),
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )
      val fileChecksCompletePage = if (userType == "judgment") {
        controller.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
      } else {
        controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
      }
      playStatus(fileChecksCompletePage) mustBe OK
      contentAsString(fileChecksCompletePage) must include(expectedTitle)
      contentAsString(fileChecksCompletePage) must include("Your upload and checks have been completed.")
      contentAsString(fileChecksCompletePage) must not include(
        s"""                <a role="button" data-prevent-double-click="true" class="govuk-button" data-module="govuk-button"
                                                                                 href="/$pathName/$consignmentId/file-checks">
        Continue""")
      contentAsString(fileChecksCompletePage) must include(expectedFaqLink)
    }
    }
  }


  private def mockGraphqlResponse(dataString: String, userType: String) = {
    setConsignmentTypeResponse(wiremockServer, userType)
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getFileCheckProgress"))
      .willReturn(okJson(dataString)))
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          new GraphQLConfiguration(app.configuration),
          user,
          consignmentService,
          frontEndInfoConfiguration
        )

        val fileChecksPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentFileChecksPage(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks"))
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.fileChecksPage(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks"))
        }
        playStatus(fileChecksPage) mustBe FORBIDDEN
      }
    }
  }

  private def progressData(filesProcessedWithAntivirus: Int, filesProcessedWithChecksum: Int,
                           filesProcessedWithFFID: Int, allChecksSucceeded: Boolean): String = {
    val client = new GraphQLConfiguration(app.configuration).getClient[fileCheck.Data, fileCheck.Variables]()
    val antivirusProgress = fileCheck.GetConsignment.FileChecks.AntivirusProgress(filesProcessedWithAntivirus)
    val checksumProgress = fileCheck.GetConsignment.FileChecks.ChecksumProgress(filesProcessedWithChecksum)
    val ffidProgress = fileCheck.GetConsignment.FileChecks.FfidProgress(filesProcessedWithFFID)
    val fileChecks = fileCheck.GetConsignment.FileChecks(antivirusProgress, checksumProgress, ffidProgress)
    val fileStatus = List(fileCheck.GetConsignment.Files(Some("Success")))
    val data: client.GraphqlData = client.GraphqlData(
      Some(
        fileCheck.Data(
          Some(
            fileCheck.GetConsignment(allChecksSucceeded, Option(""), totalFiles, fileStatus, fileChecks)
          )
        )
      )
    )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    dataString
  }
}
