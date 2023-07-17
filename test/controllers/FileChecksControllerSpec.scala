package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => fileCheck}
import graphql.codegen.UpdateConsignmentStatus.updateConsignmentStatus
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.commons.lang3.BooleanUtils.{FALSE, TRUE}
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import play.api.test.WsTestClient.InternalWSClient
import services.Statuses.{CompletedValue, UploadType}
import services.{BackendChecksService, ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.ListHasAsScala

class FileChecksControllerSpec extends FrontEndTestHelper with TableDrivenPropertyChecks {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val totalFiles: Int = 40
  val consignmentId: UUID = UUID.fromString("b5bbe4d6-01a7-4305-99ef-9fce4a67917a")
  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

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
            |                <button type="submit" role="button" draggable="false" id="file-checks-continue" class="govuk-button govuk-button--disabled" data-tdr-module="button-disabled" data-module="govuk-button" aria-disabled="true" aria-describedby="reason-disabled">
            |                Continue
            |                </button>
            |                <p class="govuk-visually-hidden" id="reason-disabled">
            |                    This button will be enabled when we have finished checking your files.
            |                </p>
            |            </form>""".stripMargin
        )
      }

      s"render the $userType fileChecks page if the checks are incomplete" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val filesProcessedWithAntivirus = 6

        val filesProcessedWithChecksum = 12
        val filesProcessedWithFFID = 8
        val dataString: String = progressData(filesProcessedWithAntivirus, filesProcessedWithChecksum, filesProcessedWithFFID, allChecksSucceeded = false)

        val uploadStatus = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), UploadType.id, CompletedValue.value, someDateTime, None))
        mockGetFileCheckProgress(dataString, userType)
        setConsignmentReferenceResponse(wiremockServer)
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = uploadStatus)

        val fileChecksController = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )
        val uploadFailed = FALSE
        val fileChecksPage = if (userType == "judgment") {
          fileChecksController
            .judgmentFileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        } else {
          fileChecksController
            .fileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
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
          fileChecksPageAsString must include(
            """            <p class="govuk-body govuk-!-margin-bottom-7">For more information on these checks, please see our
            |                <a href="/faq#progress-checks" target="_blank" rel="noopener noreferrer" class="govuk-link">FAQ (opens in new tab)</a> for this service.
            |            </p>""".stripMargin
          )
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
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val controller =
          new FileChecksController(
            getUnauthorisedSecurityComponents,
            graphQLConfiguration,
            getValidKeycloakConfiguration,
            consignmentService,
            frontEndInfoConfiguration,
            backendChecksService,
            consignmentStatusService
          )
        val fileChecksPage = controller.fileChecksPage(consignmentId, None).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))

        playStatus(fileChecksPage) mustBe FOUND
        redirectLocation(fileChecksPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      }

      s"render the $userType file checks complete page if the file checks are complete and all checks are successful" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = mock[BackendChecksService]
        val dataString: String = progressData(40, 40, 40, allChecksSucceeded = true)

        val uploadStatus = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), UploadType.id, InProgress.value, someDateTime, None))
        val client = graphQLConfiguration.getClient[updateConsignmentStatus.Data, updateConsignmentStatus.Variables]()
        val data = client.GraphqlData(Option(updateConsignmentStatus.Data(Option(1))), Nil)
        val ucsDataString = data.asJson.noSpaces
        when(backendChecksService.triggerBackendChecks(org.mockito.ArgumentMatchers.any(classOf[UUID]), anyString())).thenReturn(Future.successful(true))
        mockGetFileCheckProgress(dataString, userType)
        mockUpdateConsignmentStatus(ucsDataString)
        setConsignmentReferenceResponse(wiremockServer)
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = uploadStatus)

        val fileChecksController = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )
        val uploadFailed = FALSE
        val fileChecksPage = if (userType == "judgment") {
          fileChecksController
            .judgmentFileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        } else {
          fileChecksController
            .fileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        }

        val fileChecksCompletePageAsString = contentAsString(fileChecksPage)

        playStatus(fileChecksPage) mustBe OK

        wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("updateConsignmentStatus")))
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
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val dataString: String = progressData(40, 40, 40, allChecksSucceeded = false)
        val uploadStatus = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), UploadType.id, CompletedValue.value, someDateTime, None))

        mockGetFileCheckProgress(dataString, userType)
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = uploadStatus)
        setConsignmentReferenceResponse(wiremockServer)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )
        val fileChecksCompletePage = if (userType == "judgment") {
          controller.judgmentFileChecksPage(consignmentId, None).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
        } else {
          controller.fileChecksPage(consignmentId, None).apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks"))
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

      s"render the $userType error page if the user upload fails and ensure consignment status is updated" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val filesProcessedWithAntivirus = 6

        val filesProcessedWithChecksum = 12
        val filesProcessedWithFFID = 8
        val dataString: String = progressData(filesProcessedWithAntivirus, filesProcessedWithChecksum, filesProcessedWithFFID, allChecksSucceeded = false)

        val uploadStatus = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), UploadType.id, CompletedValue.value, someDateTime, None))
        val client = graphQLConfiguration.getClient[updateConsignmentStatus.Data, updateConsignmentStatus.Variables]()
        val data = client.GraphqlData(Option(updateConsignmentStatus.Data(Option(1))), Nil)
        val ucsDataString = data.asJson.noSpaces
        mockGetFileCheckProgress(dataString, userType)
        mockUpdateConsignmentStatus(ucsDataString)
        setConsignmentReferenceResponse(wiremockServer)
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = uploadStatus)

        val fileChecksController = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )
        val uploadFailed = TRUE
        val fileChecksPage = if (userType == "judgment") {
          fileChecksController
            .judgmentFileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        } else {
          fileChecksController
            .fileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        }

        val fileChecksPageAsString = contentAsString(fileChecksPage)

        playStatus(fileChecksPage) mustBe OK
        contentType(fileChecksPage) mustBe Some("text/html")
        headers(fileChecksPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileChecksPageAsString, userType = userType)

        fileChecksPageAsString must include(
          """<h2 class="govuk-error-summary__title" id="error-summary-title">
            |              There is a problem
            |            </h2>""".stripMargin
        )
        fileChecksPageAsString must include("""<p>Your upload was interrupted and could not be completed.</p>""")
        wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("updateConsignmentStatus")))
      }

      s"render the $userType error page if the user upload succeeds but backendChecks fails to trigger" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = mock[BackendChecksService]
        val filesProcessedWithAntivirus = 6

        val filesProcessedWithChecksum = 12
        val filesProcessedWithFFID = 8
        val dataString: String = progressData(filesProcessedWithAntivirus, filesProcessedWithChecksum, filesProcessedWithFFID, allChecksSucceeded = false)

        val uploadStatus = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), UploadType.id, InProgress.value, someDateTime, None))
        val client = graphQLConfiguration.getClient[updateConsignmentStatus.Data, updateConsignmentStatus.Variables]()
        val data = client.GraphqlData(Option(updateConsignmentStatus.Data(Option(1))), Nil)
        val ucsDataString = data.asJson.noSpaces
        when(backendChecksService.triggerBackendChecks(org.mockito.ArgumentMatchers.any(classOf[UUID]), anyString())).thenReturn(Future.successful(false))
        mockGetFileCheckProgress(dataString, userType)
        mockUpdateConsignmentStatus(ucsDataString)
        setConsignmentReferenceResponse(wiremockServer)
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = uploadStatus)

        val fileChecksController = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          keycloakConfiguration,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )
        val uploadFailed = FALSE
        val fileChecksPage = if (userType == "judgment") {
          fileChecksController
            .judgmentFileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        } else {
          fileChecksController
            .fileChecksPage(consignmentId, Some(uploadFailed))
            .apply(FakeRequest(GET, s"/$pathName/$consignmentId/file-checks?uploadFailed=$uploadFailed").withCSRFToken)
        }

        val fileChecksPageAsString = contentAsString(fileChecksPage)

        playStatus(fileChecksPage) mustBe OK
        contentType(fileChecksPage) mustBe Some("text/html")
        headers(fileChecksPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileChecksPageAsString, userType = userType)

        fileChecksPageAsString must include(
          """<h2 class="govuk-error-summary__title" id="error-summary-title">
            |              There is a problem
            |            </h2>""".stripMargin
        )
        fileChecksPageAsString must include("""<p>Your upload was interrupted and could not be completed.</p>""")
        wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("updateConsignmentStatus")))
      }
    }
  }

  "FileChecksController fileCheckProgress" should {
    "call the fileCheckProgress endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration,
        backendChecksService,
        consignmentStatusService
      )

      mockGetFileCheckProgress(progressData(1, 1, 1, allChecksSucceeded = true), "standard")

      val fileChecksResults = controller
        .fileCheckProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/file-check-progress").withCSRFToken)

      playStatus(fileChecksResults) mustBe OK

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error if the API returns an error" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val controller = new FileChecksController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        consignmentService,
        frontEndInfoConfiguration,
        backendChecksService,
        consignmentStatusService
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

  private def mockGetFileCheckProgress(dataString: String, userType: String) = {
    setConsignmentTypeResponse(wiremockServer, userType)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getFileCheckProgress"))
        .willReturn(okJson(dataString))
    )
  }

  private def mockUpdateConsignmentStatus(dataString: String) = {
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("updateConsignmentStatus"))
        .willReturn(okJson(dataString))
    )
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)

        val controller = new FileChecksController(
          getAuthorisedSecurityComponents,
          graphQLConfiguration,
          user,
          consignmentService,
          frontEndInfoConfiguration,
          backendChecksService,
          consignmentStatusService
        )

        val fileChecksPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller
              .judgmentFileChecksPage(consignmentId, None)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/file-checks"))
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller
              .fileChecksPage(consignmentId, None)
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
