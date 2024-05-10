package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers.{status => playStatus, _}
import play.api.test.{FakeHeaders, FakeRequest}
import services.{ConsignmentService, DraftMetadataService, UploadService}
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import testUtils.FrontEndTestHelper

import java.io.{BufferedWriter, File, FileWriter}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DraftMetadataUploadControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val uploadFileName: String = "draft-metadata.csv"
  val wiremockServer = new WireMockServer(9006)

  private val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataUploadControllerSpec GET" should {
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

    "redirect to draft metadata checks page when upload successful" in {
      val uploadServiceMock = mock[UploadService]
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, anyString)).thenReturn(Future.successful(putObjectResponse))

      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], uploadFileName, anyString)).thenReturn(Future.successful(true))
      val response = requestFileUpload(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 303

      val redirect = redirectLocation(response)
      redirect.getOrElse(s"incorrect redirect $redirect") must include regex "/consignment/*.*/draft-metadata/checks"
    }

    "render error page when upload unsuccessful" in {
      val uploadServiceMock = mock[UploadService]
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, anyString))
        .thenReturn(Future.failed(new RuntimeException("Upload failed")))
      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], uploadFileName, anyString)).thenReturn(Future.successful(true))
      val response = requestFileUpload(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 500

      contentAsString(response) must include("Upload failed")
    }

    "render error page when upload success but trigger fails" in {
      val uploadServiceMock = mock[UploadService]
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, anyString)).thenReturn(Future.successful(putObjectResponse))

      val draftMetadataServiceMock = mock[DraftMetadataService]
      when(draftMetadataServiceMock.triggerDraftMetadataValidator(any[UUID], uploadFileName, anyString)).thenReturn(Future.failed(new RuntimeException("Trigger failed")))
      val response = requestFileUpload(uploadServiceMock, draftMetadataServiceMock)

      playStatus(response) mustBe 500

      contentAsString(response) must include("Trigger failed")
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
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new DraftMetadataUploadController(
      securityComponents,
      keycloakConfiguration,
      frontEndInfoConfiguration,
      consignmentService,
      uploadService,
      draftMetadataService,
      applicationConfig
    )
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

    val request = FakeRequest(POST, "/consignment/1234567/draft-metadata").withBody(formData).withHeaders(FakeHeaders())
    controller.saveDraftMetadata(UUID.randomUUID()).apply(request)
  }
}
