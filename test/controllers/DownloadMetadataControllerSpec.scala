package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tototoshi.csv.CSVReader
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.types.DataType.{DateTime, Text}
import graphql.codegen.types.PropertyType.Supplied
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import play.api.http.HttpVerbs.GET
import play.api.http.Status.{FORBIDDEN, FOUND}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.io.StringReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class DownloadMetadataControllerSpec extends FrontEndTestHelper {

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements()

  def customMetadata(name: String, fullName: String, exportOrdinal: Int = Int.MaxValue, allowExport: Boolean = true): cm.CustomMetadata = {
    if (name == "DateTimeProperty") {
      cm.CustomMetadata(name, None, Option(fullName), Supplied, None, DateTime, editable = false, multiValue = false, None, 1, Nil, Option(exportOrdinal), allowExport)
    } else {
      cm.CustomMetadata(name, None, Option(fullName), Supplied, None, Text, editable = false, multiValue = false, None, 1, Nil, Option(exportOrdinal), allowExport)
    }
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
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1"),
        customMetadata("TestProperty2", "Test Property 2"),
        customMetadata("DateTimeProperty", "DateTime Property"),
        customMetadata("FileName", "File Name")
      )
      val metadataFileOne = List(
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("TestProperty2", "TestValue2File1"),
        FileMetadata("DateTimeProperty", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
        FileMetadata("FileName", "FileName1")
      )
      val metadataFileTwo = List(
        FileMetadata("TestProperty1", "TestValue1File2"),
        FileMetadata("TestProperty2", "TestValue2File2"),
        FileMetadata("FileName", "FileName2")
      )
      val files = List(
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil),
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileTwo, Nil)
      )

      val csvList: List[Map[String, String]] = getCsvFromController(customProperties, files).toLazyListWithHeaders().toList

      csvList.size must equal(2)
      csvList.head("Test Property 1") must equal("TestValue1File1")
      csvList.head("Test Property 2") must equal("TestValue2File1")
      csvList.head("DateTime Property") must equal("2021-02-03T10:33:30")
      csvList.head("File Name") must equal("FileName1")
      csvList.last("Test Property 1") must equal("TestValue1File2")
      csvList.last("Test Property 2") must equal("TestValue2File2")
      csvList.last("File Name") must equal("FileName2")
    }

    "download the csv for rows with different numbers of metadata" in {
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1"),
        customMetadata("TestProperty2", "Test Property 2"),
        customMetadata("FileName", "File Name")
      )
      val metadataFileOne = List(
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("FileName", "FileName1")
      )
      val metadataFileTwo = List(
        FileMetadata("TestProperty1", "TestValue1File2"),
        FileMetadata("TestProperty2", "TestValue2File2"),
        FileMetadata("FileName", "FileName2")
      )
      val files = List(
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil),
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileTwo, Nil)
      )

      val csvList: List[Map[String, String]] = getCsvFromController(customProperties, files).toLazyListWithHeaders().toList

      csvList.size must equal(2)
      csvList.head("Test Property 1") must equal("TestValue1File1")
      csvList.head("Test Property 2") must equal("")
      csvList.head("File Name") must equal("FileName1")
      csvList.last("Test Property 1") must equal("TestValue1File2")
      csvList.last("Test Property 2") must equal("TestValue2File2")
      csvList.last("File Name") must equal("FileName2")
    }

    "download the csv for rows with multiple values" in {
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1"),
        customMetadata("TestProperty2", "Test Property 2"),
        customMetadata("DateTimeProperty", "DateTime Property"),
        customMetadata("FileName", "File Name")
      )
      val metadataFileOne = List(
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("TestProperty2", "TestValue2File1"),
        FileMetadata("TestProperty2", "TestValue3File1"),
        FileMetadata("FileName", "FileName1")
      )
      val files = List(
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil)
      )

      val csvList: List[Map[String, String]] = getCsvFromController(customProperties, files).toLazyListWithHeaders().toList

      csvList.size must equal(1)
      csvList.head("Test Property 1") must equal("TestValue1File1")
      csvList.head("Test Property 2") must equal("TestValue2File1|TestValue3File1")
      csvList.head("File Name") must equal("FileName1")
    }

    "download the csv for datetime rows to include the seconds to the file when the input seconds are zero" in {
      val lastModified = LocalDateTime.parse("2021-02-03T10:33:00.0")
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1"),
        customMetadata("DateTimeProperty", "DateTime Property"),
        customMetadata("FileName", "File Name")
      )
      val metadataFileOne = List(
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("DateTimeProperty", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
        FileMetadata("FileName", "FileName1")
      )
      val files = List(
        gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil)
      )

      val csvList: List[Map[String, String]] = getCsvFromController(customProperties, files).toLazyListWithHeaders().toList

      csvList.size must equal(1)
      csvList.head("Test Property 1") must equal("TestValue1File1")
      csvList.head("DateTime Property") must equal("2021-02-03T10:33:00")
      csvList.head("File Name") must equal("FileName1")
    }

    "ignore fields set with allowExport set to false" in {
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1", allowExport = false),
        customMetadata("TestProperty2", "Test Property 2"),
        customMetadata("FileName", "File Name")
      )
      val metadataFileOne = List(
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("TestProperty2", "TestValue2File1"),
        FileMetadata("FileName", "FileName1")
      )
      val files = List(gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil))

      val csvList: List[Map[String, String]] = getCsvFromController(customProperties, files).toLazyListWithHeaders().toList

      csvList.size must equal(1)
      csvList.head.get("Test Property 1") must equal(None)
      csvList.head("Test Property 2") must equal("TestValue2File1")
      csvList.head("File Name") must equal("FileName1")
    }

    "order fields correctly" in {
      val customProperties = List(
        customMetadata("TestProperty1", "Test Property 1"),
        customMetadata("TestProperty2", "Test Property 2", 3),
        customMetadata("TestProperty3", "Test Property 3", 2),
        customMetadata("TestProperty4", "Test Property 4", 4),
        customMetadata("FileName", "File Name", 1)
      )
      val metadataFileOne = List(
        FileMetadata("FileName", "FileName1"),
        FileMetadata("TestProperty1", "TestValue1File1"),
        FileMetadata("TestProperty2", "TestValue2File1"),
        FileMetadata("TestProperty3", "TestValue3File1"),
        FileMetadata("TestProperty4", "TestValue4File1")
      )
      val files = List(gcfm.GetConsignment.Files(UUID.randomUUID(), Some("FileName"), metadataFileOne, Nil))

      val csvList = getCsvFromController(customProperties, files).toLazyList()

      val headers = csvList.head
      headers.head must be("File Name")
      headers(1) must be("Test Property 3")
      headers(2) must be("Test Property 2")
      headers(3) must be("Test Property 4")
      headers(4) must be("Test Property 1")
    }

    "return forbidden for a judgment user" in {
      val controller = createController("judgment")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataCsv(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
      status(response) must be(FORBIDDEN)
    }

    "return a redirect to login for a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)

      val controller = new DownloadMetadataController(getUnauthorisedSecurityComponents, consignmentService, customMetadataService, getInvalidKeycloakConfiguration)
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataCsv(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
      status(response) must be(FOUND)
    }
  }

  "DownloadMetadataController downloadMetadataPage GET" should {
    "load the download metadata page with the image, download link and continue button" in {
      setConsignmentReferenceResponse(wiremockServer)

      val controller = createController("standard")
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      val responseAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(responseAsString, userType = "standard")
      responseAsString must include("""<svg""")
      responseAsString must include(
        s"""<a class="govuk-button govuk-!-margin-bottom-8 download-metadata" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">"""
      )
      responseAsString must include(s"""<a class="govuk-button" href="/consignment/$consignmentId/confirm-transfer"""")
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

      val controller = new DownloadMetadataController(getUnauthorisedSecurityComponents, consignmentService, customMetadataService, getInvalidKeycloakConfiguration)
      val consignmentId = UUID.randomUUID()
      val response = controller.downloadMetadataPage(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/"))
      status(response) must be(FOUND)
    }
  }

  private def getCsvFromController(customProperties: List[cm.CustomMetadata], files: List[Files]): CSVReader = {
    mockFileMetadataResponse(files)
    mockCustomMetadataResponse(customProperties)

    val consignmentId = UUID.randomUUID()
    val controller = createController("standard")
    val response = controller.downloadMetadataCsv(consignmentId)(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/download-metadata/csv"))
    val responseAsString = contentAsString(response)
    val bufferedSource = new StringReader(responseAsString)
    CSVReader.open(bufferedSource)
  }

  private def createController(consignmentType: String) = {
    setConsignmentTypeResponse(wiremockServer, consignmentType)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)
    val keycloakConfiguration = consignmentType match {
      case "standard" => getValidStandardUserKeycloakConfiguration
      case "judgment" => getValidJudgmentUserKeycloakConfiguration
    }
    new DownloadMetadataController(getAuthorisedSecurityComponents, consignmentService, customMetadataService, keycloakConfiguration)
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
