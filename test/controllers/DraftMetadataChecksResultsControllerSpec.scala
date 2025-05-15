package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignment.getConsignment.{GetConsignment => gc}
import org.apache.pekko.util.ByteString
import org.dhatim.fastexcel.reader.{ReadableWorkbook, Row, Sheet}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.http.HttpVerbs.GET
import play.api.i18n.DefaultMessagesApi
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsBytes, contentAsString, defaultAwaitTimeout, status => playStatus, _}
import services.FileError.FileError
import services.Statuses.{CompletedValue, CompletedWithIssuesValue, DraftMetadataType, FailedValue}
import services.{ConsignmentService, ConsignmentStatusService, DraftMetadataService, Error, ErrorFileData, FileError, ValidationErrors}
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.validation.Metadata

import java.io.ByteArrayInputStream
import java.util.{Properties, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Using

class DraftMetadataChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)
  val draftMetaDataService: DraftMetadataService = mock[DraftMetadataService]

  private val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataChecksResultsController GET" should {
    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateController()
      val additionalMetadataEntryMethodPage =
        controller.draftMetadataChecksResultsPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden for a TNA user" in {
      val controller = instantiateController(blockDraftMetadataUpload = false, keycloakConfiguration = getValidTNAUserKeycloakConfiguration())
      val additionalMetadataEntryMethodPage =
        controller.draftMetadataChecksResultsPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      playStatus(additionalMetadataEntryMethodPage) mustBe FORBIDDEN
    }
  }

  "DraftMetadataChecksResultsController should render the page with the correct status" should {
    s"render the draftMetadataResults page when the status is completed" in {
      val controller = instantiateController(blockDraftMetadataUpload = false)
      val additionalMetadataEntryMethodPage = controller
        .draftMetadataChecksResultsPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")
      val consignmentStatuses = List(
        gc.ConsignmentStatuses(DraftMetadataType.id, CompletedValue.value)
      )
      val uploadedFileName = "file name.csv"
      setConsignmentDetailsResponse(wiremockServer, consignmentStatuses = consignmentStatuses, clientSideDraftMetadataFileName = Some(uploadedFileName))

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>Results of CSV Checks - Transfer Digital Records - GOV.UK</title>")
      pageAsString must include(
        s"""<h1 class="govuk-heading-l">
             |                Results of your metadata checks
             |            </h1>""".stripMargin
      )
      pageAsString must include(
        s"""<p class="govuk-body">The metadata in your uploaded <abbr title="Comma Separated Values">CSV</abbr> has been successfully imported.</p>"""
      )
      pageAsString must include(
        s"""<div class="govuk-summary-list__row">
           |    <dt class="govuk-summary-list__key">
           |        Status
           |    </dt>
           |    <dd class="govuk-summary-list__value">
           |        <strong class="govuk-tag govuk-tag--blue">
           |        IMPORTED
           |        </strong>
           |    </dd>
           |</div>
           |<div class="govuk-summary-list__row">
           |    <dt class="govuk-summary-list__key">
           |        Uploaded file
           |    </dt>
           |    <dd class="govuk-summary-list__value">
           |        <code>file name.csv</code>
           |    </dd>
           |</div>""".stripMargin
      )
      pageAsString must include(
        s"""<p class="govuk-body">If you need to make any changes to your metadata you can return to <a class="govuk-link" href=/consignment/$consignmentId/draft-metadata/upload>
          |                upload a metadata CSV</a>, otherwise continue with your transfer.</p>""".stripMargin
      )
      pageAsString must include(
        s"""            <div class="govuk-button-group">
             |                <a href="/consignment/$consignmentId/additional-metadata/download-metadata" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
             |                    Next
             |                </a>
             |            </div>""".stripMargin
      )

    }
  }

  "DraftMetadataChecksResultsController should render the error page with error report download button" should {
    val draftMetadataStatuses = Table(
      ("status", "fileError", "detailsMessage", "actionMessage"),
      (
        CompletedWithIssuesValue.value,
        FileError.SCHEMA_VALIDATION,
        "We found validation errors in the uploaded metadata.",
        "Download the report below for details on individual validation errors."
      )
    )
    forAll(draftMetadataStatuses) { (statusValue, fileError, detailsMessage, actionMessage) =>
      {
        s"render the draftMetadataResults page when the status is $statusValue" in {
          val controller = instantiateController(blockDraftMetadataUpload = false, fileError = fileError)
          val additionalMetadataEntryMethodPage = controller
            .draftMetadataChecksResultsPage(consignmentId)
            .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
          setConsignmentTypeResponse(wiremockServer, "standard")
          val uploadedFileName = "file name.csv"
          val consignmentStatuses = List(
            gc.ConsignmentStatuses(DraftMetadataType.id, statusValue)
          )
          setConsignmentDetailsResponse(wiremockServer, consignmentStatuses = consignmentStatuses, clientSideDraftMetadataFileName = Some(uploadedFileName))

          val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

          playStatus(additionalMetadataEntryMethodPage) mustBe OK
          contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
          pageAsString must include("<title>Results of CSV Checks - Transfer Digital Records - GOV.UK</title>")
          pageAsString must include(
            """<h1 class="govuk-heading-l">
            |    Results of your metadata checks
            |</h1>""".stripMargin
          )
          pageAsString must include(detailsMessage)
          pageAsString must include(actionMessage)
          pageAsString must include(
            """<p class="govuk-body">The report below contains detailed errors with reference to the file path and column title, which caused the error.</p>"""
          )
          pageAsString must include(
            """<div class="da-alert da-alert--default">
              |    <div class="da-alert__content">
              |        <h2 class="da-alert__heading da-alert__heading--s">
              |            Leaving and returning to this transfer
              |        </h2>
              |        <p class="govuk-body">
              |            You can sign out and return to continue working on this transfer at any time from <a class='govuk-link' href='/view-transfers'>View transfers</a>.
              |        </p>
              |    </div>
              |</div>
              |""".stripMargin
          )
          pageAsString must include(
            s"""<a class="govuk-button govuk-button--secondary download-metadata" href="/consignment/$consignmentId/draft-metadata/download-report">
               |    <span aria-hidden="true" class="tna-button-icon">
               |        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 23 23">
               |            <path fill="#020202" d="m11.5 16.75-6.563-6.563 1.838-1.903 3.412 3.413V1h2.626v10.697l3.412-3.413 1.837 1.903L11.5 16.75ZM3.625 22c-.722 0-1.34-.257-1.853-.77A2.533 2.533 0 0 1 1 19.375v-3.938h2.625v3.938h15.75v-3.938H22v3.938c0 .722-.257 1.34-.77 1.855a2.522 2.522 0 0 1-1.855.77H3.625Z"></path>
               |        </svg>
               |    </span>
               |    Download error report
               |</a>
               |""".stripMargin
          )
        }
      }
    }
  }

  "DraftMetadataChecksResultsController should render the error page with no error download for some errors" should {
    val draftMetadataStatuses = Table(
      ("status", "fileError", "detailsMessage", "actionMessage", "affectedProperties"),
      (
        FailedValue.value,
        FileError.UNKNOWN,
        "An unknown error was identified.",
        "Please contact your Digital Transfer Advisor on <a href=\"mailto:tdr@nationalarchives.gov.uk\">tdr@nationalarchives.gov.uk</a> quoting the Consignment Reference.",
        Set[String]()
      ),
      (
        CompletedWithIssuesValue.value,
        FileError.UTF_8,
        "The metadata file was not a CSV in UTF-8 format.",
        s"Ensure that you save your Excel file as file type 'CSV UTF-8 (comma separated)'.",
        Set[String]()
      ),
      (
        CompletedWithIssuesValue.value,
        FileError.SCHEMA_REQUIRED,
        "There was at least one missing column in your metadata file. The metadata file must contain specific column headers.",
        "Add the following column headers to your metadata file and re-upload.",
        Set("Closure Status", "Description")
      ),
      (
        CompletedWithIssuesValue.value,
        FileError.DUPLICATE_HEADER,
        "There was at least one duplicate column in your metadata file.",
        "Ensure there is only one of the following duplicated columns.",
        Set("Closure Status", "Description")
      )
    )
    forAll(draftMetadataStatuses) { (statusValue, fileError, detailsMessage, actionMessage, affectedProperties) =>
      {
        s"render the draftMetadataResults page when the status is $statusValue and error is $fileError" in {
          val controller = instantiateController(blockDraftMetadataUpload = false, fileError = fileError, affectedProperties = affectedProperties)
          val additionalMetadataEntryMethodPage = controller
            .draftMetadataChecksResultsPage(consignmentId)
            .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)

          setConsignmentTypeResponse(wiremockServer, "standard")
          val uploadedFileName = "file name.csv"
          val consignmentStatuses = List(
            gc.ConsignmentStatuses(DraftMetadataType.id, statusValue)
          )
          setConsignmentDetailsResponse(wiremockServer, consignmentStatuses = consignmentStatuses, clientSideDraftMetadataFileName = Some(uploadedFileName))

          val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

          playStatus(additionalMetadataEntryMethodPage) mustBe OK
          contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
          pageAsString must include("<title>Results of CSV Checks - Transfer Digital Records - GOV.UK</title>")
          pageAsString must include(
            s"""<h1 class="govuk-heading-l">
             |    Results of your metadata checks
             |</h1>""".stripMargin
          )
          pageAsString must include(detailsMessage)
          pageAsString must include(actionMessage)
          affectedProperties.foreach(p => pageAsString must include(s"<li>$p</li>"))
          pageAsString must include(
            """<div class="da-alert da-alert--default">
              |    <div class="da-alert__content">
              |        <h2 class="da-alert__heading da-alert__heading--s">
              |            Leaving and returning to this transfer
              |        </h2>
              |        <p class="govuk-body">
              |            You can sign out and return to continue working on this transfer at any time from <a class='govuk-link' href='/view-transfers'>View transfers</a>.
              |        </p>
              |    </div>
              |</div>
              |""".stripMargin
          )
        }
      }
    }
  }

  "downloadErrorReport" should {
    "download the excel file with error data" in {

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val error = Error("BASE_SCHEMA", "FOI exemption code", "enum", "BASE_SCHEMA.foi_exemption_code.enum")
      val metadata = List(Metadata("FOI exemption code", "abcd"), Metadata("Filepath", "/aa/bb/faq"))
      val errorFileData = ErrorFileData(consignmentId, date = "2024-12-12", FileError.SCHEMA_VALIDATION, List(ValidationErrors("/aa/bb/faq", Set(error), metadata)))
      val response = instantiateController(errorFileData = Some(errorFileData))
        .downloadErrorReport(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/download-report"))

      val responseByteArray: ByteString = contentAsBytes(response)
      val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
      val wb: ReadableWorkbook = new ReadableWorkbook(bufferedSource)
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length must equal(2)

      rows.head.getCell(0).asString must equal("Filepath")
      rows.head.getCell(1).asString must equal("Column")
      rows.head.getCell(2).asString must equal("Value")
      rows.head.getCell(3).asString must equal("Error Message")

      rows(1).getCell(0).asString must equal("/aa/bb/faq")
      rows(1).getCell(1).asString must equal("FOI exemption code")
      rows(1).getCell(2).asString must equal("abcd")
      rows(1).getCell(3).asString must equal("BASE_SCHEMA.foi_exemption_code.enum")
    }

    "download the excel file without error data when the error type is not SCHEMA_VALIDATION" in {

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val error = Error("FILE_VALIDATION", "draftmetadata.csv", "INVALID_CSV", "")
      val errorFileData = ErrorFileData(consignmentId, date = "2024-12-12", FileError.INVALID_CSV, List(ValidationErrors("assetId", Set(error), Nil)))

      when(draftMetaDataService.getErrorReport(any[UUID])).thenReturn(Future.successful(errorFileData))

      val response = instantiateController().downloadErrorReport(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/download-report"))

      val responseByteArray: ByteString = contentAsBytes(response)
      val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
      val wb: ReadableWorkbook = new ReadableWorkbook(bufferedSource)
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length must equal(1)

      rows.head.getCell(0).asString must equal("Filepath")
      rows.head.getCell(1).asString must equal("Column")
      rows.head.getCell(2).asString must equal("Value")
      rows.head.getCell(3).asString must equal("Error Message")
    }

    "download the excel file with row validation errors at the top where present" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val schemaError = Error("BASE_SCHEMA", "FOI exemption code", "enum", "BASE_SCHEMA.foi_exmption_code.enum")
      val rowValidationError = Error("ROW_VALIDATION", "", "unknown", "This file was listed in your metadata file but does not match to one of your uploaded files")
      val metadata = List(Metadata("FOI exemption code", "abcd"), Metadata("Filepath", "/aa/bb/faq"))
      val errorFileData =
        ErrorFileData(consignmentId, date = "2024-12-12", FileError.SCHEMA_VALIDATION, List(ValidationErrors("/aa/bb/faq", Set(schemaError, rowValidationError), metadata)))
      val response = instantiateController(errorFileData = Some(errorFileData))
        .downloadErrorReport(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/download-report"))

      val responseByteArray: ByteString = contentAsBytes(response)
      val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
      val wb: ReadableWorkbook = new ReadableWorkbook(bufferedSource)
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length must equal(3)

      rows.head.getCell(0).asString must equal("Filepath")
      rows.head.getCell(1).asString must equal("Column")
      rows.head.getCell(2).asString must equal("Value")
      rows.head.getCell(3).asString must equal("Error Message")

      rows(1).getCell(0).asString must equal("/aa/bb/faq")
      rows(1).getCell(1).asString must equal("")
      rows(1).getCell(2).asString must equal("")
      rows(1).getCell(3).asString must equal("This file was listed in your metadata file but does not match to one of your uploaded files")

      rows(2).getCell(0).asString must equal("/aa/bb/faq")
      rows(2).getCell(1).asString must equal("FOI exemption code")
      rows(2).getCell(2).asString must equal("abcd")
      rows(2).getCell(3).asString must equal("BASE_SCHEMA.foi_exmption_code.enum")
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true,
      fileError: FileError.FileError = FileError.UNKNOWN,
      affectedProperties: Set[String] = Set(),
      errorFileData: Option[ErrorFileData] = None
  ): DraftMetadataChecksResultsController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val mockedErrorFileData = if (errorFileData.isDefined) { errorFileData.get }
    else mockErrorFileData(fileError, affectedProperties)
    when(draftMetaDataService.getErrorReport(any[UUID])).thenReturn(Future.successful(mockedErrorFileData))

    val properties = new Properties()
    Using(Source.fromFile("conf/messages")) { source =>
      properties.load(source.bufferedReader())
    }
    import scala.jdk.CollectionConverters._
    val map = properties.asScala.toMap
    val testMessages = Map(
      "default" -> map
    )
    val messagesApi = new DefaultMessagesApi(testMessages)

    new DraftMetadataChecksResultsController(
      securityComponents,
      keycloakConfiguration,
      consignmentService,
      applicationConfig,
      draftMetaDataService,
      messagesApi
    )
  }

  private def mockErrorFileData(fileError: FileError = FileError.UNKNOWN, affectedProperties: Set[String] = Set()): ErrorFileData = {
    if (affectedProperties.nonEmpty) {
      val validationErrors = {
        val errors = affectedProperties.map(p => {
          val convertedProperty = p.replaceAll(" ", "_").toLowerCase()
          Error("SCHEMA_REQUIRED", p, "required", s"SCHEMA_REQUIRED.$convertedProperty.required")
        })
        ValidationErrors(UUID.randomUUID().toString, errors)
      }
      ErrorFileData(consignmentId, "", fileError, List(validationErrors))

    } else {
      ErrorFileData(consignmentId, "", fileError, List())
    }
  }
}
