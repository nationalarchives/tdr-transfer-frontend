package controllers

import akka.Done
import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import configuration.GraphQLConfiguration
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, description, descriptionClosed, titleClosed}
import controllers.util.{FormField, InputNameAndValue, RadioButtonGroupField}
import errors.GraphQlException
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.DeleteFileMetadata.{deleteFileMetadata => dfm}
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.types.{DeleteFileMetadataInput, FileMetadataFilters, UpdateBulkFileMetadataInput}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.cache.AsyncCacheApi
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import testUtils.DefaultMockFormOptions.{expectedClosureDefaultOptions, expectedClosureDependencyDefaultOptions, expectedDescriptiveDefaultOptions}
import testUtils._
import uk.gov.nationalarchives.tdr.GraphQLClient
import org.scalatest.concurrent.ScalaFutures._

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag

class AddAdditionalMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val twoOrMoreSpaces = "\\s{2,}"
  val checkPageForStaticElements = new CheckPageForStaticElements
  val checkFormElements = new CheckFormPageElements
  val fileIds: List[UUID] = List(UUID.fromString("ae4b7cad-ee83-46bd-b952-80bc8263c6c2"))

  private val dependencyFormTester = new FormTester(expectedClosureDependencyDefaultOptions)
  dependencyFormTester.generateOptionsToSelectToGenerateFormErrors("value", combineOptionNameWithValue = true)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  private val closureMetadataType = metadataType(0)
  private val descriptiveMetadataType = metadataType(1)

  "AddAdditionalMetadataController GET" should {
    "render the add additional metadata page, with the default closure form, if file has no additional metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType").withCSRFToken)
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes")
      )
      verifyConsignmentFileMetadataCall(consignmentId, FileMetadataFilters(Some(true), None, Some(List(clientSideOriginalFilepath, description))).some)
      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(closureMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add additional metadata page, with the default descriptive form, if file has no additional metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedDescriptiveDefaultOptions)

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$descriptiveMetadataType").withCSRFToken)
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputtextarea-description", ""),
        ("inputmultiselect-Language", "English")
      )

      verifyConsignmentFileMetadataCall(consignmentId, FileMetadataFilters(None, Some(true), None).some)
      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(descriptiveMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add additional metadata page, with the closure form updated with the file's additional metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType").withCSRFToken)

      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      val newInputTextValues = Map(
        "inputdate-FoiExemptionAsserted-day" -> "12",
        "inputdate-FoiExemptionAsserted-month" -> "1",
        "inputdate-FoiExemptionAsserted-year" -> "1995",
        "inputdate-ClosureStartDate-day" -> "1",
        "inputdate-ClosureStartDate-month" -> "12",
        "inputdate-ClosureStartDate-year" -> "1990",
        "inputnumeric-ClosurePeriod-years" -> "4"
      )
      val expectedOptions = expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")).map { mockOption =>
        newInputTextValues.get(mockOption.name) match {
          case Some(newValue) => mockOption.copy(value = newValue)
          case None           => mockOption
        }
      }

      val formTester = new FormTester(expectedOptions)

      val expectedDefaultForm = Seq(
        ("inputdate-FoiExemptionAsserted-day", "12"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1995"),
        ("inputdate-ClosureStartDate-day", "12"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1990"),
        ("inputnumeric-ClosurePeriod-years", "4"),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes")
      )

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")
      addAdditionalMetadataPageAsString must include(
        "<li>original/file/path</li>"
      )

      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(closureMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add additional metadata page, with the descriptive form updated with the file's additional metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedDescriptiveDefaultOptions)

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$descriptiveMetadataType").withCSRFToken)
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputtextarea-description", "a previously added description"),
        ("inputmultiselect-Language", "Welsh")
      )
      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")

      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(descriptiveMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateAddAdditionalMetadataController(getUnauthorisedSecurityComponents)
      val addAdditionalMetadataPage = controller
        .addAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType").withCSRFToken)
      redirectLocation(addAdditionalMetadataPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(addAdditionalMetadataPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val client = new GraphQLConfiguration(app.configuration).getClient[cm.Data, cm.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateAddAdditionalMetadataController()
      val addAdditionalMetadataPage = controller
        .addAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType").withCSRFToken)

      val failure = addAdditionalMetadataPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "rerender form with errors for each closure field if empty form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, closureMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(closureMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(
        addAdditionalMetadataPageAsString,
        formSubmission.toMap.removed("inputradio-DescriptionClosed"),
        formStatus = "PartiallySubmitted"
      )
      addAdditionalMetadataPageAsString should include("""    <p class="govuk-error-message" id="error-FoiExemptionCode">
          |        <span class="govuk-visually-hidden">Error:</span>
          |        Search for and select at least one FOI exemption code(s)
          |    </p>""".stripMargin)
    }

    "rerender closure form with user's data if form is partially submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", "5"),
        ("inputdate-FoiExemptionAsserted-month", "11"),
        ("inputdate-FoiExemptionAsserted-year", "2021"),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, closureMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(closureMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(
        addAdditionalMetadataPageAsString,
        formSubmission.toMap.removed("inputradio-DescriptionClosed"),
        formStatus = "PartiallySubmitted"
      )
    }

    "display the most immediate date error if more than one date input (per date field) has an mistake in it" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", "5"),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      getServeEvent("addBulkFileMetadata") should be(None)
      getServeEvent("deleteFileMetadata") should be(None)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkFormElements.checkFormContent(closureMetadataType, addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(
        addAdditionalMetadataPageAsString,
        formSubmission.toMap.removed("inputradio-DescriptionClosed"),
        formStatus = "PartiallySubmitted"
      )
    }

    // need to add check for complete form submission
  }

  s"The consignment add additional metadata page" should {
    s"return 403 if the GET is accessed by a non-standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      val addAdditionalMetadata = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
        addAdditionalMetadataController
          .addAdditionalMetadata(consignmentId, closureMetadataType, fileIds)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/add").withCSRFToken)
      }
      playStatus(addAdditionalMetadata) mustBe FORBIDDEN
    }
  }

  "AddAdditionalMetadataController POST" should {
    "send the closure form data to the API and delete the dependencies if the relevant option is not selected by user" in {
      val consignmentId = UUID.randomUUID()
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", "1"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1970"),
        ("inputdate-ClosureStartDate-day", "1"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1970"),
        ("inputnumeric-ClosurePeriod-years", "10"),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "no")
      )

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)
      setDeleteFileMetadataResponse(wiremockServer)

      addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, closureMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
        .futureValue

      val addMetadataEvent = getServeEvent("addBulkFileMetadata").get
      val request: AddBulkFileMetadataGraphqlRequestData = decode[AddBulkFileMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(AddBulkFileMetadataGraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))

      val addInput = request.variables.updateBulkFileMetadataInput
      addInput.consignmentId mustBe consignmentId
      addInput.fileIds mustBe fileIds
      addInput.metadataProperties.find(_.filePropertyName == "FoiExemptionCode").get.value mustBe "mock code1"
      addInput.metadataProperties.find(_.filePropertyName == "FoiExemptionAsserted").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "ClosureStartDate").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "TitleClosed").get.value mustBe "false"
      addInput.metadataProperties.find(_.filePropertyName == "DescriptionClosed").get.value mustBe "false"
      addInput.metadataProperties.find(_.filePropertyName == "ClosurePeriod").get.value mustBe "10"

      val deleteMetadataEvent = getServeEvent("deleteFileMetadata").get
      val deleteRequest: DeleteFileMetadataGraphqlRequestData = decode[DeleteFileMetadataGraphqlRequestData](deleteMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(DeleteFileMetadataGraphqlRequestData("", dfm.Variables(DeleteFileMetadataInput(fileIds, Nil))))
      val deleteInput = deleteRequest.variables.deleteFileMetadataInput
      deleteInput.fileIds should be(fileIds)
      deleteInput.propertyNames should contain theSameElementsAs List("TitleAlternate", "DescriptionAlternate")
    }

    "send the closure form data to the API with dependencies and do not delete the dependencies if the relevant option is selected by user" in {
      val consignmentId = UUID.randomUUID()
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", "1"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1970"),
        ("inputdate-ClosureStartDate-day", "1"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1970"),
        ("inputnumeric-ClosurePeriod-years", "10"),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputmultiselect-FoiExemptionCode", "mock code2"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-TitleClosed-TitleAlternate-yes", "text"),
        ("inputradio-DescriptionClosed", "no")
      )

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)
      setDeleteFileMetadataResponse(wiremockServer)

      addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
        .futureValue

      val addMetadataEvent = getServeEvent("addBulkFileMetadata").get
      val request: AddBulkFileMetadataGraphqlRequestData = decode[AddBulkFileMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(AddBulkFileMetadataGraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))

      val addInput = request.variables.updateBulkFileMetadataInput
      addInput.consignmentId mustBe consignmentId
      addInput.fileIds mustBe fileIds
      addInput.metadataProperties.filter(_.filePropertyName == "FoiExemptionCode").map(_.value) mustBe List("mock code1", "mock code2")
      addInput.metadataProperties.find(_.filePropertyName == "FoiExemptionAsserted").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "ClosureStartDate").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "TitleClosed").get.value mustBe "true"
      addInput.metadataProperties.find(_.filePropertyName == "TitleAlternate").get.value mustBe "text"
      addInput.metadataProperties.find(_.filePropertyName == "DescriptionClosed").get.value mustBe "false"
      addInput.metadataProperties.find(_.filePropertyName == "ClosurePeriod").get.value mustBe "10"

      val deleteMetadataEvent = getServeEvent("deleteFileMetadata").get
      val deleteRequest: DeleteFileMetadataGraphqlRequestData = decode[DeleteFileMetadataGraphqlRequestData](deleteMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(DeleteFileMetadataGraphqlRequestData("", dfm.Variables(DeleteFileMetadataInput(fileIds, Nil))))
      val deleteInput = deleteRequest.variables.deleteFileMetadataInput
      deleteInput.fileIds should be(fileIds)
      deleteInput.propertyNames should contain theSameElementsAs List("DescriptionAlternate")
    }

    "send the closure form data to the API with dependencies and do not call delete metadata to remove the dependencies if the relevant option is selected by user for all properties" in {
      val consignmentId = UUID.randomUUID()
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", "1"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1970"),
        ("inputdate-ClosureStartDate-day", "1"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1970"),
        ("inputnumeric-ClosurePeriod-years", "10"),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-TitleClosed-TitleAlternate-yes", "text"),
        ("inputradio-DescriptionClosed", "yes"),
        ("inputradio-DescriptionClosed-DescriptionAlternate-yes", "text")
      )

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)
      setDeleteFileMetadataResponse(wiremockServer)

      addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, closureMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
        .futureValue

      val addMetadataEvent = getServeEvent("addBulkFileMetadata").get
      val request: AddBulkFileMetadataGraphqlRequestData = decode[AddBulkFileMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(AddBulkFileMetadataGraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))

      val addInput = request.variables.updateBulkFileMetadataInput
      addInput.consignmentId mustBe consignmentId
      addInput.fileIds mustBe fileIds
      addInput.metadataProperties.find(_.filePropertyName == "FoiExemptionCode").get.value mustBe "mock code1"
      addInput.metadataProperties.find(_.filePropertyName == "FoiExemptionAsserted").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "ClosureStartDate").get.value mustBe "1970-01-01 00:00:00.0"
      addInput.metadataProperties.find(_.filePropertyName == "TitleClosed").get.value mustBe "true"
      addInput.metadataProperties.find(_.filePropertyName == "TitleAlternate").get.value mustBe "text"
      addInput.metadataProperties.find(_.filePropertyName == "DescriptionClosed").get.value mustBe "true"
      addInput.metadataProperties.find(_.filePropertyName == "DescriptionAlternate").get.value mustBe "text"
      addInput.metadataProperties.find(_.filePropertyName == "ClosurePeriod").get.value mustBe "10"

      getServeEvent("deleteFileMetadata") should be(None)
    }

    s"redirect user to additional summary page if there are no closure fields that need their dependencies filled in" in {
      val consignmentId = UUID.randomUUID()
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)
      setDeleteFileMetadataResponse(wiremockServer)

      val formToSubmitDefault = Seq(
        ("inputdate-FoiExemptionAsserted-day", "1"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1970"),
        ("inputdate-ClosureStartDate-day", "1"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1970"),
        ("inputnumeric-ClosurePeriod-years", "10"),
        ("inputmultiselect-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage: Result = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, closureMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$closureMetadataType")
            .withFormUrlEncodedBody(formToSubmitDefault: _*)
            .withCSRFToken
        )
        .futureValue

      val propertyNamesWhereUserDoesNotHaveToFillInDeps: Seq[String] = formToSubmitDefault.map { case (field, _) =>
        field.split("-")(1)
      }

      val redirectLocation = addAdditionalMetadataPage.header.headers.getOrElse("Location", "")

      addAdditionalMetadataPage.header.status should equal(303)
      redirectLocation must include(s"/consignment/$consignmentId/additional-metadata/selected-summary/$closureMetadataType")

      propertyNamesWhereUserDoesNotHaveToFillInDeps.foreach { propertyNameWhereUserDoesNotHaveToFillInDeps =>
        redirectLocation must not include (s"$propertyNameWhereUserDoesNotHaveToFillInDeps-True")
      }
    }

    s"save any non-empty metadata values and delete empty metadata values" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
      setCustomMetadataResponse(wiremockServer)
      setDisplayPropertiesResponse(wiremockServer)
      setDeleteFileMetadataResponse(wiremockServer, fileIds = fileIds)
      setBulkUpdateMetadataResponse(wiremockServer)

      val formToSubmitWithEmptyValue = Seq(
        ("inputtextarea-description", ""),
        ("inputmultiselect-Language", "Welsh")
      )

      addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, descriptiveMetadataType, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/$descriptiveMetadataType")
            .withFormUrlEncodedBody(formToSubmitWithEmptyValue: _*)
            .withCSRFToken
        )
        .futureValue

      val events = wiremockServer.getAllServeEvents
      val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("addBulkFileMetadata")).get
      val deleteMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("deleteFileMetadata")).get
      val addRequest: AddBulkFileMetadataGraphqlRequestData = decode[AddBulkFileMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(AddBulkFileMetadataGraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))
      val deleteRequest: DeleteFileMetadataGraphqlRequestData = decode[DeleteFileMetadataGraphqlRequestData](deleteMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(DeleteFileMetadataGraphqlRequestData("", dfm.Variables(DeleteFileMetadataInput(Nil, Nil))))

      val addInput = addRequest.variables.updateBulkFileMetadataInput
      addInput.fileIds should equal(fileIds)
      addInput.metadataProperties.size shouldBe 1
      addInput.metadataProperties.head.value should equal("Welsh")
      addInput.metadataProperties.head.filePropertyName should equal("Language")

      val deleteInput = deleteRequest.variables.deleteFileMetadataInput
      deleteInput.fileIds should equal(fileIds)
      deleteInput.propertyNames.size shouldBe 1
      deleteInput.propertyNames.head should equal("description")
    }
  }

  "AddAdditionalMetadataController formFieldOverrides" should {
    "override the 'descriptionClosed' field when the description is not empty" in {
      val fileMetadata: Map[String, List[FileMetadata]] = Map(
        descriptionClosed -> List(FileMetadata(descriptionClosed, "true")),
        description -> List(FileMetadata(description, "some value"))
      )
      val formField: FormField = RadioButtonGroupField(descriptionClosed, "name", "description", "", false, Seq(InputNameAndValue("Yes", "Yes")), "Yes", false)
      val actualFormField = AddAdditionalMetadataController.formFieldOverrides(formField, fileMetadata)

      val expectedField = RadioButtonGroupField(
        descriptionClosed,
        "name",
        "The current description of your record is below. You can edit it in the Descriptive metadata step.",
        "some value",
        false,
        Seq(InputNameAndValue("Yes", "Yes")),
        "Yes",
        false
      )
      actualFormField should equal(expectedField)
    }

    "not override the 'descriptionClosed' field when the description is empty" in {

      val fileMetadata: Map[String, List[FileMetadata]] = Map(
        descriptionClosed -> List(FileMetadata(descriptionClosed, "true"))
      )
      val formField: FormField = RadioButtonGroupField(descriptionClosed, "name", "description", "", false, Seq(InputNameAndValue("Yes", "Yes")), "Yes", false)
      val actualFormField = AddAdditionalMetadataController.formFieldOverrides(formField, fileMetadata)

      val expectedField = RadioButtonGroupField(
        descriptionClosed,
        "name",
        "If you need to add a description, you can do so in the Descriptive metadata step.",
        "",
        false,
        Seq(InputNameAndValue("Yes", "Yes")),
        "Yes",
        false,
        hideInputs = true
      )
      actualFormField should equal(expectedField)
    }

    "not override the field when the given field is not descriptionClosed" in {
      val fileMetadata: Map[String, List[FileMetadata]] = Map(
        titleClosed -> List(FileMetadata(titleClosed, "true")),
        description -> List(FileMetadata(description, "some value"))
      )
      val formField: FormField = RadioButtonGroupField(titleClosed, "name", "description", "", false, Seq(InputNameAndValue("Yes", "Yes")), "Yes", false)
      val actualFormField = AddAdditionalMetadataController.formFieldOverrides(formField, fileMetadata)
      actualFormField should equal(formField)
    }
  }

  private def instantiateAddAdditionalMetadataController(securityComponents: SecurityComponents = getAuthorisedSecurityComponents) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)
    val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)

    new AddAdditionalMetadataController(
      securityComponents,
      new GraphQLConfiguration(app.configuration),
      getValidStandardUserKeycloakConfiguration,
      consignmentService,
      customMetadataService,
      displayPropertiesService,
      MockAsyncCacheApi()
    )
  }

  private def verifyConsignmentFileMetadataCall(consignmentId: UUID, expectedFileMetadataFilters: Option[FileMetadataFilters]): Unit = {
    val events = wiremockServer.getAllServeEvents
    val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("getConsignmentFilesMetadata")).get
    val request: GetConsignmentFilesMetadataGraphqlRequestData = decode[GetConsignmentFilesMetadataGraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
      .getOrElse(GetConsignmentFilesMetadataGraphqlRequestData("", gcfm.Variables(consignmentId, None)))

    val input = request.variables.fileFiltersInput
    input.get.selectedFileIds mustBe fileIds.some
    input.get.metadataFilters mustBe expectedFileMetadataFilters
  }

  private def getServeEvent(request: String): Option[ServeEvent] = {
    val events = wiremockServer.getAllServeEvents
    events.asScala.find(event => event.getRequest.getBodyAsString.contains(request))
  }
}

case class MockAsyncCacheApi()(implicit val ec: ExecutionContext) extends AsyncCacheApi {
  override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future(Done)
  override def remove(key: String): Future[Done] = Future(Done)
  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit evidence$1: ClassTag[A]): Future[A] = orElse
  override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future(None)
  override def removeAll(): Future[Done] = Future(Done)
}
