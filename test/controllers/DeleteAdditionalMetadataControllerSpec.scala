package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.util.MetadataProperty.clientSideOriginalFilepath
import graphql.codegen.DeleteFileMetadata.{deleteFileMetadata => dfm}
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.types.FileMetadataFilters
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status.{FORBIDDEN, FOUND, OK, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper, GetConsignmentFilesMetadataGraphqlRequestData}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala

class DeleteAdditionalMetadataControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)
  val closureMetadataType = metadataType(0)
  val descriptiveMetadataType = metadataType(1)

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

  "confirmDeleteAdditionalMetadata" should {
    "render the delete closure metadata page with an authenticated user for the 'closure' metadata type" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType").withCSRFToken)
      val deleteMetadataPage = contentAsString(response)

      val events = wiremockServer.getAllServeEvents
      val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("getConsignmentFilesMetadata")).get
      val request: GetConsignmentFilesMetadataGraphqlRequestData = decode[GetConsignmentFilesMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(GetConsignmentFilesMetadataGraphqlRequestData("", gcfm.Variables(consignmentId, None)))

      val input = request.variables.fileFiltersInput
      input.get.selectedFileIds mustBe fileIds.some
      input.get.metadataFilters mustBe FileMetadataFilters(None, None, Some(List(clientSideOriginalFilepath))).some

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(deleteMetadataPage, userType = "standard", consignmentExists = false)
      checkConfirmDeleteMetadataPage(deleteMetadataPage, consignmentId, closureMetadataType)
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the delete closure metadata page with an authenticated user for the 'descriptive' metadata type" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType").withCSRFToken)
      val deleteMetadataPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(deleteMetadataPage, userType = "standard", consignmentExists = false)
      checkConfirmDeleteMetadataPage(deleteMetadataPage, consignmentId, descriptiveMetadataType)
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return forbidden if the page is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidJudgmentUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )

      val closureResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType"))

      val descriptiveResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType"))

      status(closureResponse) mustBe FORBIDDEN
      status(descriptiveResponse) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )

      val closureResponse: Throwable = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType"))
        .failed
        .futureValue

      val descriptiveResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType"))
        .failed
        .futureValue

      closureResponse.getMessage must include(errors.head.message)
      descriptiveResponse.getMessage must include(errors.head.message)
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getUnauthorisedSecurityComponents
        )

      val closureResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType"))

      val descriptiveResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType"))

      status(closureResponse) mustBe FOUND
      redirectLocation(closureResponse).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")

      status(descriptiveResponse) mustBe FOUND
      redirectLocation(descriptiveResponse).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if the fileIds are empty" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val errorMessage = "fileIds are empty"

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )

      val closureResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, Nil)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType"))
        .failed
        .futureValue

      val descriptiveResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, Nil)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType"))
        .failed
        .futureValue

      closureResponse.getMessage must include(errorMessage)
      descriptiveResponse.getMessage must include(errorMessage)
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

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )

      val closureResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$closureMetadataType"))
        .failed
        .futureValue

      val descriptiveResponse = controller
        .confirmDeleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/$descriptiveMetadataType"))
        .failed
        .futureValue

      closureResponse.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
      descriptiveResponse.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }
  }

  "deleteAdditionalMetadata" should {
    "delete the correct metadata and redirect to the navigation page for the 'closure' metadata type" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setDeleteFileMetadataResponse(wiremockServer, fileIds, List("PropertyName1"))
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = mock[CustomMetadataService]
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val mockData = mock[dfm.Data]
      val fileIdsArg: ArgumentCaptor[List[UUID]] = ArgumentCaptor.forClass(classOf[List[UUID]])
      val propertiesToDeleteArg: ArgumentCaptor[Set[String]] = ArgumentCaptor.forClass(classOf[Set[String]])

      when(customMetadataService.deleteMetadata(fileIdsArg.capture(), ArgumentMatchers.any[BearerAccessToken], propertiesToDeleteArg.capture())).thenReturn(Future(mockData))

      val controller = new DeleteAdditionalMetadataController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents
      )

      val response = controller
        .deleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$closureMetadataType"))

      status(response) mustBe SEE_OTHER
      fileIdsArg.getValue mustEqual fileIds
      propertiesToDeleteArg.getValue mustEqual Set(
        "DescriptionClosed",
        "FoiExemptionCode",
        "FoiExemptionAsserted",
        "DescriptionAlternate",
        "ClosureStartDate",
        "ClosurePeriod",
        "TitleAlternate",
        "TitleClosed",
        "ClosureType"
      )

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/files/$closureMetadataType"))
    }

    "delete the correct metadata and redirect to the navigation page for the 'descriptive' metadata type" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setDeleteFileMetadataResponse(wiremockServer, fileIds, List("PropertyName1"))
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = mock[CustomMetadataService]
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val mockData = mock[dfm.Data]
      val fileIdsArg: ArgumentCaptor[List[UUID]] = ArgumentCaptor.forClass(classOf[List[UUID]])
      val propertiesToDeleteArg: ArgumentCaptor[Set[String]] = ArgumentCaptor.forClass(classOf[Set[String]])

      when(customMetadataService.deleteMetadata(fileIdsArg.capture(), ArgumentMatchers.any[BearerAccessToken], propertiesToDeleteArg.capture())).thenReturn(Future(mockData))

      val controller = new DeleteAdditionalMetadataController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents
      )

      val response = controller
        .deleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$descriptiveMetadataType"))

      status(response) mustBe SEE_OTHER
      fileIdsArg.getValue mustEqual fileIds
      propertiesToDeleteArg.getValue mustEqual Set("end_date", "description", "Language")

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/files/$descriptiveMetadataType"))
    }

    "return an error if the fileIds are empty" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val errorMessage = "fileIds are empty"

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller = new DeleteAdditionalMetadataController(
        consignmentService,
        customMetadataService,
        displayPropertiesService,
        getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents
      )

      val closureResponse = controller
        .deleteAdditionalMetadata(consignmentId, closureMetadataType, Nil)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$closureMetadataType"))
        .failed
        .futureValue

      val descriptiveResponse = controller
        .deleteAdditionalMetadata(consignmentId, descriptiveMetadataType, Nil)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$descriptiveMetadataType"))
        .failed
        .futureValue

      closureResponse.getMessage must include(errorMessage)
      descriptiveResponse.getMessage must include(errorMessage)
    }

    "return forbidden if the url is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidJudgmentUserKeycloakConfiguration,
          getAuthorisedSecurityComponents
        )

      val closureResponse = controller
        .deleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$closureMetadataType"))

      val descriptiveResponse = controller
        .deleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$closureMetadataType"))

      status(closureResponse) mustBe FORBIDDEN
      status(descriptiveResponse) mustBe FORBIDDEN
    }

    "redirect to the login page if the url is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

      val controller =
        new DeleteAdditionalMetadataController(
          consignmentService,
          customMetadataService,
          displayPropertiesService,
          getValidStandardUserKeycloakConfiguration,
          getUnauthorisedSecurityComponents
        )

      val closureResponse = controller
        .deleteAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$closureMetadataType"))

      val descriptiveResponse = controller
        .deleteAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/$descriptiveMetadataType"))

      status(closureResponse) mustBe FOUND
      redirectLocation(closureResponse).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")

      status(descriptiveResponse) mustBe FOUND
      redirectLocation(descriptiveResponse).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  private def checkConfirmDeleteMetadataPage(pageString: String, consignmentId: UUID, metadataType: String): Unit = {
    pageString must include(s"<title>Delete $metadataType metadata</title>")
    pageString must include(
      s"""              <h1 class="govuk-heading-xl">
                            Delete $metadataType metadata
                        </h1>""".stripMargin
    )

    pageString must include("<p class=\"govuk-body\">Confirm you wish to proceed.</p>")
    val deleteButtonHref =
      s"/consignment/$consignmentId/additional-metadata/delete-metadata/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
    val cancelButtonHref =
      s"/consignment/$consignmentId/additional-metadata/selected-summary/$metadataType?fileIds=${fileIds.mkString("&amp;")}"
    pageString must include(s"""form action="$deleteButtonHref"""")
    pageString must include(
      s"""                        <div class="govuk-button-group">
         |                            <button role="button" draggable="false" class="govuk-button govuk-button--warning" data-module="govuk-button" type="submit">
         |                                Delete and return to files
         |                            </button>
         |                            <a class="govuk-link govuk-link--no-visited-state" href="$cancelButtonHref">
         |                                Cancel
         |                            </a>
         |                        </div>""".stripMargin
    )
  }
}
