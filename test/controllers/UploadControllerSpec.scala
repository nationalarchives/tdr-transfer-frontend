package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, serverError, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration}
import graphql.codegen.AddMultipleFileStatuses.addMultipleFileStatuses
import graphql.codegen.AddFilesAndMetadata.addFilesAndMetadata
import graphql.codegen.AddFilesAndMetadata.addFilesAndMetadata.AddFilesAndMetadata
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.StartUpload.startUpload
import graphql.codegen.types.{AddFileAndMetadataInput, AddFileStatusInput, AddMultipleFileStatusesInput, ClientSideMetadataInput, StartUploadInput}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, redirectLocation, status, _}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import services.{BackendChecksService, ConsignmentService, FileStatusService, UploadService}
import play.api.libs.json._

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures._
import play.api.Configuration
import play.api.test.WsTestClient.InternalWSClient
import services.Statuses.{CompletedValue, CompletedWithIssuesValue, InProgressValue, StatusValue}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import scala.jdk.CollectionConverters._

class UploadControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)
  val triggerBackendChecksServer = new WireMockServer(9008)
  private val configuration: Configuration = mock[Configuration]
  val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)

  override def beforeEach(): Unit = {
    triggerBackendChecksServer.start()
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    triggerBackendChecksServer.resetAll()
    wiremockServer.stop()
    triggerBackendChecksServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  implicit val ec: ExecutionContext = ExecutionContext.global

  "UploadController GET upload" should {

    "return forbidden for a TNA user" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidTNAUserKeycloakConfiguration(),
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      setConsignmentTypeResponse(wiremockServer, "standard")

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe FORBIDDEN
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been signed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has been partially agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "InProgress", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement-continued")
    }

    "show the standard upload page if the transfer agreement for that consignment has been agreed to in full" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard", pageRequiresAwsServices = true)
      checkForExpectedPageContentOnMainUploadPage(uploadPageAsString)
      uploadPageAsString must include("<title>Upload your records - Transfer Digital Records - GOV.UK</title>")
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Upload your records</h1>""")
      uploadPageAsString must include(
        """<p class="govuk-body">Before uploading, all files and folders you wish to transfer must be put into a single, top-level folder.</p>"""
      )
      uploadPageAsString must include(
        """Individual files must be no bigger than 2GB and you must upload no more than 3000 files per transfer. """ +
          """The total size of all files for transfer must be no more than 5GB. """ +
          """To transfer larger files, contact <a href="mailto:nationalArchives.email">nationalArchives.email</a>. """ +
          "If your folder contains file formats that we cannot accept, you may have to start again.</p>"
      )
      uploadPageAsString must include(
        """We cannot accept files and folders which are password protected, zipped or contain slashes (/ and \) in the name. """ +
          "You must remove all thumbnail images (thumbs.db) and executable files (.exe). Empty folders will not be transferred."
      )
      uploadPageAsString must include("""<h2 class="govuk-heading-m">Select a folder to upload</h2>""")
      uploadPageAsString must include(
        """<p class="govuk-body">Click 'Select a folder' to open a dialog box and select a folder. Once selected you will be prompted to confirm your choice.</p>"""
      )
      uploadPageAsString must include(
        """<p id="success-message-text" aria-live="assertive" aria-atomic="true" class="govuk-body drag-and-drop__selected__description">The folder <strong id="files-selected-folder-name" class="folder-name"></strong> (containing <span class="folder-size"></span>) has been selected.</p>"""
      )
      uploadPageAsString must include(
        """<a id="remove-file-btn" href="#" aria-describedby="files-selected-folder-name" class="govuk-link govuk-link--no-visited-state govuk-!-font-size-19 govuk-body govuk-!-font-weight-bold">Remove<span class="govuk-visually-hidden">&nbsp; selected files</span></a>""".stripMargin
      )
      uploadPageAsString must include(
        """<label class="govuk-label govuk-checkboxes__label govuk-!-padding-top-0" for="includeTopLevelFolder">
          |                                            If you want the folder name <strong class="folder-name"></strong> to be displayed on Discovery/the public catalogue, select this checkbox.
          |                                        </label>""".stripMargin
      )
      uploadPageAsString must include(
        """<p id="removed-selection-message-text" class="govuk-error-message">The folder "<span class="folder-name"></span>"""" +
          """ (containing <span class="folder-size"></span>) has been removed. Select a folder.</p>"""
      )
      uploadPageAsString must include(
        """|                            <div class="drag-and-drop__dropzone">
           |                                <input type="file" id="file-selection" name="files"
           |                                class="govuk-file-upload drag-and-drop__input" webkitdirectory""".stripMargin
      )
      uploadPageAsString must include(
        """|                                accept="*"
           |                                >
           |                                <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single top-level folder here&nbsp;</p>
           |                                <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
           |                                    Select a folder
           |                                </label>""".stripMargin
      )
      uploadPageAsString must include("For information on what metadata will be captured during upload, visit the")
      uploadPageAsString must include(
        """<a href="/faq#metadata-captured" target="_blank" rel="noopener noreferrer" class="govuk-link">FAQ (opens in new tab)</a>"""
      )
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Uploading your records</h1>""")
      uploadPageAsString must include(
        """|                <h2 class="govuk-error-summary__title" id="progress-error-summary-title">
           |                    There is a problem
           |                </h2>
           |                <div class="govuk-error-summary__body">
           |                    <p>Some or all of your files failed to upload.</p>""".stripMargin
      )

      uploadPageAsString must include(
        """<p class="govuk-body">Do not close your browser while your records are being uploaded. This may take a few minutes.</p>"""
      )
    }

    "render the 'upload in progress' page if a standard upload is in progress" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", "InProgress", someDateTime, None)
      )

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, uploadStatus = "InProgress")
    }

    "render 'upload is complete' page if a standard upload has completed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val uploadStatus = "Completed"

      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", uploadStatus, someDateTime, None)
      )

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, uploadStatus = uploadStatus)
      uploadPageAsString must include(
        s"""      <a href="/consignment/$consignmentId/file-checks?uploadFailed=false" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin
      )
    }

    "render the 'upload in progress' error page if a standard upload has a 'completedWithIssues' status" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      val consignmentStatuses = List(
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", CompletedWithIssuesValue.value, someDateTime, None)
      )

      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller
        .uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, uploadStatus = CompletedWithIssuesValue.value)
    }

    "render the 'upload in progress error' page if a judgment file upload has a 'completedWithIssues' status" in {
      val uploadStatus = CompletedWithIssuesValue
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val uploadPage = executeJudgmentsUpload(uploadStatus, consignmentId, applicationConfig)
      val uploadPageAsString = contentAsString(uploadPage)
      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, userType = "judgment", uploadStatus = uploadStatus.value)
    }

    "show the judgment upload page for judgments" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidJudgmentUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )

      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val uploadPage = controller
        .judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment", pageRequiresAwsServices = true)
      checkForExpectedPageContentOnMainUploadPage(uploadPageAsString)
      uploadPageAsString must include("<title>Upload document - Transfer Digital Records - GOV.UK</title>")
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Upload document</h1>""")
      uploadPageAsString must include("You may now select and upload the judgment or decision. You can only upload one file.")
      uploadPageAsString must include("We only accept Microsoft Word files ending in .docx.")
      uploadPageAsString must include(
        """<form id="file-upload-form" data-consignment-id="c2efd3e6-6664-4582-8c28-dcf891f60e68">"""
      )
      uploadPageAsString must include(
        """<p id="success-message-text" class="success-message">The file "<span class="file-name"></span>" has been selected </p>"""
      )
      uploadPageAsString must include(
        """<a class="success-message-flexbox-item" id="remove-file-btn" href="#">Remove selected records</a>"""
      )
      uploadPageAsString must include(
        """<p id="removed-selection-message-text" class="govuk-error-message">The file "<span class="file-name"></span>" has been removed. Select a file.</p>"""
      )
      uploadPageAsString must include(
        """|                                <div class="drag-and-drop__dropzone">
           |                                    <input type="file" id="file-selection" name="files"
           |                                    class="govuk-file-upload drag-and-drop__input"""".stripMargin
      )
      uploadPageAsString must include(
        """|                                    accept=".docx"
           |                                    >
           |                                    <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single file here or&nbsp;</p>
           |                                    <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
           |                                        Choose file""".stripMargin
      )
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Uploading document</h1>""")
      uploadPageAsString must include(
        """|                <h2 class="govuk-error-summary__title" id="progress-error-summary-title">
           |                    There is a problem
           |                </h2>
           |                <div class="govuk-error-summary__body">
           |                    <p>Your file has failed to upload.</p>""".stripMargin
      )
      uploadPageAsString must include("""<p class="govuk-body">Do not close your browser window while your file is being uploaded. This may take a few minutes.</p>""")
    }

    "render the 'upload in progress' page if a judgment file upload is in progress" in {
      val uploadStatus = InProgressValue
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val uploadPage = executeJudgmentsUpload(uploadStatus, consignmentId, applicationConfig)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, userType = "judgment", uploadStatus = uploadStatus.value)
    }

    "render the judgment 'upload is complete' page if the upload has completed" in {
      val uploadStatus = CompletedValue
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")

      val uploadPage = executeJudgmentsUpload(uploadStatus, consignmentId, applicationConfig)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      uploadPageAsString must include(
        s"""      <a href="/judgment/$consignmentId/file-checks?uploadFailed=false" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, userType = "judgment", uploadStatus = uploadStatus.value)
    }
  }

  private def executeJudgmentsUpload(uploadStatus: StatusValue, consignmentId: UUID, applicationConfig: ApplicationConfig) = {
    val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
    val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
    val fileStatusService = new FileStatusService(graphQLConfiguration)
    val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
    val controller = new UploadController(
      getAuthorisedSecurityComponents,
      graphQLConfiguration,
      getValidJudgmentUserKeycloakConfiguration,
      applicationConfig,
      consignmentService,
      uploadService,
      fileStatusService,
      backendChecksService
    )

    val consignmentStatuses = List(
      ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
      ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", uploadStatus.value, someDateTime, None)
    )

    setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)
    setConsignmentTypeResponse(wiremockServer, "judgment")
    setConsignmentReferenceResponse(wiremockServer)

    controller
      .judgmentUploadPage(consignmentId)
      .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller =
          new UploadController(
            getAuthorisedSecurityComponents,
            graphQLConfiguration,
            user,
            frontEndInfoConfiguration,
            consignmentService,
            uploadService,
            fileStatusService,
            backendChecksService
          )

        setConsignmentStatusResponse(app.configuration, wiremockServer)

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller
              .judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller
              .uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload in progress page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller =
          new UploadController(
            getAuthorisedSecurityComponents,
            graphQLConfiguration,
            user,
            frontEndInfoConfiguration,
            consignmentService,
            uploadService,
            fileStatusService,
            backendChecksService
          )

        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", "InProgress", someDateTime, None)
        )

        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller
              .judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller
              .uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload has completed page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller =
          new UploadController(
            getAuthorisedSecurityComponents,
            graphQLConfiguration,
            user,
            frontEndInfoConfiguration,
            consignmentService,
            uploadService,
            fileStatusService,
            backendChecksService
          )

        val consignmentStatuses = List(
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "TransferAgreement", "Completed", someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Upload", "Completed", someDateTime, None)
        )

        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller
              .judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller
              .uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }
  }

  "UploadController saveMetadata" should {
    "call the saveMetadata endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val client = graphQLConfiguration.getClient[addFilesAndMetadata.Data, addFilesAndMetadata.Variables]()
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val fileId = UUID.randomUUID()
      val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, "1") :: Nil
      val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(consignmentId, clientSideMetadataInput, Some(Nil))
      val data = client.GraphqlData(Option(addFilesAndMetadata.Data(List(AddFilesAndMetadata(fileId, "0")))), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("addFilesAndMetadata"))
          .willReturn(okJson(dataString))
      )

      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val saveMetadataResponse = controller
        .saveClientMetadata()
        .apply(
          FakeRequest(POST, s"/save-metadata")
            .withJsonBody(Json.parse(addFileAndMetadataInput.asJson.noSpaces))
            .withCSRFToken
        )
      val response: String = contentAsString(saveMetadataResponse)
      val metadataResponse = decode[List[AddFilesAndMetadata]](response).toOption
      metadataResponse.isDefined must be(true)
      metadataResponse.get.head.fileId must be(fileId)
      metadataResponse.get.head.matchId mustBe "0"

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val saveMetadataResponse = controller
        .saveClientMetadata()
        .apply(
          FakeRequest(POST, s"/save-metadata")
            .withJsonBody(Json.parse("""{"bad": "data"}"""))
            .withCSRFToken
        )
      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must be("Incorrect data provided AnyContentAsJson({\"bad\":\"data\"})")
    }

    "throw an error if the API returns an error" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, "1") :: Nil
      val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(UUID.randomUUID(), clientSideMetadataInput, Some(Nil))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("addFilesAndMetadata"))
          .willReturn(serverError())
      )

      val saveMetadataResponse = controller
        .saveClientMetadata()
        .apply(
          FakeRequest(POST, s"/save-metadata")
            .withJsonBody(Json.parse(addFileAndMetadataInput.asJson.noSpaces))
            .withCSRFToken
        )
      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must startWith("Unexpected response from GraphQL API")
    }
  }

  "UploadController addFileStatus" should {
    "call the addFileStatus endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val client = graphQLConfiguration.getClient[addMultipleFileStatuses.Data, addMultipleFileStatuses.Variables]()
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val fileId = UUID.randomUUID()
      val addFileStatusInput = AddMultipleFileStatusesInput(List(AddFileStatusInput(fileId, "Upload", "Success")))
      val data = client.GraphqlData(Option(addMultipleFileStatuses.Data(List(addMultipleFileStatuses.AddMultipleFileStatuses(fileId, "Upload", "Success")))), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("addMultipleFileStatuses"))
          .willReturn(okJson(dataString))
      )

      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val result = controller
        .addFileStatus()
        .apply(
          FakeRequest(POST, s"/add-file-status")
            .withJsonBody(Json.parse(addFileStatusInput.asJson.noSpaces))
            .withCSRFToken
        )
      val response: String = contentAsString(result)
      val addFileStatusResponse = decode[List[addMultipleFileStatuses.AddMultipleFileStatuses]](response).toOption
      addFileStatusResponse.isDefined must be(true)
      addFileStatusResponse.get.head.fileId must be(addFileStatusInput.statuses.head.fileId)
      addFileStatusResponse.get.head.statusType must be(addFileStatusInput.statuses.head.statusType)
      addFileStatusResponse.get.head.statusValue must be(addFileStatusInput.statuses.head.statusValue)

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val result = controller
        .addFileStatus()
        .apply(
          FakeRequest(POST, s"/add-file-status")
            .withJsonBody(Json.parse("""{"bad": "data"}"""))
            .withCSRFToken
        )
      val exception: Throwable = result.failed.futureValue
      exception.getMessage must be("Incorrect data provided AnyContentAsJson({\"bad\":\"data\"})")
    }

    "throw an error if the API returns an error" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val fileId = UUID.randomUUID()
      val addFileStatusInput = AddMultipleFileStatusesInput(List(AddFileStatusInput(fileId, "Upload", "Success")))

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("addFileStatus"))
          .willReturn(serverError())
      )

      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val result = controller
        .addFileStatus()
        .apply(
          FakeRequest(POST, s"/add-file-status")
            .withJsonBody(Json.parse(addFileStatusInput.asJson.noSpaces))
            .withCSRFToken
        )
      val exception: Throwable = result.failed.futureValue
      exception.getMessage must startWith("Unexpected response from GraphQL API")
    }
  }

  "UploadController startUpload" should {
    "call the startUpload endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val client = graphQLConfiguration.getClient[startUpload.Data, startUpload.Variables]()
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val startUploadInput = StartUploadInput(consignmentId, "parent", Some(false))
      val data = client.GraphqlData(Option(startUpload.Data("ok")), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("startUpload"))
          .willReturn(okJson(dataString))
      )

      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val saveMetadataResponse = controller
        .startUpload()
        .apply(
          FakeRequest(POST, s"/start-upload")
            .withJsonBody(Json.parse(startUploadInput.asJson.noSpaces))
            .withCSRFToken
        )
      val response: String = contentAsString(saveMetadataResponse)
      response must be("ok")

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val saveMetadataResponse = controller
        .startUpload()
        .apply(
          FakeRequest(POST, s"/start-upload")
            .withJsonBody(Json.parse("""{"bad": "data"}"""))
            .withCSRFToken
        )
      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must be("Incorrect data provided AnyContentAsJson({\"bad\":\"data\"})")
    }

    "throw an error if the API returns an error" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val backendChecksService = new BackendChecksService(new InternalWSClient("http", 9007), app.configuration)
      val uploadService = new UploadService(graphQLConfiguration, applicationConfig)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(
        getAuthorisedSecurityComponents,
        graphQLConfiguration,
        getValidStandardUserKeycloakConfiguration,
        frontEndInfoConfiguration,
        consignmentService,
        uploadService,
        fileStatusService,
        backendChecksService
      )
      val startUploadInput = StartUploadInput(consignmentId, "parent", Some(false))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("startUpload"))
          .willReturn(serverError())
      )

      val saveMetadataResponse = controller
        .startUpload()
        .apply(
          FakeRequest(POST, s"/save-metadata")
            .withJsonBody(Json.parse(startUploadInput.asJson.noSpaces))
            .withCSRFToken
        )
      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must startWith("Unexpected response from GraphQL API")
    }
  }

  private def checkForExpectedPageContentOnMainUploadPage(pageAsString: String): Unit = {
    pageAsString must include(
      """<form id="file-upload-form" data-consignment-id="c2efd3e6-6664-4582-8c28-dcf891f60e68">"""
    )
    pageAsString must (include(
      """                            <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
        |                                Start upload
        |                            </button>""".stripMargin
    ) or include(
      """                                <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
        |                                    Start upload
        |                                </button>""".stripMargin
    ))
    pageAsString must include(
      s"""            <a class="govuk-button" href="/homepage" role="button" draggable="false" data-module="govuk-button">
         |                Return to start
         |            </a>""".stripMargin
    )
    // scalastyle:off line.size.limit
    pageAsString must include(
      """                    <p class="upload-progress-error-timeout__message" hidden>Your upload has timed out. Click 'Return to start' to begin a new transfer.</p>
        |                    <p class="upload-progress-error-authentication__message" hidden>You have been signed out. Click 'Return to start' to begin a new transfer.</p>
        |                    <p class="upload-progress-error-general__message" hidden>Click 'Return to start' to begin a new transfer.</p>""".stripMargin
    )
    // scalastyle:on line.size.limit
    pageAsString must include(
      """<div aria-labelledby="upload-records-progress-label" class="progress-display" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">"""
    )
  }

  private def checkForExpectedPageContentOnOtherUploadPages(pageAsString: String, userType: String = "standard", uploadStatus: String = "") = {
    if (userType == "judgment") {
      pageAsString must include("""<h1 class="govuk-heading-l">Uploading document</h1>""")
    } else {
      pageAsString must include("""<h1 class="govuk-heading-l">Uploading your records</h1>""")
    }

    if (uploadStatus == "Completed") {
      pageAsString must include(
        """<p class="govuk-body">Your records have been uploaded and saved. You cannot add additional files or folders to this transfer.</p>"""
      )
      pageAsString must include(
        """<p class="govuk-body">Click 'Continue' to proceed with your transfer.</p>"""
      )
    } else if (uploadStatus == "InProgress") {
      pageAsString must include(
        """            <h2 class="govuk-error-summary__title" id="error-summary-title">
          |              There is a problem
          |            </h2>""".stripMargin
      )
      pageAsString must include("<p>Your upload was interrupted and could not be completed.</p>")
      pageAsString must include("<p>Please return to the start to begin a new transfer.</p>")
      pageAsString must include(
        s"""        <a href="/homepage" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |          Return to start
           |        </a>""".stripMargin
      )
    }
  }
}
