package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileStatuses
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, DisplayPropertiesService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataSummaryControllerSpec extends FrontEndTestHelper {
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

  val fileIds: List[UUID] = List(UUID.randomUUID())

  val closureMetadataType: String = metadataType(0)
  val descriptiveMetadataType: String = metadataType(1)

  "AdditionalMetadataSummaryController" should {
    "render the additional metadata review page for closure metadata type when the page is 'review'" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"

      val closureStartDate = LocalDateTime.of(1990, 12, 1, 10, 0)
      val fileMetadata = List(
        GetConsignment.Files.FileMetadata("TitleClosed", "true"),
        GetConsignment.Files.FileMetadata("TitleAlternate", "An alternative title"),
        GetConsignment.Files.FileMetadata("ClosurePeriod", "4"),
        GetConsignment.Files.FileMetadata("FoiExemptionCode", "1"),
        GetConsignment.Files.FileMetadata("FoiExemptionCode", "2"),
        GetConsignment.Files.FileMetadata("ClosureStartDate", Timestamp.valueOf(closureStartDate).toString)
      )
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()), fileMetadata = fileMetadata)
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds, "review".some)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))
      val closureMetadataSummaryPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(closureMetadataSummaryPage, userType = "standard")
      val metadataFields = List(
        ("Is the title closed?", "Yes"),
        ("Closure Period", "4 years"),
        ("Closure Start Date", "01/12/1990"),
        ("FOI exemption code(s)", "1, 2 "),
        ("Alternative Title2", "An alternative title ")
      )

      verifyReviewPage(closureMetadataSummaryPage, consignmentId.toString, closureMetadataType, metadataFields)
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the additional metadata review page for descriptive metadata type when the page is 'review'" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()))
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, descriptiveMetadataType, fileIds, "review".some)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${descriptiveMetadataType}"))
      val closureMetadataSummaryPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(closureMetadataSummaryPage, userType = "standard")
      val metadataFields = List(("Description", "a previously added description "), ("Language", "Welsh "))
      verifyReviewPage(closureMetadataSummaryPage, consignmentId.toString, descriptiveMetadataType, metadataFields)
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    forAll(metadataType) { metadataType =>
      s"render the additional metadata view page for $metadataType metadata type when the page is 'view' and the user hasn't entered any metadata" in {
        val consignmentId = UUID.randomUUID()
        val consignmentReference = "TEST-TDR-2021-GB"

        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentFilesMetadataResponse(
          wiremockServer,
          consignmentReference,
          fileIds = List(UUID.randomUUID()),
          fileStatuses = FileStatuses("ClosureMetadata", "NotEntered") :: Nil
        )
        setDisplayPropertiesResponse(wiremockServer)

        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
        val controller =
          new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val response = controller
          .getSelectedSummaryPage(consignmentId, metadataType, fileIds)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/$metadataType"))
        val metadataSummaryPage = contentAsString(response)

        status(response) mustBe OK
        contentType(response) mustBe Some("text/html")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataSummaryPage, userType = "standard")
        val descriptiveMetadataFields = List(("Description", "a previously added description "), ("Language", "Welsh "))
        val closureMetadataFields = List(("FOI decision asserted", "12/01/1995"), ("Closure Start Date", "01/12/1990"))
        val metadataFields = if (metadataType == "closure") closureMetadataFields else descriptiveMetadataFields
        verifyViewPage(metadataSummaryPage, consignmentId.toString, metadataType, metadataFields)
        wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
      }
    }

    forAll(metadataType) { metadataType =>
      s"render the additional metadata view page for $metadataType metadata type when the page is 'view' and the user has entered metadata" in {
        val consignmentId = UUID.randomUUID()
        val consignmentReference = "TEST-TDR-2021-GB"

        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentFilesMetadataResponse(
          wiremockServer,
          consignmentReference,
          fileIds = List(UUID.randomUUID()),
          fileStatuses = FileStatuses("ClosureMetadata", "Completed") :: FileStatuses("DescriptiveMetadata", "Completed") :: Nil
        )
        setDisplayPropertiesResponse(wiremockServer)

        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
        val controller =
          new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val response = controller
          .getSelectedSummaryPage(consignmentId, metadataType, fileIds)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/$metadataType"))
        val metadataSummaryPage = contentAsString(response)

        status(response) mustBe OK
        contentType(response) mustBe Some("text/html")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataSummaryPage, userType = "standard")
        val descriptiveMetadataFields = List(("Description", "a previously added description "), ("Language", "Welsh "))
        val closureMetadataFields = List(("FOI decision asserted", "12/01/1995"), ("Closure Start Date", "01/12/1990"))
        val metadataFields = if (metadataType == "closure") closureMetadataFields else descriptiveMetadataFields
        verifyViewPage(metadataSummaryPage, consignmentId.toString, metadataType, metadataFields, hasMetadata = true)
        wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
      }
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))

      status(response) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment($consignmentId:UUID!,,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if no files exist for the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = s"""{"data":{"getConsignment":{"consignmentReference":"TEST","files":[]}}}""".stripMargin
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))
        .failed
        .futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }

    "return an error if metadataType is not valid" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, "invalidMetadataType", fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))
        .failed
        .futureValue

      response.getMessage mustBe "Invalid metadata type: invalidMetadataType"
    }

    "return an error if the consignment doesn't exist" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = """{"data":{"getConsignment":null},"errors":[]}"""
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${closureMetadataType}"))
        .failed
        .futureValue

      response.getMessage mustBe s"No consignment found for consignment $consignmentId"
    }
  }

  def verifyReviewPage(page: String, consignmentId: String, metadataType: String, metadataFields: List[(String, String)]): Unit = {
    page must include(s"""<span class="govuk-caption-l">${metadataType.capitalize} metadata</span>""")
    page must include("""<h1 class="govuk-heading-l"> Review saved changes </h1>""")
    page must include(s"""<p class="govuk-body">Review the $metadataType metadata changes you have made below. You may also edit and delete any metadata changes.</p>""")

    val href = s"/consignment/$consignmentId/additional-metadata/add/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
    page must include(
      s"""          <a id="editMetadata" href="$href" role="button" draggable="false" class="govuk-button govuk-button--secondary" data-module="govuk-button">
         |            Edit metadata
         |          </a>""".stripMargin
    )
    val deleteMetadataButtonHref = s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
    page must include(
      s"""          <a id="deleteMetadata" href="$deleteMetadataButtonHref" role="button" draggable="false" class="govuk-button govuk-button--secondary">
         |            Delete metadata
         |          </a>""".stripMargin
    )
    metadataFields.foreach { field =>
      page must include(
        s"""
          |    <div class="govuk-summary-list__row govuk-summary-list__row--no-border">
          |      <dt class="govuk-summary-list__key">
          |      ${field._1}
          |      </dt>
          |      <dd class="govuk-summary-list__value">
          |        <pre>${field._2}</pre>
          |      </dd>
          |    </div>
          |""".stripMargin
      )
    }
    page must include(
      """    <dt class="govuk-summary-list__key">
        |      Name
        |    </dt>""".stripMargin
    )
    page must include(
      """      <dd class="govuk-summary-list__value">
        |      FileName
        |      </dd>""".stripMargin
    )
    page must include(
      """        <h2 class="govuk-heading-m  govuk-!-margin-bottom-3">Have you finished reviewing the metadata?</h2>
        |        <p class="govuk-body govuk-!-margin-bottom-2">Once you have finished reviewing the metadata of this file you may add metadata to another file.</p>""".stripMargin
    )
    page must include(
      s"""
        |        <a id="chooseFile" href="/consignment/$consignmentId/additional-metadata/files/$metadataType" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
        |          Choose another file
        |        </a>
        |""".stripMargin
    )
    page must include(
      s"""<p class="govuk-body">Or leave $metadataType metadata and return to the
        |          <a href="/consignment/$consignmentId/additional-metadata" role="button" draggable="false" data-module="govuk-button">Descriptive & closure metadata</a> overview page.</p>""".stripMargin
    )
  }

  def verifyViewPage(page: String, consignmentId: String, metadataType: String, metadataFields: List[(String, String)], hasMetadata: Boolean = false): Unit = {
    page must include(s"""<span class="govuk-caption-l">${metadataType.capitalize} metadata</span>""")
    page must include(s"""<h1 class="govuk-heading-l">View Metadata</h1>""")
    page must include(s"""<p class="govuk-body">View existing $metadataType metadata.</p>""")
    page must include(s"""<a href="/consignment/$consignmentId/additional-metadata/files/$metadataType?expanded=false" class="govuk-back-link">Choose a file</a>""".stripMargin)

    if (hasMetadata) {
      val href = s"/consignment/$consignmentId/additional-metadata/add/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
      page must include(s"""
           |            <a id="editMetadata" href="$href" role="button" draggable="false" class="govuk-button govuk-button--secondary govuk-!-margin-bottom-4" data-module="govuk-button">
           |              Edit metadata
           |            </a>""".stripMargin)
      val deleteMetadataButtonHref = s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
      page must include(s"""
           |            <a id="deleteMetadata" href="$deleteMetadataButtonHref" role="button" draggable="false" class="govuk-button govuk-button--secondary">
           |              Delete metadata
           |            </a>""".stripMargin)
    } else {
      if (metadataType == "descriptive") {
        val href = s"/consignment/$consignmentId/additional-metadata/add/descriptive?fileIds=${fileIds.mkString("&amp;")}"
        page must include(s"""
           |              <a id="addMetadata" href="$href" role="button" draggable="false" class="govuk-button govuk-button--secondary govuk-!-margin-bottom-4" data-module="govuk-button">
           |                Add metadata
           |              </a>
           |""".stripMargin)
        s""
      } else {
        val href = s"/consignment/$consignmentId/additional-metadata/status/closure?fileIds=${fileIds.mkString("&amp;")}"
        page must include(s"""
           |              <a id="addMetadata" href="$href" role="button" draggable="false" class="govuk-button govuk-button--secondary govuk-!-margin-bottom-4" data-module="govuk-button">
           |                Add closure metadata
           |              </a>
           |""".stripMargin)
      }

    }
    metadataFields.foreach { field =>
      page must include(
        s"""
           |    <div class="govuk-summary-list__row govuk-summary-list__row--no-border">
           |      <dt class="govuk-summary-list__key">
           |      ${field._1}
           |      </dt>
           |      <dd class="govuk-summary-list__value">
           |        <pre>${field._2}</pre>
           |      </dd>
           |    </div>
           |""".stripMargin
      )
    }

    page must include(
      """    <dt class="govuk-summary-list__key">
        |      Name
        |    </dt>""".stripMargin
    )
    page must include(
      """      <dd class="govuk-summary-list__value">
        |      FileName
        |      </dd>""".stripMargin
    )
  }
}
