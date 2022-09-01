package controllers

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
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import scala.concurrent.ExecutionContext

class AddClosureMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "AddClosureMetadataController GET" should {
    "render the add closure metadata page for an authenticated standard user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer)
      mockGraphqlResponse()

      val addClosureMetadataPage = addClosureMetadataController.addClosureMetadata(consignmentId)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

      playStatus(addClosureMetadataPage) mustBe OK
      contentType(addClosureMetadataPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      addClosureMetadataPageAsString must include(
        """<h1 class="govuk-heading-l">Add closure metadata to '[selected file/folder name to go here]'</h1>"""
      )
      addClosureMetadataPageAsString must include(
        """<p class="govuk-body">Enter metadata for closure fields here.</p>"""
      )
      addClosureMetadataPageAsString must include(
        """<div class="govuk-input__wrapper">
          |        <input
          |        class="govuk-input govuk-input--width-5 "
          |        id="years"
          |        name="inputnumeric-ClosurePeriod-years"
          |        type="number"
          |        value="4"
          |        placeholder="0"
          |        inputmode="numeric"
          |        >""".stripMargin
      )
      List("No", "Yes").foreach { fieldValue =>
        addClosureMetadataPageAsString must include(
          s"""<input
                        class="govuk-radios__input"
                        id="inputradio-TitlePublic-${fieldValue}"
                        name="inputradio-TitlePublic"
                        type="radio"
                        value="${fieldValue.toLowerCase()}"
                """)
      }
      List("ClosureStartDate", "FoiExemptionAsserted").foreach(fieldId =>
        List(("12", "day", "dd"), ("1", "month", "mm"), ("1995", "year", "yyyy")).foreach { fieldValue =>
          addClosureMetadataPageAsString must include(
            s"""<input class="govuk-input
               |                                      govuk-date-input__input
               |                                      govuk-input--width-${if (fieldValue._3.length == 2) 2 else 3}
               |                        "
               |                    id="date-input-${fieldValue._2}"
               |                    name="inputdate-${fieldId}-${fieldValue._2}"
               |                    value="${fieldValue._1}"
               |                    type="number"
               |                    inputmode="numeric"
               |                    placeholder="${fieldValue._3}"
               |                    maxlength="${fieldValue._3.length}"
               |                    >""".stripMargin)
        })
      addClosureMetadataPageAsString must include(
        """<select class="govuk-select" id="inputdropdown-FoiExemptionCode" name="inputdropdown-FoiExemptionCode"  >"""
      )
      addClosureMetadataPageAsString must include("""<option selected="selected" value="open">open</option>""")
      addClosureMetadataPageAsString must include("""<option value="mock code2">mock code2</option>""")
      addClosureMetadataPageAsString must include(
        """<button data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button">
          |                            Continue
          |                        </button>""".stripMargin)
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
      mock[AsyncCacheApi]
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
            Values("open", List()), Values("mock code2", List())))))
  }
}
