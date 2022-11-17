package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.MetadataProperty.closureType
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.types.UpdateBulkFileMetadataInput
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status._
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

// scalastyle:off line.size.limit
class AdditionalMetadataClosureStatusControllerSpec extends FrontEndTestHelper {

  val wiremockServer = new WireMockServer(9006)
  val parentFolderId: UUID = UUID.randomUUID()

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
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status").withCSRFToken)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId)

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the closure status page with the closure status checkbox checked and disabled when the closure status is already set to 'closed'" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed")

      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status").withCSRFToken)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId, isChecked = true)

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed", "judgment", getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status").withCSRFToken)

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
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        getValidJudgmentUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val controller = createController("Closed", "standard", getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if no files exist for the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = parentFolderId.some)

      val dataString = s"""{"data":{"getConsignment":{"consignmentReference":"TEST","files":[]}}}""".stripMargin
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status"))
        .failed
        .futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }

    "return an error if the consignment doesn't exist" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = """{"data":{"getConsignment":null},"errors":[]}"""
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .getClosureStatusPage(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure-status"))
        .failed
        .futureValue

      response.getMessage mustBe s"No consignment found for consignment $consignmentId"
    }
  }

  "submitClosureStatus" should {
    "render the closure status page and display an error when the form is submitted without selecting the checkbox" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = parentFolderId.some)
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = List(UUID.randomUUID()))

      val incompleteClosureStatusForm: Seq[(String, String)] = Seq()

      setConsignmentTypeResponse(wiremockServer, "standard")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .submitClosureStatus(consignmentId, fileIds)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/additional-metadata/closure-status")
            .withFormUrlEncodedBody(incompleteClosureStatusForm: _*)
            .withCSRFToken
        )

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("text/html")

      verifyClosureStatusPage(contentAsString(response), consignmentId, isChecked = false, true)

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
      val asyncCacheApi = MockAsyncCacheApi()
      val controller = new AdditionalMetadataClosureStatusController(
        consignmentService,
        customMetadataService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        asyncCacheApi
      )
      val response = controller
        .submitClosureStatus(consignmentId, fileIds)
        .apply(
          FakeRequest(POST, f"/consignment/$consignmentId/additional-metadata/closure-status")
            .withFormUrlEncodedBody(completedClosureStatusForm: _*)
            .withCSRFToken
        )

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(
        Some(s"/consignment/$consignmentId/additional-metadata/add?propertyNameAndFieldSelected=ClosureType-Closed&fileIds=${fileIds.mkString("&")}")
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
    setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = parentFolderId.some)
    setConsignmentFilesMetadataResponse(wiremockServer, fileIds = List(UUID.randomUUID()), closureType = closureType)

    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)
    val asyncCacheApi = MockAsyncCacheApi()
    new AdditionalMetadataClosureStatusController(consignmentService, customMetadataService, keycloakConfiguration, controllerComponents, asyncCacheApi)
  }

  // scalastyle:off method.length
  def verifyClosureStatusPage(closureStatusPage: String, consignmentId: UUID, isChecked: Boolean = false, isErrorOnThePage: Boolean = false): Unit = {
    val twoOrMoreSpaces = "\\s{2,}"
    val page = closureStatusPage.replaceAll(twoOrMoreSpaces, "")
    checkPageForStaticElements.checkContentOfPagesThatUseMainScala(closureStatusPage, userType = "standard")
    if (isErrorOnThePage) {
      page.contains("<div class=\"govuk-form-group govuk-form-group--error\">") mustBe true
      page.contains(
        """<p class="govuk-error-message" id="error-closure">
          |        <span class="govuk-visually-hidden">Error:</span>
          |        You must confirm this closure has been approved before continuing.
          |    </p>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
      ) mustBe true
    }

    page.contains("Confirm the closure status of your record") mustBe true
    page.contains("<p class=\"govuk-body\">You are updating the status for the selected file 'original/file/path'</p>") mustBe true
    page.contains(
      """<h2 class="govuk-fieldset__heading">
        |                                    Has this closure been approved by the Advisory Council?
        |                                </h2>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
    ) mustBe true
    val href = s"/consignment/$consignmentId/additional-metadata/closure-status?fileIds=${fileIds.mkString("&")}"
    page.contains(s"""<form action="$href" method="POST" novalidate="">""") mustBe true
    page.contains(
      "<input" + (if (isChecked) "checked" else "") +
        s"""                class="govuk-checkboxes__input"
          |                id="closureStatus"
          |                name="closureStatus"
          |                type="checkbox"
          |                value="true"
          |                ${if (isChecked) "disabled" else ""} />
          |            <label class="govuk-label govuk-checkboxes__label" for="closureStatus">
          |                Yes, I confirm
          |            </label>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
    ) mustBe true

    page must include(
      """<span class="govuk-details__summary-text">
        |                                You must provide the following information, as they are mandatory for closure.
        |                            </span>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
    )
    page must include(
      """<ul class="govuk-list govuk-list--bullet govuk-list--spaced">
        |                                <li>
        |                                    FOI decision asserted, this is the date of the Advisory Council approval
        |                                </li>
        |                                <li>
        |                                    Closure start date, this is the date the record starts
        |                                </li>
        |                                <li>
        |                                    Closure period
        |                                </li>
        |                                <li>
        |                                    FOI exemption code
        |                                </li>
        |                                <li>
        |                                    Title public
        |                                </li>
        |                            </ul>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
    )

    val cancelHref = s"/consignment/$consignmentId/additional-metadata/files/closure/"
    val continueButton = (if (isChecked) {
                            s"""<a class="govuk-button" href="/consignment/$consignmentId/add-closure-metadata"""
                          } else {
                            s"""<div class="govuk-button-group">
         |                        <button type= "submit" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
         |                            Continue
         |                        </button>
         |                        <a class="govuk-link" href="$cancelHref">Cancel</a>
         |                    </div>
         |"""
                          }).stripMargin.replaceAll(twoOrMoreSpaces, "").replaceAll("\n", "")
    page.contains(continueButton) mustBe true
  }
}
