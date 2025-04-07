package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.libs.Files
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers.{status => playStatus, _}
import play.api.test.{FakeHeaders, FakeRequest}
import services.{ConsignmentService, ConsignmentStatusService, DraftMetadataService, FileError, UploadService}
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.io.{BufferedWriter, File, FileWriter}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DraftMetadataUploadControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)
  val uploadFilename = "draft-metadata.csv"

  private val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataUploadController GET" should {
    "render 'draft metadata upload' page when 'blockDraftMetadataUpload' set to 'false'" in {

      val controller = instantiateDraftMetadataUploadController(blockDraftMetadataUpload = false)
      val draftMetadataUploadPage = controller
        .draftMetadataUploadPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/upload").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val pageAsString = contentAsString(draftMetadataUploadPage)

      playStatus(draftMetadataUploadPage) mustBe OK
      contentType(draftMetadataUploadPage) mustBe Some("text/html")
      pageAsString must include("<title>Upload a metadata CSV - Transfer Digital Records - GOV.UK</title>")
      pageAsString must include(s"""<a href="/consignment/$consignmentId/draft-metadata/prepare-metadata" class="govuk-back-link">Prepare your metadata</a>""")
      pageAsString must include("""<p class="govuk-body">Upload a <abbr title="Comma Separated Values">CSV</abbr> containing the record metadata.</p>""")
      pageAsString must include("""<details class="govuk-details govuk-!-margin-bottom-2" data-module="govuk-details">
                                  |  <summary class="govuk-details__summary">
                                  |    <span class="govuk-details__summary-text">How to save an Excel file as CSV</span>
                                  |  </summary>""".stripMargin)
      pageAsString must include("""<li>Save your file as Excel first (File > Save) before you save as CSV</li>
                                  |                                 <li>From the ‘Save as type’ dropdown, choose CSV UTF-8 (Comma delimited) (*.csv)</li>
                                  |                                 <li>Click Save</li>
                                  |                                 <li>Close the file, you are ready to upload</li>""".stripMargin)
      pageAsString must include(
        """<p class="govuk-body">Once uploaded, we will check your metadata for errors. There will be a chance to review and re-upload the metadata before completing the transfer.</p>"""
      )
      pageAsString must include("""<div class="govuk-warning-text">
                                  |                <span class="govuk-warning-text__icon" aria-hidden="true">!</span>
                                  |                <strong class="govuk-warning-text__text">
                                  |                    <span class="govuk-warning-text__assistive">Warning</span>
                                  |                    We can only accept metadata in a <abbr title="Comma Separated Values">CSV</abbr> UTF-8 file format.
                                  |                </strong>
                                  |            </div>""".stripMargin)
      pageAsString must include("""<button id="to-draft-metadata-checks" class="govuk-button" type="submit" data-module="govuk-button"  role="button">
                                  |                                Upload
                                  |                            </button>""".stripMargin)
    }

    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateDraftMetadataUploadController()
      val draftMetadataUploadPage = controller.draftMetadataUploadPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/upload").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(draftMetadataUploadPage)

      playStatus(draftMetadataUploadPage) mustBe OK
      contentType(draftMetadataUploadPage) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateDraftMetadataUploadController(securityComponents = getUnauthorisedSecurityComponents, blockDraftMetadataUpload = false)

      val draftMetadataUploadPage = controller
        .draftMetadataUploadPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/upload").withCSRFToken)

      playStatus(draftMetadataUploadPage) mustBe FOUND
      redirectLocation(draftMetadataUploadPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiateDraftMetadataUploadController(keycloakConfiguration = getValidJudgmentUserKeycloakConfiguration, blockDraftMetadataUpload = false)
      val draftMetadataUploadPage = controller
        .draftMetadataUploadPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/upload").withCSRFToken)

      playStatus(draftMetadataUploadPage) mustBe FORBIDDEN
    }

    "return forbidden for a TNA user" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiateDraftMetadataUploadController(keycloakConfiguration = getValidTNAUserKeycloakConfiguration(), blockDraftMetadataUpload = false)
      val draftMetadataUploadPage = controller
        .draftMetadataUploadPage(consignmentId)
        .apply(FakeRequest(GET, "/draft-metadata/upload").withCSRFToken)
      playStatus(draftMetadataUploadPage) mustBe FORBIDDEN
    }
  }

  "DraftMetadataUploadController saveDraftMetadata" should {
    "redirect to draft metadata checks page when upload successful" in {
      val uploadServiceMock = mock[UploadService]
      when(configuration.get[String]("draftMetadata.fileName")).thenReturn(uploadFilename)
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, any[Array[Byte]])).thenReturn(Future.successful(putObjectResponse))
      setUpdateConsignmentStatus(wiremockServer)

      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], anyString, any[Token])).thenReturn(Future.successful(true))
      val response = requestFileUpload(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 303

      val redirect = redirectLocation(response)
      redirect.getOrElse(s"incorrect redirect $redirect") must include regex "/consignment/*.*/draft-metadata/checks"
    }

    "render error page when upload unsuccessful when no file uploaded" in {
      val uploadServiceMock = mock[UploadService]
      setConsignmentReferenceResponse(wiremockServer)
      when(configuration.get[String]("draftMetadata.fileName")).thenReturn("wrong name")
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, any[Array[Byte]])).thenReturn(Future.successful(putObjectResponse))

      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], anyString, any[Token])).thenReturn(Future.successful(true))
      val response = requestFileUploadWithoutFile(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 200

      contentAsString(response) must include("There is a problem")
    }

    "render error page when upload success but trigger fails" in {
      val uploadServiceMock = mock[UploadService]
      setConsignmentReferenceResponse(wiremockServer)
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
      when(configuration.get[String]("draftMetadata.fileName")).thenReturn(uploadFilename)
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, any[Array[Byte]])).thenReturn(Future.successful(putObjectResponse))

      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], anyString, any[Token])).thenReturn(Future.failed(new RuntimeException("Trigger failed")))
      val response = requestFileUpload(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 200

      contentAsString(response) must include("There is a problem")
    }
  }

  private def instantiateDraftMetadataUploadController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true,
      uploadService: UploadService = mock[UploadService],
      draftMetadataService: DraftMetadataService = mock[DraftMetadataService]
  ): DraftMetadataUploadController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    when(draftMetadataService.getErrorTypeFromErrorJson(any[UUID])).thenReturn(Future.successful(FileError.NONE))
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new DraftMetadataUploadController(
      securityComponents,
      keycloakConfiguration,
      frontEndInfoConfiguration,
      consignmentService,
      uploadService,
      draftMetadataService,
      consignmentStatusService,
      applicationConfig
    )
  }

  private def requestFileUploadWithoutFile(uploadServiceMock: UploadService, draftMetadataServiceMock: DraftMetadataService): Future[Result] = {
    val controller = instantiateDraftMetadataUploadController(blockDraftMetadataUpload = false, uploadService = uploadServiceMock, draftMetadataService = draftMetadataServiceMock)

    val formData = MultipartFormData(dataParts = Map("" -> Seq("dummy data")), files = Seq.empty[FilePart[Files.TemporaryFile]], badParts = Seq())

    val request = FakeRequest(POST, "/consignment/1234567/draft-metadata").withCSRFToken.withBody(formData).withHeaders(FakeHeaders())
    controller.saveDraftMetadata(UUID.randomUUID()).apply(request)
  }

  private def requestFileUpload(uploadServiceMock: UploadService, draftMetadataServiceMock: DraftMetadataService): Future[Result] = {
    val controller = instantiateDraftMetadataUploadController(blockDraftMetadataUpload = false, uploadService = uploadServiceMock, draftMetadataService = draftMetadataServiceMock)

    val csvUploadFile = new File("test_metadata_upload.csv")
    val fileWriter = new BufferedWriter(new FileWriter(csvUploadFile))
    fileWriter.write("yo,ho\n1,2")
    fileWriter.close()
    csvUploadFile.deleteOnExit()

    val tempFile = SingletonTemporaryFileCreator.create(csvUploadFile.toPath)
    val file = FilePart("upload", "hello.txt", Option("text/plain"), tempFile)
    val formData = MultipartFormData(dataParts = Map("" -> Seq("dummy data")), files = Seq(file), badParts = Seq())

    val request = FakeRequest(POST, "/consignment/1234567/draft-metadata").withCSRFToken.withBody(formData).withHeaders(FakeHeaders())

    controller.saveDraftMetadata(UUID.randomUUID()).apply(request)
  }
}
