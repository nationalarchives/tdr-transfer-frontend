package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.typesafe.config.{ConfigFactory, ConfigValue, ConfigValueFactory}
import configuration.{ApplicationConfig, GraphQLConfiguration}
import controllers.util.GuidanceUtils.ALL_COLUMNS
import controllers.util.MetadataProperty._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType.{DateTime, Text}
import graphql.codegen.types.PropertyType.Supplied
import io.circe
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.apache.pekko.util.ByteString
import org.dhatim.fastexcel.reader._
import org.scalatest.prop.TableFor1
import play.api.Configuration
import play.api.http.HttpVerbs.GET
import play.api.http.Status.{FORBIDDEN, FOUND}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsBytes, contentAsString, defaultAwaitTimeout, status}
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.validation.utils.GuidanceUtils.{GuidanceItem, loadGuidanceFile}

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class DownloadMetadataControllerSpec extends FrontEndTestHelper {

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements()

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val userTypeTable: TableFor1[String] = Table(
    "userType",
    "standard",
    "TNA"
  )

  "DownloadMetadataController downloadMetadataCsv GET" should {
    forAll(userTypeTable)(userType => {
      s"download the csv for a multiple properties and rows when $userType user" in {
        val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
        val uuid1 = UUID.randomUUID().toString
        val uuid2 = UUID.randomUUID().toString

        val metadataFileOne = List(
          FileMetadata(fileUUID, uuid1),
          FileMetadata(fileName, "FileName1"),
          FileMetadata(clientSideOriginalFilepath, "test/path1"),
          FileMetadata(clientSideFileLastModifiedDate, lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
          FileMetadata(end_date, ""),
          FileMetadata(description, "")
        )
        val metadataFileTwo = List(
          FileMetadata(fileUUID, uuid2),
          FileMetadata(fileName, "FileName2"),
          FileMetadata(clientSideOriginalFilepath, "test/path2"),
          FileMetadata(clientSideFileLastModifiedDate, lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
          FileMetadata(end_date, ""),
          FileMetadata(description, "")
        )
        val files = List(
          gcfm.GetConsignment.Files(UUID.randomUUID(), Some("filename"), metadataFileOne, Nil),
          gcfm.GetConsignment.Files(UUID.randomUUID(), Some("filename"), metadataFileTwo, Nil)
        )

        val wb: ReadableWorkbook = getFileFromController(files, userType)
        val ws: Sheet = wb.getFirstSheet
        val rows: List[Row] = ws.read.asScala.toList

        rows.length must equal(3)

        rows.head.getCell(0).asString must equal("filepath")
        rows.head.getCell(1).asString must equal("filename")
        rows.head.getCell(2).asString must equal("date last modified")
        rows.head.getCell(3).asString must equal("date of the record")
        rows.head.getCell(4).asString must equal("description")

        rows(1).getCell(0).asString must equal("test/path1")
        rows(1).getCell(1).asString must equal("FileName1")
        rows(1).getCell(2).asDate.toLocalDate.toString must equal(lastModified.format(DateTimeFormatter.ISO_DATE))

        rows(2).getCell(0).asString must equal("test/path2")
        rows(2).getCell(1).asString must equal("FileName2")
        rows(2).getCell(2).asDate.toLocalDate.toString must equal(lastModified.format(DateTimeFormatter.ISO_DATE))

      }
    })

    s"generate the expected quick guide worksheet" in {
      val wb: ReadableWorkbook = getFileFromController(List.empty[Files], "standard")
      val ws: Sheet = wb.getSheet(1).get
      val rows: List[Row] = ws.read.asScala.toList
      val metadataConfiguration = ConfigUtils.loadConfiguration
      val tdrFileHeaderMapper = metadataConfiguration.propertyToOutputMapper("tdrFileHeader")
      val keyToPropertyType = metadataConfiguration.getPropertyType

      val guidanceItems: Seq[GuidanceItem] = loadGuidanceFile.toOption.getOrElse(Seq.empty)

      ALL_COLUMNS.zipWithIndex.foreach { case (colType, colNumber) =>
        rows.head.getCell(colNumber).asString must equal(colType.header)
      }
      guidanceItems.zipWithIndex.foreach { case (guidanceItem, idx) =>
        val rowNumber = idx + 1
        val examplePropertyType = keyToPropertyType(guidanceItem.property)
        rows(rowNumber).getCell(0).asString must equal(tdrFileHeaderMapper(guidanceItem.property))
        rows(rowNumber).getCell(1).asString must equal(guidanceItem.details)
        rows(rowNumber).getCell(2).asString must equal(guidanceItem.format)
        rows(rowNumber).getCell(3).asString must equal(guidanceItem.tdrRequirement)
        examplePropertyType match {
          case "date" if guidanceItem.example != "N/A" =>
            rows(rowNumber).getCell(4).asDate.toLocalDate.toString must equal(guidanceItem.example)
          case _ => rows(rowNumber).getCell(4).asString() must equal(guidanceItem.example)
        }
      }
    }

    "return forbidden for a judgment user" in {
      val controller = createController("judgment")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataFile(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
      status(response) must be(FORBIDDEN)
    }

    "return a redirect to login for a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

      val controller =
        new DownloadMetadataController(
          getUnauthorisedSecurityComponents,
          consignmentService,
          consignmentStatusService,
          getInvalidKeycloakConfiguration
        )
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataFile(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
      status(response) must be(FOUND)
    }
  }

  "DownloadMetadataController downloadMetadataPage GET" should {
    "load the download metadata page with the image, download link, next button and back button" in {
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = createController("standard")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      val responseAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(responseAsString, userType = "standard")
      responseAsString must include("""<svg""")
      responseAsString must include(
        s"""<a class="govuk-button govuk-button--secondary download-metadata" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">"""
      )
      responseAsString must include(s"""<a href="/consignment/$consignmentId/metadata-review/request"""")
    }

    "return forbidden for a judgment user" in {
      val controller = createController("judgment")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      status(response) must be(FORBIDDEN)
    }

    "return a redirect to login for a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

      val controller =
        new DownloadMetadataController(
          getUnauthorisedSecurityComponents,
          consignmentService,
          consignmentStatusService,
          getInvalidKeycloakConfiguration
        )
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      status(response) must be(FOUND)
    }
  }

  private def getFileFromController(
      files: List[Files],
      userType: String
  ): ReadableWorkbook = {
    mockFileMetadataResponse(files)

    val consignmentId = UUID.randomUUID()
    val controller = createController("standard", userType.some)
    val response = controller.downloadMetadataFile(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
    val responseByteArray: ByteString = contentAsBytes(response)
    val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
    new ReadableWorkbook(bufferedSource)
  }

  private def createController(consignmentType: String, userType: Option[String] = None) = {
    setConsignmentTypeResponse(wiremockServer, consignmentType)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val keycloakConfiguration = userType.getOrElse(consignmentType) match {
      case "standard" => getValidStandardUserKeycloakConfiguration
      case "judgment" => getValidJudgmentUserKeycloakConfiguration
      case "TNA"      => getValidTNAUserKeycloakConfiguration()
    }

    new DownloadMetadataController(
      getAuthorisedSecurityComponents,
      consignmentService,
      consignmentStatusService,
      keycloakConfiguration
    )
  }

  private def mockFileMetadataResponse(files: List[gcfm.GetConsignment.Files]) = {
    val client: GraphQLClient[gcfm.Data, gcfm.Variables] = new GraphQLConfiguration(app.configuration).getClient[gcfm.Data, gcfm.Variables]()

    val dataString = client.GraphqlData(Option(gcfm.Data(Option(gcfm.GetConsignment(files, ""))))).asJson.printWith(Printer.noSpaces)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFilesMetadata"))
        .willReturn(okJson(dataString))
    )
  }
}
