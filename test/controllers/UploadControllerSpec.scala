package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, serverError, urlEqualTo}
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddFileStatus
import graphql.codegen.AddFileStatus.addFileStatus
import graphql.codegen.AddFilesAndMetadata.addFilesAndMetadata
import graphql.codegen.AddFilesAndMetadata.addFilesAndMetadata.AddFilesAndMetadata
import graphql.codegen.StartUpload.startUpload
import graphql.codegen.UpdateConsignmentStatus.updateConsignmentStatus
import graphql.codegen.types.{AddFileAndMetadataInput, AddFileStatusInput, ClientSideMetadataInput, ConsignmentStatusInput, StartUploadInput}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, redirectLocation, status, _}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import services.{ConsignmentService, FileStatusService, GoogleDriveService, UploadService}
import play.api.libs.json._

import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures._
import play.api.libs.ws.WSClient

import scala.jdk.CollectionConverters._


class UploadControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  val googleDriveService = new GoogleDriveService(mock[WSClient], keycloakConfiguration, ec)

  "UploadController GET upload" should {
    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been signed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has not been agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "redirect to the transfer agreement page if the transfer agreement for that consignment has been partially agreed to" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("InProgress"))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, "/consignment/1/upload").withCSRFToken)
      status(uploadPage) mustBe SEE_OTHER
      redirectLocation(uploadPage).get must equal(s"/consignment/$consignmentId/transfer-agreement-continued")
    }

    "show the standard upload page if the transfer agreement for that consignment has been agreed to in full" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard", pageRequiresAwsServices = true)
      checkForExpectedPageContentOnMainUploadPage(uploadPageAsString)
      uploadPageAsString must include("<title>Upload your records</title>")
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Upload your records</h1>""")
      uploadPageAsString must include(
        """<p class="govuk-body">You can only upload one folder to be transferred. """ +
          """If your folder contains files that we cannot accept, you may have to start again.</p>"""
      )
      uploadPageAsString must include("There is no limit to the size of the files but larger files may take longer to be uploaded and checked.")
      uploadPageAsString must include(
        """We cannot accept files or folders which are password protected, zipped or contain slashes (/ and \) in the name.""" +
          " Remove any thumbnail images (thumb dbs) and executable files (.exe) from the records before uploading."
      )
      uploadPageAsString must include(
        """<p id="success-message-text" class="success-message">The folder "<span id="folder-name"></span>"""" +
          """ (containing <span id="folder-size"></span>) has been selected </p>"""
      )
      uploadPageAsString must include(
        """|                                <div class="drag-and-drop__dropzone">
           |                                    <input type="file" id="file-selection" name="files"
           |                                    class="govuk-file-upload drag-and-drop__input" webkitdirectory""".stripMargin
      )
      uploadPageAsString must include(
        """|                                    accept="*" aria-hidden="true"
           |                                    >
           |                                    <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single folder here or</p>
           |                                    <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
           |                                        Choose folder""".stripMargin
      )
      uploadPageAsString must include("For more information on what metadata will be captured during the upload please visit our")
      uploadPageAsString must include(
        """<a href="/faq#metadata-captured" target="_blank" rel="noopener noreferrer" class="govuk-link">FAQ (opens in new tab)</a>"""
      )
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Uploading records</h1>""")
      uploadPageAsString must include(
        """|                <h2 class="govuk-error-summary__title" id="error-summary-title">
           |                    There is a problem
           |                </h2>
           |                <div class="govuk-error-summary__body">
           |                    <p>Some or all of your files failed to upload.</p>""".stripMargin
      )

      uploadPageAsString must include(
        """<p class="govuk-body">Do not close your browser window while your files are being uploaded. This could take a few minutes.</p>"""
      )
    }

    "render the 'upload in progress' page if a standard upload is in progress" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val uploadStatus = "InProgress"

      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some(uploadStatus))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, uploadStatus = uploadStatus)
    }

    "render 'upload is complete' page if a standard upload has completed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val uploadStatus = "Completed"

      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some(uploadStatus))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.uploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "standard")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, uploadStatus = uploadStatus)
      uploadPageAsString must include(
        s"""      <a href="/consignment/$consignmentId/file-checks" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin
      )
    }

    "show the judgment upload page for judgments" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      headers(uploadPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment", pageRequiresAwsServices = true)
      checkForExpectedPageContentOnMainUploadPage(uploadPageAsString)
      uploadPageAsString must include("<title>Upload your judgment</title>")
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Upload judgment</h1>""")
      uploadPageAsString must include("You may now upload the judgment you wish to transfer. You can only upload one file.")
      uploadPageAsString must include("We only accept Microsoft Word files (.docx).")
      uploadPageAsString must include(
        """<form id="file-upload-form" data-consignment-id="c2efd3e6-6664-4582-8c28-dcf891f60e68">"""
      )
      uploadPageAsString must include(
        """<p id="success-message-text" class="success-message">The file "<span id="file-name"></span>" has been selected </p>"""
      )
      uploadPageAsString must include(
        """|                                <div class="drag-and-drop__dropzone">
           |                                    <input type="file" id="file-selection" name="files"
           |                                    class="govuk-file-upload drag-and-drop__input"""".stripMargin
      )
      uploadPageAsString must include(
        """|                                    accept=".docx" aria-hidden="true"
           |                                    >
           |                                    <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single file here or</p>
           |                                    <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
           |                                        Choose file""".stripMargin
      )
      uploadPageAsString must include("""<h1 class="govuk-heading-l">Uploading judgment</h1>""")
      uploadPageAsString must include(
        """|                <h2 class="govuk-error-summary__title" id="error-summary-title">
           |                    There is a problem
           |                </h2>
           |                <div class="govuk-error-summary__body">
           |                    <p>Your file has failed to upload.</p>""".stripMargin
      )
      uploadPageAsString must include(
        """<p class="govuk-body">Do not close your browser window while your file is being uploaded. This could take a few minutes.</p>""")
    }

    "render the 'upload in progress' page if a judgment file upload is in progress" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val uploadStatus = "InProgress"
      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some(uploadStatus))
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, userType = "judgment", uploadStatus = uploadStatus)
    }

    "render the judgment 'upload is complete' page if the upload has completed" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidJudgmentUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val uploadStatus = "Completed"
      setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some(uploadStatus))
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val uploadPage = controller.judgmentUploadPage(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
      val uploadPageAsString = contentAsString(uploadPage)

      status(uploadPage) mustBe OK
      uploadPageAsString must include(
        s"""      <a href="/judgment/$consignmentId/file-checks" role="button" draggable="false" class="govuk-button govuk-button--primary">
           |        Continue
           |      </a>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(uploadPageAsString, userType = "judgment")
      checkForExpectedPageContentOnOtherUploadPages(uploadPageAsString, userType = "judgment", uploadStatus = uploadStatus)
    }
  }

  forAll(userChecks) { (user, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

        setConsignmentStatusResponse(app.configuration, wiremockServer)

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload in progress page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

        setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some("InProgress"))

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/consignment/$consignmentId/upload").withCSRFToken)
        }
        status(uploadPage) mustBe FORBIDDEN
      }
    }

    s"The $url upload has completed page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val uploadService = new UploadService(graphQLConfiguration)
        val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
        val fileStatusService = new FileStatusService(graphQLConfiguration)
        val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
        val controller = new UploadController(getAuthorisedSecurityComponents,
          graphQLConfiguration, user, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)

        setConsignmentStatusResponse(app.configuration, wiremockServer, transferAgreementStatus = Some("Completed"), uploadStatus = Some("Completed"))

        val uploadPage = url match {
          case "judgment" =>
            setConsignmentTypeResponse(wiremockServer, "standard")
            controller.judgmentUploadPage(consignmentId)
              .apply(FakeRequest(GET, s"/judgment/$consignmentId/upload").withCSRFToken)
          case "consignment" =>
            setConsignmentTypeResponse(wiremockServer, "judgment")
            controller.uploadPage(consignmentId)
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
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val fileId = UUID.randomUUID()
      val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, 1) :: Nil
      val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(consignmentId, clientSideMetadataInput, Some(Nil))
      val data = client.GraphqlData(Option(addFilesAndMetadata.Data(List(AddFilesAndMetadata(fileId, 0)))), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("addFilesAndMetadata"))
        .willReturn(okJson(dataString)))

      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.saveClientMetadata()
        .apply(FakeRequest(POST, s"/save-metadata")
          .withJsonBody(Json.parse(addFileAndMetadataInput.asJson.noSpaces))
          .withCSRFToken
        )
      val response: String = contentAsString(saveMetadataResponse)
      val metadataResponse = decode[List[AddFilesAndMetadata]](response).toOption
      metadataResponse.isDefined must be(true)
      metadataResponse.get.head.fileId must be(fileId)
      metadataResponse.get.head.matchId mustBe 0

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.saveClientMetadata()
        .apply(FakeRequest(POST, s"/save-metadata")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, 1) :: Nil
      val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(UUID.randomUUID(), clientSideMetadataInput, Some(Nil))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("addFilesAndMetadata"))
        .willReturn(serverError()))

      val saveMetadataResponse = controller.saveClientMetadata()
        .apply(FakeRequest(POST, s"/save-metadata")
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
      val client = graphQLConfiguration.getClient[addFileStatus.Data, addFileStatus.Variables]()
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val fileId = UUID.randomUUID()
      val addFileStatusInput = AddFileStatusInput(fileId, "Upload", "Success")
      val data = client.GraphqlData(Option(addFileStatus.Data(addFileStatus.AddFileStatus(fileId, "Upload", "Success"))), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("addFileStatus"))
        .willReturn(okJson(dataString)))

      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val result = controller.addFileStatus()
        .apply(FakeRequest(POST, s"/add-file-status")
          .withJsonBody(Json.parse(addFileStatusInput.asJson.noSpaces))
          .withCSRFToken
        )
      val response: String = contentAsString(result)
      val addFileStatusResponse = decode[addFileStatus.AddFileStatus](response).toOption
      addFileStatusResponse.isDefined must be(true)
      addFileStatusResponse.get.fileId must be(addFileStatusInput.fileId)
      addFileStatusResponse.get.statusType must be(addFileStatusInput.statusType)
      addFileStatusResponse.get.statusValue must be(addFileStatusInput.statusValue)

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val result = controller.addFileStatus()
        .apply(FakeRequest(POST, s"/add-file-status")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val fileId = UUID.randomUUID()
      val addFileStatusInput = AddFileStatusInput(fileId, "Upload", "Success")

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("addFileStatus"))
        .willReturn(serverError()))

      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val result = controller.addFileStatus()
        .apply(FakeRequest(POST, s"/add-file-status")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val startUploadInput = StartUploadInput(consignmentId, "parent")
      val data = client.GraphqlData(Option(startUpload.Data("ok")), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("startUpload"))
        .willReturn(okJson(dataString)))

      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.startUpload()
        .apply(FakeRequest(POST, s"/start-upload")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.startUpload()
        .apply(FakeRequest(POST, s"/start-upload")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val startUploadInput = StartUploadInput(consignmentId, "parent")
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("startUpload"))
        .willReturn(serverError()))

      val saveMetadataResponse = controller.startUpload()
        .apply(FakeRequest(POST, s"/save-metadata")
          .withJsonBody(Json.parse(startUploadInput.asJson.noSpaces))
          .withCSRFToken
        )
      val exception: Throwable = saveMetadataResponse.failed.futureValue
      exception.getMessage must startWith("Unexpected response from GraphQL API")
    }
  }

  "UploadController updateConsignmentStatus" should {
    "call the updateConsignmentStatus endpoint" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val client = graphQLConfiguration.getClient[updateConsignmentStatus.Data, updateConsignmentStatus.Variables]()
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val updateConsignmentStatusInput = ConsignmentStatusInput(consignmentId, "type", "value")
      val data = client.GraphqlData(Option(updateConsignmentStatus.Data(Option(1))), Nil)
      val dataString = data.asJson.noSpaces

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("updateConsignmentStatus"))
        .willReturn(okJson(dataString)))

      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.updateConsignmentStatus()
        .apply(FakeRequest(POST, s"/update-consignment-status")
          .withJsonBody(Json.parse(updateConsignmentStatusInput.asJson.noSpaces))
          .withCSRFToken
        )
      val response: String = contentAsString(saveMetadataResponse)
      response must be("1")

      wiremockServer.getAllServeEvents.asScala.nonEmpty must be(true)
    }

    "throw an error for invalid input data" in {
      val graphQLConfiguration: GraphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService: ConsignmentService = new ConsignmentService(graphQLConfiguration)
      val fileStatusService = new FileStatusService(graphQLConfiguration)
      val uploadService = new UploadService(graphQLConfiguration)
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val saveMetadataResponse = controller.updateConsignmentStatus()
        .apply(FakeRequest(POST, s"/update-consignment-status")
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
      val uploadService = new UploadService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = new UploadController(getAuthorisedSecurityComponents,
        graphQLConfiguration, getValidStandardUserKeycloakConfiguration, frontEndInfoConfiguration, consignmentService, uploadService, fileStatusService, googleDriveService)
      val updateConsignmentStatusInput = ConsignmentStatusInput(consignmentId, "type", "value")
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("updateConsignmentStatus"))
        .willReturn(serverError()))

      val saveMetadataResponse = controller.updateConsignmentStatus()
        .apply(FakeRequest(POST, s"/save-metadata")
          .withJsonBody(Json.parse(updateConsignmentStatusInput.asJson.noSpaces))
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
    pageAsString must include(
      """                                <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
        |                                    Start upload""".stripMargin
    )
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
    pageAsString must include("""<div class="progress-display" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">""")
  }

  private def checkForExpectedPageContentOnOtherUploadPages(pageAsString: String, userType: String = "standard", uploadStatus: String = "") = {
    if (userType == "judgment") {
      pageAsString must include("""<h1 class="govuk-heading-l">Uploading judgment</h1>""")
    } else {
      pageAsString must include("""<h1 class="govuk-heading-l">Uploading records</h1>""")
    }

    if (uploadStatus == "Completed") {
      pageAsString must include(
        """<p class="govuk-body">Your upload is complete and has been saved. You cannot make amendments to your upload or add additional files.</p>"""
      )
      pageAsString must include(
        """<p class="govuk-body">Please click 'Continue' to proceed with your transfer.</p>"""
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
