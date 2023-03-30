package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, closureType, fileType}
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.types.{FileMetadataFilters, UpdateBulkFileMetadataInput}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status._
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper, GetConsignmentFilesMetadataGraphqlRequestData}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

// scalastyle:off line.size.limit
class AdditionalMetadataClosureStatusControllerSpec extends FrontEndTestHelper {

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

  "getClosureStatusPage" should {
    "render the closure status page and closure status checkbox should be unchecked" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Open")

      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}").withCSRFToken)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId)

      val events = wiremockServer.getAllServeEvents
      val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("getConsignmentFilesMetadata")).get
      val request: GetConsignmentFilesMetadataGraphqlRequestData = decode[GetConsignmentFilesMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(GetConsignmentFilesMetadataGraphqlRequestData("", gcfm.Variables(consignmentId, None)))

      val input = request.variables.fileFiltersInput
      input.get.selectedFileIds mustBe fileIds.some
      input.get.metadataFilters mustBe FileMetadataFilters(Some(true), None, Some(List(clientSideOriginalFilepath, fileType))).some

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the closure status page with the closure status checkbox checked and disabled when the closure status is already set to 'closed'" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed")

      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}").withCSRFToken)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId, isChecked = true)

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed", "judgment", getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}").withCSRFToken)

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
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidJudgmentUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed", "standard", getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if no files exist for the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setDisplayPropertiesResponse(wiremockServer)

      val dataString = s"""{"data":{"getConsignment":{"consignmentReference":"TEST","files":[]}}}""".stripMargin
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }

    "return an error if the consignment doesn't exist" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setDisplayPropertiesResponse(wiremockServer)

      val dataString = """{"data":{"getConsignment":null},"errors":[]}"""
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage mustBe s"No consignment found for consignment $consignmentId"
    }
  }

  "submitClosureStatus" should {
    "render the closure status page and display an error when the form is submitted without selecting the checkbox" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = List(UUID.randomUUID()))
      setDisplayPropertiesResponse(wiremockServer)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val incompleteClosureStatusForm: Seq[(String, String)] = Seq()

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .submitClosureStatus(consignmentId, metadataType(0), fileIds)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}")
            .withFormUrlEncodedBody(incompleteClosureStatusForm: _*)
            .withCSRFToken
        )

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId, isErrorOnThePage = true)

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "should save the metadata and redirect to the next page when the closure status checkbox is checked" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setBulkUpdateMetadataResponse(wiremockServer)
      val completedClosureStatusForm: Seq[(String, String)] = Seq(("closureStatus", "true"))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .submitClosureStatus(consignmentId, metadataType(0), fileIds)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/additional-metadata/status/${metadataType(0)}")
            .withFormUrlEncodedBody(completedClosureStatusForm: _*)
            .withCSRFToken
        )

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(
        Some(s"/consignment/$consignmentId/additional-metadata/add/${metadataType(0)}?fileIds=${fileIds.mkString("&")}")
      )

      case class GraphqlRequestData(query: String, variables: abfm.Variables)
      val events = wiremockServer.getAllServeEvents
      val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("addBulkFileMetadata")).get
      val request: GraphqlRequestData = decode[GraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(GraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))

      val input = request.variables.updateBulkFileMetadataInput
      input.consignmentId mustBe consignmentId
      input.fileIds mustBe fileIds
      input.metadataProperties.find(_.filePropertyName == closureType.name).get.value mustBe closureType.value

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }
  }

  def createController(
      closureType: String,
      consignmentType: String = "standard",
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      controllerComponents: SecurityComponents = getAuthorisedSecurityComponents
  ): AdditionalMetadataClosureStatusController = {
    setConsignmentTypeResponse(wiremockServer, consignmentType)
    setDisplayPropertiesResponse(wiremockServer)
    setConsignmentFilesMetadataResponse(wiremockServer, fileIds = List(UUID.randomUUID()), closureType = closureType)

    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)
    val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
    val asyncCacheApi = MockAsyncCacheApi()
    new AdditionalMetadataClosureStatusController(consignmentService, customMetadataService, displayPropertiesService, keycloakConfiguration, controllerComponents, asyncCacheApi)
  }

  // scalastyle:off method.length
  def verifyClosureStatusPage(closureStatusPage: String, consignmentId: UUID, isChecked: Boolean = false, isErrorOnThePage: Boolean = false): Unit = {

    checkPageForStaticElements.checkContentOfPagesThatUseMainScala(closureStatusPage, userType = "standard")
    if (isErrorOnThePage) {
      closureStatusPage must include("<div class=\"govuk-form-group govuk-form-group--error\">")
      closureStatusPage must include(
        """<p class="govuk-error-message" id="error-closure">
          |        <span class="govuk-visually-hidden">Error:</span>
          |        You must confirm this closure has been approved before continuing.
          |    </p>""".stripMargin
      )
    }

    closureStatusPage must include("Confirm closure status")
    closureStatusPage must include("<p class=\"govuk-body govuk-!-margin-bottom-2 govuk-!-font-weight-bold\">You are adding closure status to the following files:</p>")
    closureStatusPage must include("<li>original/file/path</li>")
    closureStatusPage must include(
      """<h2 class="govuk-fieldset__heading">
        |                                    Has this closure status been agreed with the Advisory Council and/or The National Archives SIRO?
        |                                </h2>""".stripMargin
    )
    val href = s"/consignment/$consignmentId/additional-metadata/status/closure?fileIds=${fileIds.mkString("&")}"
    closureStatusPage must include(s"""<form action="$href" method="POST" novalidate="">""")
    closureStatusPage must include(
      s"""<input
          |                ${if (isChecked) "checked" else ""}
          |                class="govuk-checkboxes__input"
          |                id="closureStatus"
          |                name="closureStatus"
          |                type="checkbox"
          |                value="true"
          |                ${if (isChecked) "disabled" else ""} />
          |            <label class="govuk-label govuk-checkboxes__label" for="closureStatus">
          |                Yes, I confirm
          |            </label>""".stripMargin
    )

    closureStatusPage must include(
      """<span class="govuk-details__summary-text">
        |                                When you click continue, you will be asked to provide the following mandatory information for closed records.
        |                            </span>""".stripMargin
    )
    closureStatusPage must include("""
        |                            <ul class="govuk-list govuk-list--bullet govuk-list--spaced">
        |<Spaces>
        |                                    <li>FOI decision asserted, this is the date of the Advisory Council approval</li>
        |<Spaces>
        |                                    <li>Closure start date</li>
        |<Spaces>
        |                                    <li>Closure period</li>
        |<Spaces>
        |                                    <li>FOI exemption code</li>
        |<Spaces>
        |                            </ul>
        |""".stripMargin.replace("<Spaces>", "                                "))

    val cancelHref = s"/consignment/$consignmentId/additional-metadata/files/closure"
    val continueHref = s"/consignment/$consignmentId/additional-metadata/add/closure?fileIds=${fileIds.mkString("&")}"
    if (isChecked) {
      closureStatusPage must include(
        s"""<a class="govuk-button" href="$continueHref" role="button" draggable="false" data-module="govuk-button">
           |                                Continue
           |                            </a>""".stripMargin
      )
      closureStatusPage must include("closureStatus.warningMessage")
    } else {
      closureStatusPage must include(
        s"""                            <button type= "submit" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
           |                                Continue
           |                            </button>""".stripMargin
      )
      closureStatusPage must not include ("closureStatus.warningMessage")
    }
    closureStatusPage must include(s"""<a class="govuk-link" href="$cancelHref">Cancel</a>""".stripMargin)
  }
}
