package controllers

import akka.Done
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.GraphQlException
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import play.api.Play.materializer
import play.api.cache.AsyncCacheApi
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper, MockInputOption}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class AddClosureMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements
  val expectedDefaultOptions: List[MockInputOption] = List(
    MockInputOption(
      name="inputdate-ClosureStartDate-day",
      label="Day",
      id="date-input-ClosureStartDate-day",
      placeholder="dd",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Day."
    ),
    MockInputOption(
      name="inputdate-ClosureStartDate-month",
      label="Month",
      id="date-input-ClosureStartDate-month",
      placeholder="mm",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Month.",
      errorMessageDependency="inputdate-ClosureStartDate-day"
    ),
    MockInputOption(
      name="inputdate-ClosureStartDate-year",
      label="Year",
      id="date-input-ClosureStartDate-year",
      placeholder="yyyy",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Year.",
      errorMessageDependency="inputdate-ClosureStartDate-month"
    ),
    MockInputOption(
      name="inputdate-FoiExemptionAsserted-day",
      label="Day",
      id="date-input-FoiExemptionAsserted-day",
      placeholder="dd",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Day."
    ),
    MockInputOption(
      name="inputdate-FoiExemptionAsserted-month",
      label="Month",
      id="date-input-FoiExemptionAsserted-month",
      placeholder="mm",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Month.",
      errorMessageDependency="inputdate-FoiExemptionAsserted-day"
    ),
    MockInputOption(
      name="inputdate-FoiExemptionAsserted-year",
      label="Year",
      id="date-input-FoiExemptionAsserted-year",
      placeholder="yyyy",
      fieldType="inputDate",
      errorMessage=s"There was no number entered for the Year.",
      errorMessageDependency="inputdate-FoiExemptionAsserted-month"
    ),
    MockInputOption(
      name="inputnumeric-ClosurePeriod-years",
      label="years",
      id="years",
      value="0",
      placeholder="0",
      fieldType="inputNumeric",
      errorMessage=s"There was no number entered for the years."
    ),
    MockInputOption(
      name="inputdropdown-FoiExemptionCode",
      id="inputdropdown-FoiExemptionCode",
      label="mock code1",
      value="mock code1",
      fieldType="inputDropdown",
      errorMessage="There was no value selected for the FOI Exemption Code."
    ),
    MockInputOption(
      name="inputdropdown-FoiExemptionCode",
      id="inputdropdown-FoiExemptionCode",
      label="mock code2",
      value="mock code2",
      fieldType="inputDropdown",
      errorMessage="There was no value selected for the FOI Exemption Code.",
    ),
    MockInputOption(
      name="inputradio-TitlePublic",
      label="Yes",
      id="inputradio-TitlePublic-Yes",
      value="yes",
      fieldType="inputRadio",
      errorMessage=s"There was no value selected for Is Title Closed."
    ),
    MockInputOption(
      name="inputradio-TitlePublic",
      label="No",
      id="inputradio-TitlePublic-No",
      value="no",
      errorMessage=s"There was no value selected for Is Title Closed.",
      fieldType="inputRadio"
    )
  )

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "AddClosureMetadataController GET" should {
    "render the add closure metadata page, with the default form, if file has no closure metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val formTester = new FormTester(expectedDefaultOptions)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata=false)
      mockGraphqlResponse()

      val addClosureMetadataPage = addClosureMetadataController.addClosureMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", "0"),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitlePublic", "yes")
      )

      playStatus(addClosureMetadataPage) mustBe OK
      contentType(addClosureMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "render the add closure metadata page, with the form updated with the file's closure metadata, for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer)
      mockGraphqlResponse()

      val addClosureMetadataPage = addClosureMetadataController.addClosureMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

      val newInputTextValues = Map(
        "inputdate-FoiExemptionAsserted-day" -> "12",
        "inputdate-FoiExemptionAsserted-month" -> "1",
        "inputdate-FoiExemptionAsserted-year" -> "1995",
        "inputdate-ClosureStartDate-day" -> "1",
        "inputdate-ClosureStartDate-month" -> "12",
        "inputdate-ClosureStartDate-year" -> "1990",
        "inputnumeric-ClosurePeriod-years" -> "4"
      )
      val expectedOptions = expectedDefaultOptions.map {
        mockOption =>
          newInputTextValues.get(mockOption.name) match {
            case Some(newValue) => mockOption.copy(value = newValue)
            case None => mockOption
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
        ("inputradio-TitlePublic", "no")
      )

      playStatus(addClosureMetadataPage) mustBe OK
      contentType(addClosureMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateAddClosureMetadataController(getUnauthorisedSecurityComponents)
      val addClosureMetadataPage = controller.addClosureMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      redirectLocation(addClosureMetadataPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(addClosureMetadataPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val client = new GraphQLConfiguration(app.configuration).getClient[cm.Data, cm.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = instantiateAddClosureMetadataController()
      val addClosureMetadataPage = controller.addClosureMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)

      val failure = addClosureMetadataPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "rerender form with user's data if form is partially submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val newValues = Map(
        "inputdate-FoiExemptionAsserted-day" -> "5",
        "inputdate-FoiExemptionAsserted-month" -> "11",
        "inputdate-FoiExemptionAsserted-year" -> "2021",
        "inputdate-ClosureStartDate-day" -> "",
        "inputdate-ClosureStartDate-month" -> "",
        "inputdate-ClosureStartDate-year" -> "",
        "inputnumeric-ClosurePeriod-years" ->  ""
      )
      val expectedOptions = expectedDefaultOptions.map{
        mockOption =>
          newValues.get(mockOption.name) match {
            case Some(newValue) => mockOption.copy(value = newValue)
            case None => mockOption
          }
      }

      val formTester = new FormTester(expectedOptions)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
      setConsignmentReferenceResponse(wiremockServer)
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
        ("inputradio-TitlePublic", "yes")
      )

      val addClosureMetadataPage = addClosureMetadataController.addClosureMetadataSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
          .withFormUrlEncodedBody(formSubmission: _*)
          .withCSRFToken
        )
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, formSubmission.toMap, formStatus = "PartiallySubmitted")
    }

    "display the most immediate date error if more than one date input (per date field) has an mistake in it" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val newValues = Map(
        "inputdate-FoiExemptionAsserted-day" -> "",
        "inputdate-FoiExemptionAsserted-month" -> "",
        "inputdate-FoiExemptionAsserted-year" -> "",
        "inputdate-ClosureStartDate-day" -> "5",
        "inputdate-ClosureStartDate-month" -> "",
        "inputdate-ClosureStartDate-year" -> ""
      )
      val expectedOptions = expectedDefaultOptions.map{
        mockOption =>
          newValues.get(mockOption.name) match {
            case Some(newValue) => mockOption.copy(value = newValue)
            case None => mockOption.copy(errorMessage = "")
          }
      }

      val formTester = new FormTester(expectedOptions)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentFilesMetadataResponse(wiremockServer)

      val formSubmission = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", "5"),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", "0"),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitlePublic", "yes")
      )

      val addClosureMetadataPage = addClosureMetadataController.addClosureMetadataSubmit(consignmentId)
        .apply(FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
          .withFormUrlEncodedBody(formSubmission: _*)
          .withCSRFToken
        )
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, formSubmission.toMap, formStatus = "PartiallySubmitted")
    }

    // need to add check for complete form submission
  }

  s"The consignment add closure metadata page" should {
    s"return 403 if the GET is accessed by a non-standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()

      val addClosureMetadata = {
        setConsignmentTypeResponse(wiremockServer, consignmentType = "judgment")
        addClosureMetadataController.addClosureMetadata(consignmentId)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/add-closure-metadata").withCSRFToken)
      }
      playStatus(addClosureMetadata) mustBe FORBIDDEN
    }
  }

  private def instantiateAddClosureMetadataController(securityComponents: SecurityComponents = getAuthorisedSecurityComponents) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val customMetadataService = new CustomMetadataService(graphQLConfiguration)

    new AddClosureMetadataController(
      securityComponents,
      new GraphQLConfiguration(app.configuration),
      getValidStandardUserKeycloakConfiguration,
      consignmentService,
      customMetadataService,
      MockAsyncCacheApi()
    )
  }

  private def mockGraphqlResponse() = {
    val client: GraphQLClient[cm.Data, cm.Variables] = new GraphQLConfiguration(app.configuration).getClient[cm.Data, cm.Variables]()
    val customMetadataResponse: cm.Data = getDataObject
    val data: client.GraphqlData = client.GraphqlData(Some(customMetadataResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("customMetadata"))
      .willReturn(okJson(dataString)))
  }
  //scalastyle:off method.length
  private def checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString: String) = {
    addClosureMetadataPageAsString must include(
      """      <title>Add closure metadata to &#x27;[selected file/folder name to go here]&#x27;</title>"""
    )
    addClosureMetadataPageAsString must include(
      """            <h1 class="govuk-heading-l">Add closure metadata to '[selected file/folder name to go here]'</h1>"""
    )
    addClosureMetadataPageAsString must include("""            <p class="govuk-body">Enter metadata for closure fields here.</p>""")
    addClosureMetadataPageAsString must include(
      """            <h2 class="govuk-label govuk-label--m">
        |                FOI decision asserted
        |            </h2>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <div id="date-input-FoiExemptionAsserted-hint" class="govuk-hint">
        |            Date of the Advisory Council Approval
        |        </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """            <h2 class="govuk-label govuk-label--m">
        |                Closure start date
        |            </h2>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <div id="date-input-ClosureStartDate-hint" class="govuk-hint">
        |            Date of the record from when the closure starts. It is usually the last date modified.
        |        </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <label class="govuk-label govuk-label--m" for=years>
        |            Closure period
        |        </label>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """    <div id="date-input-with-suffix-hint" class="govuk-hint">
        |        Number of years the record is closed from the closure start date
        |    </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """            <label class="govuk-label govuk-label--m" for="inputdropdown-FoiExemptionCode">
        |                FOI exemption code
        |            </label>""".replace("................", "                ").stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <div id="inputdropdown-FoiExemptionCode-hint" class="govuk-hint">
        |            Select the exemption code that applies
        |        </div>
        |....
        |
        |....
        |
        |    <select class="govuk-select" id="inputdropdown-FoiExemptionCode" name="inputdropdown-FoiExemptionCode"  >"""
        .replace("....", "    ").stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
        |            Is the title closed?
        |        </legend>""".stripMargin
    )
  }
  //scalastyle:on method.length
  private def getDataObject = {
    // Until the 'sortMetadataIntoCorrectPageOrder', getDefaultValue and getFieldHints methods in the MetadataUtils are
    // no longer needed, the real names have to be returned
    cm.Data(
      List(
        cm.CustomMetadata("ClosureType", None, Some("Closure Type"), Defined, Some("MandatoryClosure"), Text, true, false, Some("open_on_transfer"), 1,
          List(
            Values("closed_for",
              List(
                Dependencies("FoiExemptionAsserted"),
                Dependencies("ClosurePeriod"),
                Dependencies("ClosureStartDate"),
                Dependencies("FoiExemptionCode"),
                Dependencies("TitlePublic"),
                Dependencies("DescriptionPublic"))),
            Values("open_on_transfer",
              List(
                Dependencies("TitlePublic"),
                Dependencies("DescriptionPublic"))))),
        cm.CustomMetadata(
          "ClosurePeriod", None, Some("Closure Period"), Supplied, Some("MandatoryClosure"), Integer, true, false, Some("0"), 2, List(Values("0", List()))),
        cm.CustomMetadata(
          "DescriptionPublic", None, Some("Description Public"), Supplied, Some("MandatoryClosure"), Boolean, true, false, Some("True"), 3,
          List(
            Values("True", List()),
            Values("False",
              List(
                Dependencies("DescriptionAlternate"))))),
        cm.CustomMetadata(
          "TitlePublic", None, Some("Title Public"), Supplied, Some("MandatoryClosure"), Boolean, true, false, Some("True"), 4,
          List(
            Values("False",
              List(
                Dependencies("TitleAlternate"))),
            Values("True", List()))),
        cm.CustomMetadata(
          "ClosureStartDate", None, Some("Closure Start Date"), Supplied, Some("OptionalClosure"), DateTime, true, false, None, 5, List()),
        cm.CustomMetadata(
          "DescriptionAlternate", None, Some("Description Alternate"), Supplied, Some("OptionalClosure"), Text, true, false, None, 6, List()),
        cm.CustomMetadata(
          "TitleAlternate", None, Some("Title Alternate"), Supplied, Some("OptionalClosure"), Text, true, false, None, 7, List()),
        cm.CustomMetadata(
          "FoiExemptionAsserted", None, Some("Foi Exemption Asserted"), Supplied, Some("MandatoryClosure"), DateTime, true, false, None, 8, List()),
        cm.CustomMetadata(
          "FoiExemptionCode", None, Some("Foi Exemption Code"), Defined, Some("MandatoryClosure"), Text, true, true, Some("mock code1"), 9,
          List(
            Values("mock code1", List()), Values("mock code2", List())))))
  }
}

case class MockAsyncCacheApi()(implicit val ec: ExecutionContext) extends AsyncCacheApi {
  override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future(Done)
  override def remove(key: String): Future[Done] = Future(Done)
  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit evidence$1: ClassTag[A]): Future[A] =  orElse
  override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future(None)
  override def removeAll(): Future[Done] = Future(Done)
}
