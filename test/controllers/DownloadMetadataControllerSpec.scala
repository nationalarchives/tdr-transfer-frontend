package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.typesafe.config.{ConfigFactory, ConfigValue, ConfigValueFactory}
import org.dhatim.fastexcel.reader._

import scala.jdk.CollectionConverters._
import configuration.{ApplicationConfig, GraphQLConfiguration}
import controllers.util.MetadataProperty._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType.{DateTime, Text}
import graphql.codegen.types.PropertyType.Supplied
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.apache.pekko.util.ByteString
import org.mockito.Mockito.when
import play.api.Configuration
import play.api.http.HttpVerbs.GET
import play.api.http.Status.{FORBIDDEN, FOUND}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsBytes, contentAsString, defaultAwaitTimeout, status}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class DownloadMetadataControllerSpec extends FrontEndTestHelper {

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements()

  def getApplicationConfig(blockMetadataReview: Boolean): ApplicationConfig = {
    val config: Map[String, ConfigValue] = ConfigFactory
      .load()
      .withValue("featureAccessBlock.blockMetadataReview", ConfigValueFactory.fromAnyRef(blockMetadataReview))
      .entrySet()
      .asScala
      .map(e => e.getKey -> e.getValue)
      .toMap
    new ApplicationConfig(Configuration.from(config))
  }

  def customMetadata(name: String, fullName: String, exportOrdinal: Int = Int.MaxValue, allowExport: Boolean = true): cm.CustomMetadata = {
    if (name == "DateTimeProperty" || name == clientSideFileLastModifiedDate) {
      cm.CustomMetadata(name, None, Option(fullName), Supplied, None, DateTime, editable = false, multiValue = false, None, 1, Nil, Option(exportOrdinal), allowExport)
    } else if (name.contains("BooleanProperty")) {
      cm.CustomMetadata(name, None, Option(fullName), Supplied, None, DataType.Boolean, editable = false, multiValue = false, None, 1, Nil, Option(exportOrdinal), allowExport)
    } else {
      cm.CustomMetadata(name, None, Option(fullName), Supplied, None, Text, editable = false, multiValue = false, None, 1, Nil, Option(exportOrdinal), allowExport)
    }
  }

  def displayProperty(name: String, value: String, dataType: DataType = DataType.Text, active: Boolean = true): dp.DisplayProperties = {
    val attributes = List(
      dp.DisplayProperties.Attributes("Name", Option(value), dataType),
      dp.DisplayProperties.Attributes("Datatype", Option(dataType.toString.toLowerCase()), dataType),
      dp.DisplayProperties.Attributes("Active", Option(active.toString), dataType)
    )
    dp.DisplayProperties(name, attributes)
  }

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DownloadMetadataController downloadMetadataCsv GET" should {
    "download the csv for a multiple properties and rows" in {
      val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
      val uuid1 = UUID.randomUUID().toString
      val uuid2 = UUID.randomUUID().toString
      val displayProperties = List(
        displayProperty(fileUUID, "UUID"),
        displayProperty(fileName, "File Name"),
        displayProperty(clientSideOriginalFilepath, "Filepath"),
        displayProperty(clientSideFileLastModifiedDate, "Date last modified", DataType.DateTime),
        displayProperty(end_date, "Date of the record", DataType.DateTime),
        displayProperty(description, "Description")
      )
      val customProperties = List(
        customMetadata(fileUUID, "UUID"),
        customMetadata(fileName, "FileName"),
        customMetadata(clientSideOriginalFilepath, "Filepath"),
        customMetadata(clientSideFileLastModifiedDate, "Date last modified"),
        customMetadata(end_date, ""),
        customMetadata(description, "")
      )
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
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil),
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileTwo, Nil)
      )

      val wb: ReadableWorkbook = getFileFromController(customProperties, files, displayProperties)
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length must equal(3)

      rows.head.getCell(0).asString must equal("Filepath")
      rows.head.getCell(1).asString must equal("File Name")
      rows.head.getCell(2).asString must equal("Date last modified")
      rows.head.getCell(3).asString must equal("Date of the record")
      rows.head.getCell(4).asString must equal("Description")
      rows.head.getCell(5).asString must equal("UUID")

      rows(1).getCell(0).asString must equal("test/path1")
      rows(1).getCell(1).asString must equal("FileName1")
      rows(1).getCell(2).asDate.toLocalDate.toString must equal(lastModified.format(DateTimeFormatter.ISO_DATE))
      rows(2).getCell(5).asString must equal(uuid2)

      rows(2).getCell(0).asString must equal("test/path2")
      rows(2).getCell(1).asString must equal("FileName2")
      rows(2).getCell(2).asDate.toLocalDate.toString must equal(lastModified.format(DateTimeFormatter.ISO_DATE))
      rows(2).getCell(5).asString must equal(uuid2)

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
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val applicationConfig: ApplicationConfig = new ApplicationConfig(app.configuration)

      val controller =
        new DownloadMetadataController(
          getUnauthorisedSecurityComponents,
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getInvalidKeycloakConfiguration,
          applicationConfig
        )
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataFile(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
      status(response) must be(FOUND)
    }
  }

  "DownloadMetadataController downloadMetadataPage GET" should {
    "load the download metadata page with the image, download link, next button and back button when 'blockMetadataReview' set to true" in {
      setConsignmentReferenceResponse(wiremockServer)

      val controller = createController("standard")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      val responseAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(responseAsString, userType = "standard")
      responseAsString must include("""<svg""")
      responseAsString must include(
        s"""<a class="govuk-button govuk-!-margin-bottom-8 govuk-button--secondary" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">"""
      )
      responseAsString must include(s"""<a class="govuk-button" href="/consignment/$consignmentId/confirm-transfer"""")
      responseAsString must include(s"""<a href="/consignment/$consignmentId/additional-metadata/entry-method"""")
    }

    "load the download metadata page with the image, download link, next button and back button when 'blockMetadataReview' set to false" in {
      setConsignmentReferenceResponse(wiremockServer)

      val controller = createController("standard", blockMetadataReview = false)
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      val responseAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(responseAsString, userType = "standard")
      responseAsString must include("""<svg""")
      responseAsString must include(
        s"""<a class="govuk-button govuk-!-margin-bottom-8 govuk-button--secondary" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">"""
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
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val applicationConfig: ApplicationConfig = new ApplicationConfig(app.configuration)

      val controller =
        new DownloadMetadataController(
          getUnauthorisedSecurityComponents,
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getInvalidKeycloakConfiguration,
          applicationConfig
        )
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      status(response) must be(FOUND)
    }
  }

  private def getFileFromController(customProperties: List[cm.CustomMetadata], files: List[Files], displayProperties: List[dp.DisplayProperties]): ReadableWorkbook = {
    mockFileMetadataResponse(files)
    mockCustomMetadataResponse(customProperties)
    mockDisplayPropertiesResponse(displayProperties)

    val consignmentId = UUID.randomUUID()
    val controller = createController("standard")
    val response = controller.downloadMetadataFile(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
    val responseByteArray: ByteString = contentAsBytes(response)
    val bufferedSource = new ByteArrayInputStream(responseByteArray.toArray)
    new ReadableWorkbook(bufferedSource)
  }

  private def createController(consignmentType: String, blockMetadataReview: Boolean = true) = {
    setConsignmentTypeResponse(wiremockServer, consignmentType)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)
    val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
    val keycloakConfiguration = consignmentType match {
      case "standard" => getValidStandardUserKeycloakConfiguration
      case "judgment" => getValidJudgmentUserKeycloakConfiguration
    }

    new DownloadMetadataController(
      getAuthorisedSecurityComponents,
      consignmentService,
      customMetadataService,
      displayPropertiesService,
      keycloakConfiguration,
      getApplicationConfig(blockMetadataReview)
    )
  }

  private def mockDisplayPropertiesResponse(displayProperties: List[dp.DisplayProperties]) = {
    val client: GraphQLClient[dp.Data, dp.Variables] = new GraphQLConfiguration(app.configuration).getClient[dp.Data, dp.Variables]()
    val customMetadataResponse: dp.Data = dp.Data(displayProperties)
    val data: client.GraphqlData = client.GraphqlData(Some(customMetadataResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("displayProperties"))
        .willReturn(okJson(dataString))
    )
  }

  private def mockCustomMetadataResponse(customProperties: List[cm.CustomMetadata]) = {
    val client: GraphQLClient[cm.Data, cm.Variables] = new GraphQLConfiguration(app.configuration).getClient[cm.Data, cm.Variables]()
    val customMetadataResponse: cm.Data = cm.Data(customProperties)
    val data: client.GraphqlData = client.GraphqlData(Some(customMetadataResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("customMetadata"))
        .willReturn(okJson(dataString))
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
