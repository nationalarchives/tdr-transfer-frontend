package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import controllers.util.MetadataProperty.fileType
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files
import graphql.codegen.GetConsignmentFiles.{getConsignmentFiles => gcf}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.types.FileMetadataFilters
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.Assertion
import play.api.Play.materializer
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, FOUND, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, defaultAwaitTimeout, redirectLocation, status => playStatus}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper, GetConsignmentFilesMetadataGraphqlRequestData}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.CollectionHasAsScala

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
          val parentFile = gcf.GetConsignment.Files(UUID.randomUUID(), Option("parent"), Option("Folder"), None, emptyMetadata, List())
          val consignmentService: ConsignmentService = mockConsignmentService(List(parentFile), "standard")

          val additionalMetadataController =
            new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
          val result = additionalMetadataController
            .getAllFiles(consignmentId, metadataType)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/$metadataType/").withCSRFToken)
          val content = contentAsString(result)

          content must include(s"Add or edit $metadataType metadata on file basis")
          content must include(s"Folder uploaded: ${parentFile.fileName.get}")
          content must not include ("Select at least one file or folder")
          content must include(
            s"""<a href="/consignment/$consignmentId/additional-metadata" draggable="false" class="govuk-button govuk-button--secondary" data-module="govuk-button">
            |          Back
            |          </a>""".stripMargin
          )
          if (metadataType == "closure") getExpectedClosureHtml(content: String)
        }

        s"render the file navigation page with nested directories for metadata type $metadataType" in {
          val parentId = UUID.randomUUID()
          val descendantOneFileId = UUID.randomUUID()
          val descendantTwoFileId = UUID.randomUUID()
          val parentFile = gcf.GetConsignment.Files(parentId, Option("parent"), Option("Folder"), None, emptyMetadata, List())
          val descendantOneFile = gcf.GetConsignment.Files(descendantOneFileId, Option("descendantOneFile"), Option("Folder"), Option(parentId), emptyMetadata, List())
          val descendantTwoFile = gcf.GetConsignment.Files(descendantTwoFileId, Option("descendantTwoFile"), Option("File"), Option(descendantOneFileId), emptyMetadata, List())
          val consignmentService: ConsignmentService = mockConsignmentService(List(parentFile, descendantOneFile, descendantTwoFile), "standard")

          val additionalMetadataController =
            new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
          val result = additionalMetadataController
            .getAllFiles(consignmentId, metadataType)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/$metadataType/").withCSRFToken)

          val content = contentAsString(result).replaceAll("\n", "").replaceAll(" ", "")

          content must include(getExpectedFolderHtml(parentId, "parent"))
          content must include(getExpectedFolderHtml(descendantOneFileId, "descendantOneFile"))
          content must include(getExpectedFileHtml(descendantTwoFileId, "descendantTwoFile"))
        }
      }

      "return forbidden for a judgment user" in {
        val consignmentService = mockConsignmentService(Nil, "judgment")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .getAllFiles(consignmentId, "closure")
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/${metadataType(0)}").withCSRFToken)
        playStatus(result) must equal(FORBIDDEN)
      }

      "return a redirect the login page for a logged out user" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .getAllFiles(consignmentId, "closure")
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/files/${metadataType(0)}").withCSRFToken)
        playStatus(result) must equal(FOUND)
      }
    }

    "submitFiles" should {
      "redirect to the closure status page with the correct file ids and closure metadata type if the files are not already closed" in {
        val files = gcf.GetConsignment.Files(UUID.randomUUID(), None, None, None, gcf.GetConsignment.Files.Metadata(Option("")), List()) :: Nil
        val consignmentService = mockConsignmentService(files, "standard")
        val fileId = UUID.randomUUID()
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(
            FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/${metadataType(0)}")
              .withFormUrlEncodedBody(Seq((s"radios-list-$fileId", "checked")): _*)
              .withCSRFToken
          )

        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}?fileIds=$fileId")

        val events = wiremockServer.getAllServeEvents
        val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("getConsignmentFilesMetadata")).get
        val request: GetConsignmentFilesMetadataGraphqlRequestData = decode[GetConsignmentFilesMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
          .getOrElse(GetConsignmentFilesMetadataGraphqlRequestData("", gcfm.Variables(consignmentId, None)))

        val input = request.variables.fileFiltersInput
        input.get.selectedFileIds mustBe List(fileId).some
        input.get.metadataFilters mustBe FileMetadataFilters(Some(true), None, Some(List(fileType))).some
      }

      "redirect to the closure metadata page with the correct file ids and closure metadata type if the files are already closed" in {
        val files = gcf.GetConsignment.Files(UUID.randomUUID(), None, None, None, gcf.GetConsignment.Files.Metadata(Option("")), List()) :: Nil
        val consignmentService = mockConsignmentService(files, "standard", allClosed = true)
        val fileId = UUID.randomUUID().toString
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .submitFiles(consignmentId, metadataType(0))
          .apply(
            FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/${metadataType(0)}")
              .withFormUrlEncodedBody(Seq((s"radios-list-$fileId", "checked")): _*)
              .withCSRFToken
          )
        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(
          s"/consignment/$consignmentId/additional-metadata/add/${metadataType(0)}?fileIds=$fileId"
        )
      }

      "redirect to the metadata summary page if the metadata type is descriptive" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val fileId = UUID.randomUUID().toString
        val result = additionalMetadataController
          .submitFiles(consignmentId, "descriptive")
          .apply(
            FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/descriptive/")
              .withFormUrlEncodedBody(Seq((s"radios-list-$fileId", "checked")): _*)
              .withCSRFToken
          )
        playStatus(result) must equal(SEE_OTHER)
        redirectLocation(result).get must equal(s"/consignment/$consignmentId/additional-metadata/add/descriptive?fileIds=$fileId")
      }

      "redirect to the file navigation page with an error message if a user submits the page without selecting any files and folders" in {
        val files = gcf.GetConsignment.Files(UUID.randomUUID(), None, None, None, gcf.GetConsignment.Files.Metadata(Option("")), List()) :: Nil
        val consignmentService = mockConsignmentService(files, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/closure").withCSRFToken)

        playStatus(result) must equal(BAD_REQUEST)
        val content = contentAsString(result)
        content must include(
          """    <div class="govuk-error-summary govuk-!-margin-bottom-4" data-module="govuk-error-summary">
            |      <div role="alert">
            |        <h2 class="govuk-error-summary__title">There is a problem</h2>
            |        <div class="govuk-error-summary__body">
            |          <ul class="govuk-list govuk-error-summary__list">
            |            <li>
            |              <a href="#file-selection">Select at least one file or folder</a>
            |            </li>
            |          </ul>
            |        </div>
            |      </div>
            |    </div>""".stripMargin
        )
      }

      "return forbidden for a judgment user" in {
        val consignmentService = mockConsignmentService(Nil, "judgment")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .submitFiles(consignmentId, "closure")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/closure").withCSRFToken)
        playStatus(result) must equal(FORBIDDEN)
      }

      "return a redirect the login page for a logged out user" in {
        val consignmentService = mockConsignmentService(Nil, "standard")
        val additionalMetadataController =
          new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents, MockAsyncCacheApi())
        val result = additionalMetadataController
          .submitFiles(consignmentId, "descriptive")
          .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/files/descriptive/").withCSRFToken)
        playStatus(result) must equal(SEE_OTHER)
      }
    }
  }

  private def getExpectedFolderHtml(id: UUID, label: String): String = {
    s"""
        |<div class="tna-tree__node-item__container">
        |        <button class="tna-tree__expander js-tree__expander--radios" tabindex="-1" id="radios-expander-$id">
        |          <span class="govuk-visually-hidden">Expand</span>
        |        </button>
        |
        |        <div class="js-radios-directory tna-radios-directory">
        |          <span class="govuk-label tna-radios-directory__label">
        |            <img class="tna-tree__svg-directory" role="img" src="/assets/images/folder.svg" alt="Directory">
        |            $label
        |          </span>
        |        </div>
        |      </div>
        |""".stripMargin.replaceAll("\n", "").replaceAll(" ", "")
  }

  private def getExpectedFileHtml(id: UUID, label: String): String = {
    s"""
        |<li class="tna-tree__item govuk-radios--small" role="treeitem" id="radios-list-$id" aria-level="3" aria-setsize="1" aria-posinset="0" aria-selected="false">
        |      <div class="tna-tree__node-item__radio govuk-radios__item">
        |        <span class="govuk-radios__input" aria-hidden="true" id="radio-$id" name="nested-navigation"></span>
        |        <span class="govuk-label govuk-radios__label">
        |        $label
        |        </span>
        |      </div>
        |    </li>
        |""".stripMargin.replaceAll("\n", "").replaceAll(" ", "")
  }

  private def getExpectedClosureHtml(htmlContent: String): Assertion = {
    htmlContent must include(s"""
       |        <div class="govuk-inset-text govuk-!-margin-top-0">
       |            Once you have added all necessary closure metadata return to the <a href="/consignment/$consignmentId/additional-metadata">previous page</a> to add descriptive metadata or continue with the transfer.
       |        </div>
       |""".stripMargin)
  }

  private def mockConsignmentService(files: List[gcf.GetConsignment.Files], consignmentType: String, allClosed: Boolean = false) = {
    val consignmentData: gcf.GetConsignment = gcf.GetConsignment(files)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val graphqlClient = graphQLConfiguration.getClient[gcf.Data, gcf.Variables]()
    val dataString = graphqlClient.GraphqlData(Option(gcf.Data(Option(consignmentData)))).asJson.printWith(Printer.noSpaces)
    val metadataClient = graphQLConfiguration.getClient[gcfm.Data, gcfm.Variables]()
    val getMetadataFiles = files.map(file => {
      val closureType: String = if (allClosed) "Closed" else "Open"
      val fileMetadata = List(gcfm.GetConsignment.Files.FileMetadata("FileType", "File"), gcfm.GetConsignment.Files.FileMetadata("ClosureType", closureType))
      gcfm.GetConsignment.Files(file.fileId, Some("FileName"), fileMetadata)
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
