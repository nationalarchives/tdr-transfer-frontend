package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment => gcs}
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
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
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
      setConsignmentReferenceResponse(wiremockServer)
      val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
      val consignmentStatuses = List(
        gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, CompletedValue.value, someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

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
        s"""          <dl class="govuk-summary-list">
            |                <div class="govuk-summary-list__row">
            |                    <dt class="govuk-summary-list__key">
            |                        Status
            |                    </dt>
            |                    <dd class="govuk-summary-list__value">
            |                        <strong class="govuk-tag govuk-tag--${DraftMetadataProgress("IMPORTED", "blue").colour}">
            |                            ${DraftMetadataProgress("IMPORTED", "blue").value}
            |                        </strong>
            |                    </dd>
            |                </div>
            |            </dl>""".stripMargin
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
          setConsignmentReferenceResponse(wiremockServer)
          val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
          val consignmentStatuses = List(
            gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, statusValue, someDateTime, None)
          )
          setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

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
          pageAsString must include("""<p class="govuk-body">The report below contains details about issues found.</p>""")
          pageAsString must include(
            s"""<a class="govuk-button govuk-button--secondary govuk-!-margin-bottom-8 download-metadata" href="/consignment/$consignmentId/draft-metadata/download-report">
               |    <span aria-hidden="true" class="tna-button-icon">
               |        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 23 23">
               |            <path fill="#020202" d="m11.5 16.75-6.563-6.563 1.838-1.903 3.412 3.413V1h2.626v10.697l3.412-3.413 1.837 1.903L11.5 16.75ZM3.625 22c-.722 0-1.34-.257-1.853-.77A2.533 2.533 0 0 1 1 19.375v-3.938h2.625v3.938h15.75v-3.938H22v3.938c0 .722-.257 1.34-.77 1.855a2.522 2.522 0 0 1-1.855.77H3.625Z"></path>
               |        </svg>
               |    </span>
               |    Download report
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
        "Require details message for draftMetadata.validation.details.",
        "Require action message for draftMetadata.validation.action.",
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
        "Add the following column headers to your metadata file and re-load.",
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
          setConsignmentReferenceResponse(wiremockServer)
          val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
          val consignmentStatuses = List(
            gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, statusValue, someDateTime, None)
          )
          setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

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
        }
      }
    }

    "downloadErrorReport" should {
      "download the excel file with error data" in {

        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentReferenceResponse(wiremockServer)
        val error = Error("BASE_SCHEMA", "FOI exemption code", "enum", "BASE_SCHEMA.foi_exmption_code.enum")
        val metadata = List(Metadata("FOI exemption code", "abcd"), Metadata("Filepath", "/aa/bb/faq"))
        val errorFileData = ErrorFileData(consignmentId, date = "2024-12-12", FileError.SCHEMA_VALIDATION, List(ValidationErrors("assetId", Set(error), metadata)))
        val response = instantiateController(errorFileData = Some(errorFileData))
          .downloadErrorReport(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/download-report"))

        val responseByteArray: ByteString = contentAsBytes(response)
        val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
        val wb: ReadableWorkbook = new ReadableWorkbook(bufferedSource)
        val ws: Sheet = wb.getFirstSheet
        val rows: List[Row] = ws.read.asScala.toList

        rows.length must equal(2)

        rows.head.getCell(0).asString must equal("Filepath")
        rows.head.getCell(1).asString must equal("Field")
        rows.head.getCell(2).asString must equal("Value")
        rows.head.getCell(3).asString must equal("Error Message")

        rows(1).getCell(0).asString must equal("/aa/bb/faq")
        rows(1).getCell(1).asString must equal("FOI exemption code")
        rows(1).getCell(2).asString must equal("abcd")
        rows(1).getCell(3).asString must equal("BASE_SCHEMA.foi_exmption_code.enum")
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
        rows.head.getCell(1).asString must equal("Field")
        rows.head.getCell(2).asString must equal("Value")
        rows.head.getCell(3).asString must equal("Error Message")
      }
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
      consignmentStatusService,
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
