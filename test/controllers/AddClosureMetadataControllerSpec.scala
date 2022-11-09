package controllers

import akka.Done
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import errors.GraphQlException
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import graphql.codegen.types.UpdateBulkFileMetadataInput
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableFor3
import play.api.Play.materializer
import play.api.cache.AsyncCacheApi
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper}
import testUtils.DefaultMockFormOptions.{expectedClosureDefaultOptions, expectedClosureDependencyDefaultOptions, MockInputOption}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag

class AddClosureMetadataControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements
  val fileIds: List[UUID] = List(UUID.fromString("ae4b7cad-ee83-46bd-b952-80bc8263c6c2"))
  val parentFolderId = Option(UUID.randomUUID())

  private val fieldsThatHaveDependenciesToSelect: TableFor3[Seq[String], Map[String, String], Seq[String]] = Table(
    ("Fields with Dependencies", "Full name and name", "DependencyInputNames"),
    (Seq("inputradio-TitleClosed"), Map("Alternate Title" -> "TitleAlternate"), Seq("inputtext-TitleAlternate-TitleAlternate")),
    (Seq("inputradio-DescriptionClosed"), Map("Alternate Description" -> "DescriptionAlternate"), Seq("inputtext-DescriptionAlternate-DescriptionAlternate")),
    (
      Seq("inputradio-DescriptionClosed", "inputradio-TitleClosed"),
      Map("Alternate Description" -> "DescriptionAlternate", "Alternate Title" -> "TitleAlternate"),
      Seq("inputtext-DescriptionAlternate-DescriptionAlternate", "inputtext-TitleAlternate-TitleAlternate")
    )
  )

  private val fieldAndValueSelectedPriorToMainPage: List[String] = List("ClosureType-Closed")
  private val dependencyFormTester = new FormTester(expectedClosureDependencyDefaultOptions)
  private val expectedDependencyDefaultForm: Seq[Seq[(String, String)]] =
    dependencyFormTester.generateWaysToIncorrectlySubmitAForm("value", combineOptionNameWithValue = true)

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
      val formTester = new FormTester(expectedClosureDefaultOptions)

      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
      mockGraphqlResponse()

      val addClosureMetadataPage = addClosureMetadataController
        .addClosureMetadata(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)
      val expectedDefaultForm = Seq(
        ("inputdate-FoiExemptionAsserted-day", ""),
        ("inputdate-FoiExemptionAsserted-month", ""),
        ("inputdate-FoiExemptionAsserted-year", ""),
        ("inputdate-ClosureStartDate-day", ""),
        ("inputdate-ClosureStartDate-month", ""),
        ("inputdate-ClosureStartDate-year", ""),
        ("inputnumeric-ClosurePeriod-years", ""),
        ("inputdropdown-FoiExemptionCode", "mock code1"),
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "yes")
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

      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
      mockGraphqlResponse()

      val addClosureMetadataPage = addClosureMetadataController
        .addClosureMetadata(consignmentId, fileIds)
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
      val expectedOptions = expectedClosureDefaultOptions.map { mockOption =>
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
        ("inputradio-TitleClosed", "yes"),
        ("inputradio-DescriptionClosed", "yes")
      )

      playStatus(addClosureMetadataPage) mustBe OK
      contentType(addClosureMetadataPage) mustBe Some("text/html")
      addClosureMetadataPageAsString must include(
        "<h3>original/file/path</h3>"
      )
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDefaultForm.toMap)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val controller = instantiateAddClosureMetadataController(getUnauthorisedSecurityComponents)
      val addClosureMetadataPage = controller
        .addClosureMetadata(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)
      redirectLocation(addClosureMetadataPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(addClosureMetadataPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val client = new GraphQLConfiguration(app.configuration).getClient[cm.Data, cm.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateAddClosureMetadataController()
      val addClosureMetadataPage = controller
        .addClosureMetadata(consignmentId, fileIds)
        .apply(FakeRequest(GET, s"/standard/$consignmentId/add-closure-metadata").withCSRFToken)

      val failure = addClosureMetadataPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "rerender form with errors for each field if empty form is submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
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
        ("inputradio-TitleClosed", ""),
        ("inputradio-DescriptionClosed", "")
      )

      val addClosureMetadataPage = addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
            .withFormUrlEncodedBody(formSubmission: _*)
            .withCSRFToken
        )
      val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
      checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString)
      formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, formSubmission.toMap, formStatus = "PartiallySubmitted")
    }

    "rerender form with user's data if form is partially submitted" in {
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val formTester = new FormTester(expectedClosureDefaultOptions)
      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
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
        ("inputradio-DescriptionClosed", "no")
      )

      val addClosureMetadataPage = addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
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
      val formTester = new FormTester(expectedClosureDefaultOptions)

      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
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
        ("inputradio-DescriptionClosed", "no")
      )

      val addClosureMetadataPage = addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
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
        addClosureMetadataController
          .addClosureMetadata(consignmentId, fileIds)
          .apply(FakeRequest(GET, s"/consignment/$consignmentId/add-closure-metadata").withCSRFToken)
      }
      playStatus(addClosureMetadata) mustBe FORBIDDEN
    }
  }

  "AddClosureMetadataController POST" should {
    "send the form data to the API" in {
      val consignmentId = UUID.randomUUID()
      val addClosureMetadataController = instantiateAddClosureMetadataController()
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
      mockGraphqlResponse()
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)

      addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
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
      input.metadataProperties.find(_.filePropertyName == "FoiExemptionAsserted").get.value mustBe "1970-01-01 00:00:00"
      input.metadataProperties.find(_.filePropertyName == "ClosureStartDate").get.value mustBe "1970-01-01 00:00:00"
      input.metadataProperties.find(_.filePropertyName == "TitleClosed").get.value mustBe "false"
      input.metadataProperties.find(_.filePropertyName == "DescriptionClosed").get.value mustBe "false"
      input.metadataProperties.find(_.filePropertyName == "ClosurePeriod").get.value mustBe "10"
    }

    forAll(fieldsThatHaveDependenciesToSelect) { (fieldsThatHaveDependencies, _, _) =>
      val propertyNamesThatHaveDeps: Seq[String] = fieldsThatHaveDependencies.map(_.split("-")(1))
      s"redirect user to dependency page which has ${propertyNamesThatHaveDeps.mkString(" and ")}, " +
        "with a value of 'True', in the url but no other form fields" in {
          val consignmentId = UUID.randomUUID()
          val addClosureMetadataController = instantiateAddClosureMetadataController()

          setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
          setConsignmentTypeResponse(wiremockServer, "standard")
          mockGraphqlResponse()
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
            ("inputradio-DescriptionClosed", "no")
          )

          val formToSubmit: Seq[(String, String)] = formToSubmitDefault.map { case (field, value) =>
            if (fieldsThatHaveDependencies.contains(field)) (field, "yes") else (field, value)
          }

          val propertyNamesWhereUserDoesNotHaveToFillInDeps: Seq[String] = formToSubmitDefault.collect {
            case (field, _) if !fieldsThatHaveDependencies.contains(field) => field.split("-")(1)
          }

          val addClosureMetadataPage: Result =
            addClosureMetadataController
              .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
              .apply(
                FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
                  .withFormUrlEncodedBody(formToSubmit: _*)
                  .withCSRFToken
              )
              .futureValue

          val redirectLocation = addClosureMetadataPage.header.headers.getOrElse("Location", "")

          addClosureMetadataPage.header.status should equal(303)
          redirectLocation.contains(s"/consignment/$consignmentId/add-closure-metadata/nestedDependencies") should equal(true)
          propertyNamesThatHaveDeps.foreach { propertyNameWhereUserDoesHaveToFillInDeps =>
            redirectLocation.contains(s"nestedDependencies=$propertyNameWhereUserDoesHaveToFillInDeps-True") should equal(true)
          }
          propertyNamesWhereUserDoesNotHaveToFillInDeps.foreach { propertyNameWhereUserDoesNotHaveToFillInDeps =>
            redirectLocation.contains(s"$propertyNameWhereUserDoesNotHaveToFillInDeps-True") should equal(false)
          }
        }
    }

    s"redirect user to additional summary page if there are no fields that need their dependencies filled in" in {
      val consignmentId = UUID.randomUUID()
      val addClosureMetadataController = instantiateAddClosureMetadataController()

      setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
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
        ("inputradio-DescriptionClosed", "no")
      )

      val addClosureMetadataPage: Result = addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, fieldAndValueSelectedPriorToMainPage, consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
            .withFormUrlEncodedBody(formToSubmitDefault: _*)
            .withCSRFToken
        )
        .futureValue

      val propertyNamesWhereUserDoesNotHaveToFillInDeps: Seq[String] = formToSubmitDefault.map { case (field, _) =>
        field.split("-")(1)
      }

      val redirectLocation = addClosureMetadataPage.header.headers.getOrElse("Location", "")

      addClosureMetadataPage.header.status should equal(303)
      redirectLocation.contains(s"/consignment/$consignmentId/additional-metadata/closure/selected-summary") should equal(true)

      propertyNamesWhereUserDoesNotHaveToFillInDeps.foreach { propertyNameWhereUserDoesNotHaveToFillInDeps =>
        redirectLocation.contains(s"$propertyNameWhereUserDoesNotHaveToFillInDeps-True") should equal(false)
      }
    }
  }

  "AddClosureMetadataController.addClosureMetadataDependenciesPage GET" should {
    forAll(fieldsThatHaveDependenciesToSelect) { (fieldsThatHaveDependencies, fullNameAndName, dependencyInputNames) =>
      val propertyNamesThatHaveDeps: Seq[String] = fieldsThatHaveDependencies.map(_.split("-")(1))
      s"render the add closure metadata dependency page, with the dependencies that belong to ${propertyNamesThatHaveDeps.mkString(" and ")}," +
        " for an authenticated standard user, if file has no closure metadata" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val addClosureMetadataController = instantiateAddClosureMetadataController()
          val formTester = new FormTester(
            expectedClosureDependencyDefaultOptions.filter(mockInputOption => dependencyInputNames.contains(mockInputOption.name))
          )

          setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false)
          mockGraphqlResponse()

          val addClosureMetadataPage =
            addClosureMetadataController
              .addClosureMetadataDependenciesPage(propertyNamesThatHaveDeps.map(propertyNameThatHasDeps => s"$propertyNameThatHasDeps-True").toList, consignmentId, fileIds)
              .apply(
                FakeRequest(
                  GET,
                  s"/standard/$consignmentId/add-closure-metadata?nestedDependencies=${propertyNamesThatHaveDeps.mkString("&nestedDependencies=")}"
                ).withCSRFToken
              )
          val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)
          val expectedDependencyDefaultForm: Seq[(String, String)] = dependencyInputNames.map(field => (field, ""))

          playStatus(addClosureMetadataPage) mustBe OK
          contentType(addClosureMetadataPage) mustBe Some("text/html")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
          checkForExpectedClosureMetadataDependenciesFormPageContent(addClosureMetadataPageAsString, fullNameAndName)
          formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDependencyDefaultForm.toMap)
        }

      s"render the add closure metadata dependency page, with the dependencies that belong to ${propertyNamesThatHaveDeps.mkString(" and ")}," +
        " for an authenticated standard user, with the file's closure metadata" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val addClosureMetadataController = instantiateAddClosureMetadataController()
          val formTester = new FormTester(expectedClosureDependencyDefaultOptions.filter(mockInputOption => dependencyInputNames.contains(mockInputOption.name)))

          setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentFilesMetadataResponse(wiremockServer, fileIds = fileIds)
          mockGraphqlResponse()

          val addClosureMetadataPage =
            addClosureMetadataController
              .addClosureMetadataDependenciesPage(propertyNamesThatHaveDeps.map(propertyNameThatHasDeps => s"$propertyNameThatHasDeps-True").toList, consignmentId, fileIds)
              .apply(
                FakeRequest(
                  GET,
                  s"/standard/$consignmentId/add-closure-metadata?nestedDependencies=${propertyNamesThatHaveDeps.mkString("&nestedDependencies=")}"
                ).withCSRFToken
              )
          val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)
          val expectedDependencyDefaultForm: Seq[(String, String)] = dependencyInputNames.map(field => (field, s"$field value"))

          playStatus(addClosureMetadataPage) mustBe OK
          contentType(addClosureMetadataPage) mustBe Some("text/html")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
          checkForExpectedClosureMetadataDependenciesFormPageContent(addClosureMetadataPageAsString, fullNameAndName)
          formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDependencyDefaultForm.toMap)
        }
    }
  }

  "AddClosureMetadataController nestedDependencies page POST" should {
    forAll(fieldsThatHaveDependenciesToSelect) { (fieldsThatHaveDependencies, fullNameAndName, dependencyInputNames) =>
      val propertyNamesThatHaveDeps: Seq[String] = fieldsThatHaveDependencies.map(_.split("-")(1))
      val propertyNamesThatHaveDepsAndTheirValues = propertyNamesThatHaveDeps.map(propertyNames => s"$propertyNames-True").toList
      s"render the add closure metadata dependency page form with errors for the dependencies that belong to ${propertyNamesThatHaveDeps.mkString(" and ")}," +
        " for an authenticated standard user, if empty form is submitted" in {
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val addClosureMetadataController = instantiateAddClosureMetadataController()
          val formTester = new FormTester(expectedClosureDependencyDefaultOptions.filter(mockInputOption => dependencyInputNames.contains(mockInputOption.name)))

          setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false, fileIds = fileIds)
          mockGraphqlResponse()

          val expectedDependencyDefaultForm: Seq[(String, String)] = dependencyInputNames.map(field => (field, ""))

          val addClosureMetadataPage =
            addClosureMetadataController
              .addClosureMetadataSubmit(isMainForm = false, propertyNamesThatHaveDepsAndTheirValues, consignmentId, fileIds)
              .apply(
                FakeRequest(
                  POST,
                  s"/standard/$consignmentId/add-closure-metadata?nestedDependencies=${propertyNamesThatHaveDeps.mkString("&nestedDependencies=")}"
                ).withFormUrlEncodedBody(expectedDependencyDefaultForm: _*).withCSRFToken
              )
          val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

          playStatus(addClosureMetadataPage) mustBe OK
          contentType(addClosureMetadataPage) mustBe Some("text/html")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
          checkForExpectedClosureMetadataDependenciesFormPageContent(addClosureMetadataPageAsString, fullNameAndName)
          formTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, expectedDependencyDefaultForm.toMap, formStatus = "PartiallySubmitted")
        }
    }

    expectedDependencyDefaultForm.foreach { optionsToEnter =>
      val value: String = optionsToEnter.map(optionAndValue => optionAndValue._1).mkString(", ")
      s"render the add closure metadata dependency page form with user's data if form is partially submitted (only data entered for $value)" +
        " for an authenticated standard user" in {
          val fullNameAndName = fieldsThatHaveDependenciesToSelect(2)._2
          val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
          val addClosureMetadataController = instantiateAddClosureMetadataController()

          setConsignmentDetailsResponse(wiremockServer, None, "reference", parentFolderId)
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentFilesMetadataResponse(wiremockServer, fileHasMetadata = false, fileIds = fileIds)
          mockGraphqlResponse()

          val incompleteDependencyForm: Map[String, String] =
            Map("inputtext-DescriptionAlternate-DescriptionAlternate" -> "", "inputtext-TitleAlternate-TitleAlternate" -> "") ++ optionsToEnter

          val propertyNamesThatHaveDeps = List("TitleClosed-True", "DescriptionClosed-True")
          val addClosureMetadataPage =
            addClosureMetadataController
              .addClosureMetadataSubmit(isMainForm = false, List("TitleClosed-True", "DescriptionClosed-True"), consignmentId, fileIds)
              .apply(
                FakeRequest(
                  POST,
                  s"/standard/$consignmentId/add-closure-metadata?nestedDependencies=${propertyNamesThatHaveDeps.mkString("&nestedDependencies=")}"
                ).withFormUrlEncodedBody(incompleteDependencyForm.toSeq: _*).withCSRFToken
              )
          val addClosureMetadataPageAsString = contentAsString(addClosureMetadataPage)

          playStatus(addClosureMetadataPage) mustBe OK
          contentType(addClosureMetadataPage) mustBe Some("text/html")

          checkPageForStaticElements.checkContentOfPagesThatUseMainScala(addClosureMetadataPageAsString, userType = "standard")
          checkForExpectedClosureMetadataDependenciesFormPageContent(addClosureMetadataPageAsString, fullNameAndName)
          dependencyFormTester.checkHtmlForOptionAndItsAttributes(addClosureMetadataPageAsString, incompleteDependencyForm, formStatus = "PartiallySubmitted")
        }
    }

    "send the form data to the API" in {
      val consignmentId = UUID.randomUUID()
      val addClosureMetadataController = instantiateAddClosureMetadataController()
      val formSubmission = Seq(
        ("inputtext-DescriptionAlternate-DescriptionAlternate", "DescriptionAlternate value"),
        ("inputtext-TitleAlternate-TitleAlternate", "TitleAlternate value")
      )

      setConsignmentTypeResponse(wiremockServer, "standard")
      mockGraphqlResponse()
      setConsignmentFilesMetadataResponse(wiremockServer)
      setBulkUpdateMetadataResponse(wiremockServer)

      addClosureMetadataController
        .addClosureMetadataSubmit(isMainForm = true, List("TitleClosed-True", "DescriptionClosed-True"), consignmentId, fileIds)
        .apply(
          FakeRequest(POST, s"/standard/$consignmentId/add-closure-metadata")
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
      input.metadataProperties.find(_.filePropertyName == "DescriptionAlternate").get.value mustBe "DescriptionAlternate value"
      input.metadataProperties.find(_.filePropertyName == "TitleAlternate").get.value mustBe "TitleAlternate value"
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

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("customMetadata"))
        .willReturn(okJson(dataString))
    )
  }
  // scalastyle:off method.length
  private def checkForExpectedClosureMetadataFormPageContent(addClosureMetadataPageAsString: String) = {
    addClosureMetadataPageAsString must include(
      """      <title>Add closure metadata to files</title>"""
    )
    addClosureMetadataPageAsString must include(
      """      <h1 class="govuk-heading-l">Add closure metadata to</h1>"""
    )

    addClosureMetadataPageAsString must include("""      <p class="govuk-body">Enter metadata for closure fields here.</p>""")
    addClosureMetadataPageAsString must include(
      """            <h2 class="govuk-label govuk-label--m">
        |                FOI decision asserted
        |            </h2>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <div id="date-input-FoiExemptionAsserted-hint" class="govuk-hint">
        |            Date of the Advisory Council approval (or SIRO approval if appropriate)
        |        </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """            <h2 class="govuk-label govuk-label--m">
        |                Closure start date
        |            </h2>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <div id="date-input-ClosureStartDate-hint" class="govuk-hint">
        |            This has been defaulted to the last date modified. If this is not correct, amend the field below.
        |        </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <label class="govuk-label govuk-label--m" for=years>
        |            Closure period
        |        </label>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """    <div id="numeric-input-hint" class="govuk-hint">
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
        |        </div>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """<select class="govuk-select" id="inputdropdown-FoiExemptionCode" name="inputdropdown-FoiExemptionCode"  >""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
        |            Is the title closed?
        |        </legend>""".stripMargin
    )
    addClosureMetadataPageAsString must include(
      """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
        |            Is the description closed?
        |        </legend>""".stripMargin
    )
  }

  private def checkForExpectedClosureMetadataDependenciesFormPageContent(addClosureMetadataPageAsString: String, fullNameAndName: Map[String, String]): Unit = {
    val title = fullNameAndName.keys.mkString(" and ")
    val pronoun = if (fullNameAndName.size > 1) "they contain" else "it contains"

    addClosureMetadataPageAsString must include(s"""      <title>Add an $title to files</title>""")
    addClosureMetadataPageAsString must include(s"""      <h1 class="govuk-heading-l">Add an $title to</h1>""")

    addClosureMetadataPageAsString must include(s"""      <p class="govuk-body">Enter a publicly visible $title if, for example, $pronoun sensitive information.
                | For guidance on how to create an $title, read our FAQs (opens in a new tab)</p>""".stripMargin)

    fullNameAndName.foreach { case (fullName, name) =>
      addClosureMetadataPageAsString must include(
        s"""        <label class="govuk-label govuk-label--m" for=$name>
            |            $fullName
            |        </label>""".stripMargin
      )
    }
  }

  private def getDataObject = {
    // Until the 'sortMetadataIntoCorrectPageOrder', getDefaultValue and getFieldHints methods in the MetadataUtils are
    // no longer needed, the real names have to be returned
    cm.Data(
      List(
        cm.CustomMetadata(
          "ClosureType",
          None,
          Some("Closure Type"),
          Defined,
          Some("MandatoryClosure"),
          Text,
          editable = true,
          multiValue = false,
          Some("Open"),
          1,
          List(
            Values(
              "Closed",
              List(
                Dependencies("FoiExemptionAsserted"),
                Dependencies("ClosurePeriod"),
                Dependencies("ClosureStartDate"),
                Dependencies("FoiExemptionCode"),
                Dependencies("TitleClosed"),
                Dependencies("DescriptionClosed")
              ),
              1
            ),
            Values("Open", List(Dependencies("TitleClosed"), Dependencies("DescriptionClosed")), 1)
          ),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "ClosurePeriod",
          Some("Number of years the record is closed from the closure start date"),
          Some("Closure Period"),
          Supplied,
          Some("MandatoryClosure"),
          Integer,
          editable = true,
          multiValue = false,
          None,
          2,
          List(Values("0", List(), 1)),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "DescriptionClosed",
          None,
          Some("Is the description closed?"),
          Supplied,
          Some("MandatoryClosure"),
          Boolean,
          editable = true,
          multiValue = false,
          Some("True"),
          3,
          List(Values("False", List(), 1), Values("True", List(Dependencies("DescriptionAlternate")), 1)),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "TitleClosed",
          None,
          Some("Is the title closed?"),
          Supplied,
          Some("MandatoryClosure"),
          Boolean,
          editable = true,
          multiValue = false,
          Some("True"),
          4,
          List(Values("True", List(Dependencies("TitleAlternate")), 1), Values("False", List(), 1)),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "ClosureStartDate",
          Some("This has been defaulted to the last date modified. If this is not correct, amend the field below."),
          Some("Closure Start Date"),
          Supplied,
          Some("OptionalClosure"),
          DateTime,
          editable = true,
          multiValue = false,
          None,
          5,
          List(),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "DescriptionAlternate",
          None,
          Some("Description Alternate"),
          Supplied,
          Some("OptionalClosure"),
          Text,
          editable = true,
          multiValue = false,
          None,
          6,
          List(),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "TitleAlternate",
          None,
          Some("Title Alternate"),
          Supplied,
          Some("OptionalClosure"),
          Text,
          editable = true,
          multiValue = false,
          None,
          7,
          List(),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "FoiExemptionAsserted",
          Some("Date of the Advisory Council approval (or SIRO approval if appropriate)"),
          Some("Foi Exemption Asserted"),
          Supplied,
          Some("MandatoryClosure"),
          DateTime,
          editable = true,
          multiValue = false,
          None,
          8,
          List(),
          None,
          allowExport = false
        ),
        cm.CustomMetadata(
          "FoiExemptionCode",
          Some("Select the exemption code that applies"),
          Some("Foi Exemption Code"),
          Defined,
          Some("MandatoryClosure"),
          Text,
          editable = true,
          multiValue = true,
          Some("mock code1"),
          9,
          List(Values("mock code1", List(), 1), Values("mock code2", List(), 2)),
          None,
          allowExport = false
        )
      )
    )
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
