package controllers

import akka.Done
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.GraphQlException
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.types.UpdateBulkFileMetadataInput
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
import testUtils.DefaultMockFormOptions.{expectedClosureDefaultOptions, expectedClosureDependencyDefaultOptions}
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper}
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

  "AddAdditionalMetadataController GET" should {
    "render the add additional metadata page, with the default closure form, if file has no additional metadata, for an authenticated standard user" in {
      val closureMetadataType = metadataType(0)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
      setCustomMetadataResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, metadataType(0), fileIds)
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
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes")
      )

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add additional metadata page, with the closure form updated with the file's additional metadata, for an authenticated standard user" in {
      val closureMetadataType = metadataType(0)

      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
      setCustomMetadataResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, metadataType(0), fileIds)
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
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes")
      )

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")
      addAdditionalMetadataPageAsString must include(
        "<li>original/file/path</li>"
      )
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add additional metadata page, with the default descriptive form, if file has no additional metadata, for an authenticated standard user" in {
      val descriptiveMetadataType = metadataType(1)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
      setDisplayPropertiesResponse(wiremockServer)
      setCustomMetadataResponse(wiremockServer)

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadata(consignmentId, descriptiveMetadataType, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/$descriptiveMetadataType").withCSRFToken)
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputtext-description-description", ""),
        ("inputdropdown-Language-Language", "")
      )

      playStatus(addAdditionalMetadataPage) mustBe OK
      contentType(addAdditionalMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addAdditionalMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateAddAdditionalMetadataController(getUnauthorisedSecurityComponents)
      val addAdditionalMetadataPage = controller
        .addAdditionalMetadata(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}").withCSRFToken)
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
        .addAdditionalMetadata(consignmentId, metadataType(0), fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}").withCSRFToken)

      val failure = addAdditionalMetadataPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "rerender form with errors for each field if empty form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputdropdown-FoiExemptionCode", ""),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(
        addAdditionalMetadataPageAsString,
        formSubmission.toMap.removed("inputradio-DescriptionClosed"),
        formStatus = "PartiallySubmitted"
      )
    }

    "rerender form with user's data if form is partially submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions.filterNot(_.name.contains("DescriptionClosed")))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", "5"),
        ("inputdate-FoiExemptionAsserted-month", "11"),
        ("inputdate-FoiExemptionAsserted-year", "2021"),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
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

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", "5"),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addAdditionalMetadataPageAsString = contentAsString(addAdditionalMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addAdditionalMetadataPageAsString, userType = "standard")
      checkForExpectedClosureFormPageContent(addAdditionalMetadataPageAsString)
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
          .addAdditionalMetadata(consignmentId, metadataType(0), fileIds)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/add").withCSRFToken)
      }
      playStatus(addAdditionalMetadata) mustBe FORBIDDEN
    }
  }

  "AddAdditionalMetadataController POST" should {
    "send the form data to the API" in {
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
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "no")
      )

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)

      addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, "closure", fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
        .futureValue

      case class GraphqlRequestData(query: String, variables: abfm.Variables)
      val events = wiremockServer.getAllServeEvents
      val addMetadataEvent = events.asScala.find(event => event.getRequest.getBodyAsString.contains("addBulkFileMetadata")).get
      val request: GraphqlRequestData = decode[GraphqlRequestData](addMetadataEvent.getRequest.getBodyAsString)
        .getOrElse(GraphqlRequestData("", abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, Nil, Nil))))

      val input = request.variables.updateBulkFileMetadataInput
      input.consignmentId mustBe consignmentId
      input.fileIds mustBe fileIds
      input.metadataProperties.find(_.filePropertyName == "FoiExemptionCode").get.value mustBe "mock code1"
      input.metadataProperties.find(_.filePropertyName == "FoiExemptionAsserted").get.value mustBe "1970-01-01 00:00:00.0"
      input.metadataProperties.find(_.filePropertyName == "ClosureStartDate").get.value mustBe "1970-01-01 00:00:00.0"
      input.metadataProperties.find(_.filePropertyName == "TitleClosed").get.value mustBe "false"
      input.metadataProperties.find(_.filePropertyName == "DescriptionClosed").get.value mustBe "false"
      input.metadataProperties.find(_.filePropertyName == "ClosurePeriod").get.value mustBe "10"
    }

    s"redirect user to additional summary page if there are no fields that need their dependencies filled in" in {
      val consignmentId = UUID.randomUUID()
      val addAdditionalMetadataController = instantiateAddAdditionalMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setCustomMetadataResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)

      val formToSubmitDefault = Seq(
        ("inputdate-FoiExemptionAsserted-day", "1"),
        ("inputdate-FoiExemptionAsserted-month", "1"),
        ("inputdate-FoiExemptionAsserted-year", "1970"),
        ("inputdate-ClosureStartDate-day", "1"),
        ("inputdate-ClosureStartDate-month", "1"),
        ("inputdate-ClosureStartDate-year", "1970"),
        ("inputnumeric-ClosurePeriod-years", "10"),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "no"),
        ("inputradio-DescriptionClosed", "exclude")
      )

      val addAdditionalMetadataPage: Result = addAdditionalMetadataController
        .addAdditionalMetadataSubmit(consignmentId, metadataType(0), fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/additional-metadata/add/${metadataType(0)}")
            .withFormUrlEncodedBody(formToSubmitDefault: _*)
            .withCSRFToken
        )
        .futureValue

      val propertyNamesWhereUserDoesNotHaveToFillInDeps: Seq[String] = formToSubmitDefault.map { case (field, _) =>
        field.split("-")(1)
      }

      val redirectLocation = addAdditionalMetadataPage.header.headers.getOrElse("Location", "")

      addAdditionalMetadataPage.header.status should equal(303)
      redirectLocation.contains(s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}") should equal(true)

      propertyNamesWhereUserDoesNotHaveToFillInDeps.foreach { propertyNameWhereUserDoesNotHaveToFillInDeps =>
        redirectLocation.contains(s"$propertyNameWhereUserDoesNotHaveToFillInDeps-True") should equal(false)
      }
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

  // scalastyle:off method.length
  private def checkForExpectedClosureFormPageContent(addClosureFormPageAsFormattedString: String): Unit = {
    val addAdditionalMetadataPageAsString = addClosureFormPageAsFormattedString.replaceAll(twoOrMoreSpaces, "")
    val closureMetadataHtmlElements = Set(
      """      <title>Add closure metadata to files</title>""",
      """      <h1 class="govuk-heading-l">Add closure metadata to</h1>""",
      """      <p class="govuk-body">Enter metadata for closure fields here.</p>""",
      """            <h2 class="govuk-label govuk-label--m">
        |                FOI decision asserted
        |            </h2>""",
      """        <div id="date-input-FoiExemptionAsserted-hint" class="govuk-hint">
        |            Date of the Advisory Council approval (or SIRO approval if appropriate)
        |        </div>""",
      """            <h2 class="govuk-label govuk-label--m">
        |                Closure start date
        |            </h2>""",
      """        <div id="date-input-ClosureStartDate-hint" class="govuk-hint">
        |            This has been defaulted to the last date modified. If this is not correct, amend the field below.
        |        </div>""",
      """        <label class="govuk-label govuk-label--m" for=years>
        |            Closure period
        |        </label>""",
      """    <div id="numeric-input-hint" class="govuk-hint">
        |        Number of years the record is closed from the closure start date
        |    </div>""",
      """            <label class="govuk-label govuk-label--m" for="inputdropdown-FoiExemptionCode">
        |                FOI exemption code
        |            </label>""".replace("................", "                "),
      """        <div id="inputdropdown-FoiExemptionCode-hint" class="govuk-hint">
        |            Add one or more exemption code to this closure. Here is a<a target="_blank" href="https://www.legislation.gov.uk/ukpga/2000/36/contents">full list of FOI codes and their designated exemptions</a>.
        |        </div>""",
      """<select class="govuk-select" id="inputdropdown-FoiExemptionCode" name="inputdropdown-FoiExemptionCode"  >""",
      """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
        |            Is the title closed?
        |        </legend>""",
      """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
        |            Is the description closed?
        |        </legend>"""
    )

    closureMetadataHtmlElements.foreach { htmlElement =>
      addAdditionalMetadataPageAsString must include(
        htmlElement.stripMargin.replaceAll(twoOrMoreSpaces, "")
      )
    }
  }

  private def checkForExpectedAdditionalMetadataDependenciesFormPageContent(
      addAdditionalMetadataDependenciesPageAsFormattedString: String,
      fullNameAndName: Map[String, String]
  ): Unit = {
    val title = fullNameAndName.keys.mkString(" and ")
    val pronoun = if (fullNameAndName.size > 1) "they contain" else "it contains"

    val addAdditionalMetadataDependenciesPageAsString = addAdditionalMetadataDependenciesPageAsFormattedString.replaceAll(twoOrMoreSpaces, "")
    val closureMetadataDependenciesHtmlElements = Set(
      s"""      <title>Add an $title to files</title>""",
      s"""      <h1 class="govuk-heading-l">Add an $title to</h1>""",
      s"""      <p class="govuk-body">Enter a publicly visible $title if, for example, $pronoun sensitive information.
                | For guidance on how to create an $title, read our FAQs (opens in a new tab)</p>""".stripMargin
    )
    closureMetadataDependenciesHtmlElements.foreach { closureMetadataDependenciesHtmlElement =>
      addAdditionalMetadataDependenciesPageAsString must include(closureMetadataDependenciesHtmlElement.replaceAll(twoOrMoreSpaces, ""))
    }

    fullNameAndName.foreach { case (fullName, name) =>
      addAdditionalMetadataDependenciesPageAsString must include(
        s"""        <label class="govuk-label govuk-label--m" for=$name>
            |            $fullName
            |        </label>""".stripMargin.replaceAll(twoOrMoreSpaces, "")
      )
    }
  }
  // scalastyle:on method.length
}

case class MockAsyncCacheApi()(implicit val ec: ExecutionContext) extends AsyncCacheApi {
  override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future(Done)
  override def remove(key: String): Future[Done] = Future(Done)
  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit evidence$1: ClassTag[A]): Future[A] = orElse
  override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future(None)
  override def removeAll(): Future[Done] = Future(Done)
}
