package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files
import graphql.codegen.GetConsignmentFiles.{getConsignmentFiles => gcf}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, defaultAwaitTimeout, redirectLocation, status => playStatus}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class AdditionalMetadataNavigationControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)
  val emptyMetadata: Files.Metadata = gcf.GetConsignment.Files.Metadata(None)
  val checkPageForStaticElements = new CheckPageForStaticElements

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val consignmentId: UUID = UUID.randomUUID()

  "AdditionalMetadataNavigationController" should {
    "getAllFiles" should {
      forAll(metadataType) { metadataType =>
        s"render the correct description for metadata type $metadataType" in {
          val parentFile = gcf.GetConsignment.Files(UUID.randomUUID(), Option("parent"), Option("Folder"), None, emptyMetadata)
          val consignmentService: ConsignmentService = mockConsignmentService(List(parentFile), "standard")

          val additionalMetadataController =
            new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
          val result = additionalMetadataController
            .getAllFiles(consignmentId, metadataType)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/$metadataType/").withCSRFToken)
          val content = contentAsString(result)

          content.contains(s"Add or edit $metadataType metadata on file basis") must be(true)
          content.contains(s"Folder uploaded: ${parentFile.fileName.get}")
        }

        s"render the file navigation page with nested directories for metadata type $metadataType" in {
          val parentId = UUID.randomUUID()
          val descendantOneFileId = UUID.randomUUID()
          val descendantTwoFileId = UUID.randomUUID()
          val parentFile = gcf.GetConsignment.Files(parentId, Option("parent"), Option("Folder"), None, emptyMetadata)
          val descendantOneFile = gcf.GetConsignment.Files(descendantOneFileId, Option("descendantOneFile"), Option("Folder"), Option(parentId), emptyMetadata)
          val descendantTwoFile = gcf.GetConsignment.Files(descendantTwoFileId, Option("descendantTwoFile"), Option("File"), Option(descendantOneFileId), emptyMetadata)
          val consignmentService: ConsignmentService = mockConsignmentService(List(parentFile, descendantOneFile, descendantTwoFile), "standard")

          val additionalMetadataController =
            new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
          val result = additionalMetadataController
            .getAllFiles(consignmentId, metadataType)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/$metadataType/").withCSRFToken)

          val content = contentAsString(result).replaceAll("\n", "").replaceAll(" ", "")

          content.contains(getExpectedCheckboxHtml(parentId, "parent")) must equal(true)
          content.contains(getExpectedCheckboxHtml(descendantOneFileId, "descendantOneFile")) must equal(true)
          content.contains(getExpectedCheckboxHtml(descendantTwoFileId, "descendantTwoFile")) must equal(true)
        }
      }

      "return forbidden for a judgment user" in {
        val consignmentService = mockConsignmentService(Nil, "judgment")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .getAllFiles(consignmentId, "closure")
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/closure/").withCSRFToken)
        playStatus(result) must equal(FORBIDDEN)
      }

      "return a redirect the login page for a logged out user" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
        val result = additionalMetadataController
          .getAllFiles(consignmentId, "closure")
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/closure/").withCSRFToken)
        playStatus(result) must equal(FOUND)
      }
    }

    "submitFiles" should {
      "redirect to the closure status page with the correct file ids and closure metadata type if the files are not already closed" in {
        val files = gcf.GetConsignment.Files(UUID.randomUUID(), None, None, None, gcf.GetConsignment.Files.Metadata(Option(""))) :: Nil
        val consignmentService = mockConsignmentService(files, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/closure/").withCSRFToken)
        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(s"/consignment/$consignmentId/additional-metadata/closure-status")
      }

      "redirect to the closure metadata page with the correct file ids and closure metadata type if the files are already closed" in {
        val files = gcf.GetConsignment.Files(UUID.randomUUID(), None, None, None, gcf.GetConsignment.Files.Metadata(Option(""))) :: Nil
        val consignmentService = mockConsignmentService(files, "standard", allClosed = true)
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/closure/").withCSRFToken)
        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(s"/consignment/$consignmentId/add-closure-metadata")
      }

      "redirect to the metadata summary page if the metadata type is descriptive" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .submitFiles(consignmentId, "descriptive")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/descriptive/").withCSRFToken)
        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(s"/consignment/$consignmentId/additional-metadata/closure/selected-summary?metadataTypeAndValueSelected=")
      }

      "return forbidden for a judgment user" in {
        val consignmentService = mockConsignmentService(Nil, "judgment")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/closure/").withCSRFToken)
        playStatus(result) must equal(FORBIDDEN)
      }

      "return a redirect the login page for a logged out user" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val result = additionalMetadataController
          .submitFiles(consignmentId, "descriptive")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/descriptive/").withCSRFToken)
        playStatus(result) must equal(SEE_OTHER)
      }
    }
  }

  private def getExpectedCheckboxHtml(id: UUID, label: String): String = {
    s"""
        |<input class="govuk-checkboxes__input" name="$id" id="checkbox-$id" tabindex="-1" type="checkbox"/>
        |        <label class="govuk-label govuk-checkboxes__label" for="checkbox-$id">
        |        $label
        |        </label>
        |""".stripMargin.replaceAll("\n", "").replaceAll(" ", "")
  }

  private def mockConsignmentService(files: List[gcf.GetConsignment.Files], consignmentType: String, allClosed: Boolean = false) = {
    val consignmentData: gcf.GetConsignment = gcf.GetConsignment(files)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val graphqlClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables]()
    val dataString = graphqlClient.GraphqlData(Option(gcf.Data(Option(consignmentData)))).asJson.printWith(Printer.noSpaces)
    val metadataClient = graphQLConfiguration.getClient[gcfm.Data, gcfm.Variables]()
    val oldMetadata = gcfm.GetConsignment.Files.Metadata(None, None, None, None, None, None)
    val getMetadataFiles = files.map(file => {
      val closureType: String = if (allClosed) "Closed" else "Open"
      val fileMetadata = List(gcfm.GetConsignment.Files.FileMetadata("FileType", "File"), gcfm.GetConsignment.Files.FileMetadata("ClosureType", closureType))
      gcfm.GetConsignment.Files(file.fileId, fileMetadata, oldMetadata)
    })

    val metadataDataString = metadataClient.GraphqlData(Option(gcfm.Data(Option(gcfm.GetConsignment(getMetadataFiles, "Reference"))))).asJson.printWith(Printer.noSpaces)
    setConsignmentTypeResponse(wiremockServer, consignmentType)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFilesMetadata"))
        .willReturn(okJson(metadataDataString))
    )
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFiles("))
        .willReturn(okJson(dataString))
    )
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    consignmentService
  }
}
