package controllers

import akka.util.ByteString
import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.{Configuration, libs}
import play.api.Play.materializer
import play.api.libs.Files
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.Files.TemporaryFile.temporaryFileToFile
import play.api.libs.streams.Accumulator
import play.api.mvc.{MultipartFormData, Result}
import play.api.mvc.MultipartFormData.FilePart
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{status => playStatus, _}
import play.libs.Files
import play.libs.Files.TemporaryFile
import services.{ConsignmentService, UploadService}
import software.amazon.awssdk.http.{SdkHttpFullResponse, SdkHttpResponse}
import software.amazon.awssdk.services.s3.model.{PutObjectResponse, S3Error}
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import testUtils.FrontEndTestHelper

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.StandardCopyOption
import java.util.UUID
import scala.concurrent.ExecutionContext

class DraftMetadataUploadControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
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
      pageAsString must include("<title>[WIP] Draft Metadata Upload - Transfer Digital Records - GOV.UK</title>")
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

    "render next page when upload successful" in {
      val uploadServiceMock = mock[UploadService]
      val r: SdkHttpFullResponse = SdkHttpResponse.builder().statusCode(500).build()
      val putObjectResponse = PutObjectResponse.builder().eTag("testEtag"). .build()
      val completedUpload = IO(CompletedUpload.builder().response(putObjectResponse).build())

      when(uploadServiceMock.uploadDraftMetadata(anyString, anyString, anyString)).thenReturn(completedUpload)

      val controller = instantiateDraftMetadataUploadController(blockDraftMetadataUpload = false, uploadService = uploadServiceMock)

      val csvUploadFile = new File("my_csv.csv")
      val fileWriter = new BufferedWriter(new FileWriter(csvUploadFile))
      fileWriter.write("yo,ho\n1,2")
      fileWriter.close()
      val tempFile = SingletonTemporaryFileCreator.create(csvUploadFile.toPath)
      csvUploadFile.deleteOnExit()
      val file = FilePart("upload", "hello.txt", Option("text/plain"), tempFile)
      val formData = MultipartFormData(dataParts = Map("" -> Seq("dummydata")), files = Seq(file), badParts = Seq())

      val request = FakeRequest(POST, "/consignment/1234567/draft-metadata").withBody(formData).withHeaders(FakeHeaders())
      val responseStatus  = playStatus(controller.saveDraftMetadata(UUID.randomUUID()).apply(request))

      responseStatus mustBe 303

    }
  }

  private def instantiateDraftMetadataUploadController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true,
      uploadService: UploadService = mock[UploadService]
  ): DraftMetadataUploadController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new DraftMetadataUploadController(securityComponents, keycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, applicationConfig)
  }
}
