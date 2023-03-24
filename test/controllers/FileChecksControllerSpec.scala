package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{status, _}
import configuration.GraphQLConfiguration
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => fileCheck}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala

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

  val checkPageForStaticElements = new CheckPageForStaticElements

  forAll(fileChecks) { userType =>
    "FileChecksController GET" should {
      val (pathName, keycloakConfiguration, expectedTitle, expectedHeading, expectedInstruction, expectedForm) = if (userType == "judgment") {
        (
          "judgment",
          getValidJudgmentUserKeycloakConfiguration,
          "<title>Checking your upload - Transfer Digital Records - GOV.UK</title>",
          """<h1 class="govuk-heading-l">Checking your upload""",
          """            <p class="govuk-body">Your judgment is being checked for errors.
            |              This may take a few minutes. Once your judgment has been checked, you will be redirected automatically.
            |            </p>""".stripMargin,
          s"""<form action="/judgment/$consignmentId/file-checks-results" method="GET" id="file-checks-form">""".stripMargin
        )
      } else {
        // scalastyle:off line.size.limit
        (
          "consignment",
          getValidStandardUserKeycloakConfiguration,
          "<title>Checking your records - Transfer Digital Records - GOV.UK</title>",
          """<h1 class="govuk-heading-l">Checking your records</h1>""",
          """            <p class="govuk-body">Please wait while your records are being checked. This may take a few minutes.</p>
            |            <p class="govuk-body">The following checks are now being performed:</p>
            |            <ul class="govuk-list govuk-list--bullet">
            |                <li>Anti-virus scanning</li>
            |                <li>Identifying file formats</li>
            |                <li>Validating data integrity</li>
            |            </ul>""".stripMargin,
          s"""            <form action="/consignment/$consignmentId/file-checks-results">
            |                <button type="submit" role="button" draggable="false" id="file-checks-continue" class="govuk-button govuk-button--disabled" aria-disabled="true" aria-describedby="reason-disabled">
            |                Continue
            |                </button>
            |                <p class="govuk-visually-hidden" id="reason-disabled">
            |                    This button will be enabled when we have finished checking your files.
            |                </p>
            |            </form>""".stripMargin
        )
        // scalastyle:on line.size.limit
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

        val fileChecksController = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration
        )

        val fileChecksPage = if (userType == "judgment") {
          fileChecksController.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks").withCSRFToken)
        } else {
          fileChecksController.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks").withCSRFToken)
        }

        val fileChecksPageAsString = contentAsString(fileChecksPage)

        playStatus(fileChecksPage) mustBe OK
        contentType(fileChecksPage) mustBe Some("text/html")
        headers(fileChecksPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileChecksPageAsString, userType = userType)
        fileChecksPageAsString must include(expectedTitle)
        fileChecksPageAsString must include(expectedHeading)
        fileChecksPageAsString must include(expectedInstruction)
        if (userType == "judgment") {
          fileChecksPageAsString must include("""<input id="consignmentId" type="hidden" value="b5bbe4d6-01a7-4305-99ef-9fce4a67917a">""")
        } else {
          // scalastyle:off line.size.limit
          fileChecksPageAsString must include(
            """            <p class="govuk-body govuk-!-margin-bottom-7">For more information on these checks, please see our
            |                <a href="/faq#progress-checks" target="_blank" rel="noopener noreferrer" class="govuk-link">FAQ (opens in new tab)</a> for this service.
            |            </p>""".stripMargin
          )
          // scalastyle:on line.size.limit
          fileChecksPageAsString must include(
            """                <div class="govuk-notification-banner__header">
            |                    <h2 class="govuk-notification-banner__title" id="govuk-notification-banner-title">
            |                        Important
            |                    </h2>
            |                </div>
            |                <div class="govuk-notification-banner__content">
            |                    <p class="govuk-notification-banner__heading">Your records have been checked</p>
            |                    <p class="govuk-body">Please click 'Continue' to see your results.</p>
            |                </div>""".stripMargin
          )
        }
        fileChecksPageAsString must include(expectedForm)
      }

      s"return a redirect to the auth server with an unauthenticated $userType user" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val controller =
          new FileChecksController(getUnauthorisedSecurityComponents, graphQLConfiguration, getValidKeycloakConfiguration, consignmentService, frontEndInfoConfiguration)
        val fileChecksPage = controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))

        playStatus(fileChecksPage) mustBe FOUND
        redirectLocation(fileChecksPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      }

      s"render the $userType file checks complete page if the file checks are complete and all checks are successful" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val dataString: String = progressData(40, 40, 40, allChecksSucceeded = true)

        mockGraphqlResponse(dataString, userType)
        setConsignmentReferenceResponse(wiremockServer)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration
        )

        val fileChecksCompletePage = if (userType == "judgment") {
          controller.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        } else {
          controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        }

        val fileChecksCompletePageAsString = contentAsString(fileChecksCompletePage)

        playStatus(fileChecksCompletePage) mustBe OK

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileChecksCompletePageAsString, userType = userType)
        fileChecksCompletePageAsString must include(expectedTitle)
        fileChecksCompletePageAsString must include(expectedHeading)
        fileChecksCompletePageAsString must include("Your upload and checks have been completed.")
        fileChecksCompletePageAsString must not include (
          s"""                <a role="button" data-prevent-double-click="true" class="govuk-button" data-module="govuk-button"
                                                                                 href="/$pathName/$consignmentId/file-checks">
        Continue"""
        )
      }

      s"render the $userType file checks complete page if the file checks are complete and all checks are not successful" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val dataString: String = progressData(40, 40, 40, allChecksSucceeded = false)

        mockGraphqlResponse(dataString, userType)
        setConsignmentReferenceResponse(wiremockServer)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration
        )
        val fileChecksCompletePage = if (userType == "judgment") {
          controller.judgmentFileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        } else {
          controller.fileChecksPage(consignmentId).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        }
        val fileChecksCompletePageAsString = contentAsString(fileChecksCompletePage)

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileChecksCompletePageAsString, userType = userType)
        playStatus(fileChecksCompletePage) mustBe OK
        fileChecksCompletePageAsString must include(expectedTitle)
        fileChecksCompletePageAsString must include(expectedHeading)
        fileChecksCompletePageAsString must include("Your upload and checks have been completed.")
        fileChecksCompletePageAsString must not include (
          s"""                <a role="button" data-prevent-double-click="true" class="govuk-button" data-module="govuk-button"
                                                                                 href="/$pathName/$consignmentId/file-checks">
        Continue"""
        )
      }
    }
  }

  "FileChecksController fileCheckProgress" should {
    "call the fileCheckProgress endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )

      mockGraphqlResponse(progressData(1, 1, 1, allChecksSucceeded = true), "standard")

      val fileChecksResults = controller
        .fileCheckProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/file-check-progress").withCSRFToken)

      playStatus(fileChecksResults) mustBe OK

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error if the API returns an error" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration
      )

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getFileCheckProgress"))
          .willReturn(serverError())
      )

      val saveMetadataResponse = controller
        .fileCheckProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/file-check-progress").withCSRFToken)

      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must startWith("Unexpected response from GraphQL API")
    }
  }

  private def mockGraphqlResponse(dataString: String, userType: String) = {
    setConsignmentTypeResponse(wiremockServer, userType)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getFileCheckProgress"))
        .willReturn(okJson(dataString))
    )
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          user,
          consignmentService,
          frontEndInfoConfiguration
        )

        val fileChecksPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller
              .judgmentFileChecksPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks"))
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller
              .fileChecksPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/file-checks"))
        }
        playStatus(fileChecksPage) mustBe FORBIDDEN
      }
    }
  }

  private def progressData(filesProcessedWithAntivirus: Int, filesProcessedWithChecksum: Int, filesProcessedWithFFID: Int, allChecksSucceeded: Boolean): String = {
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
