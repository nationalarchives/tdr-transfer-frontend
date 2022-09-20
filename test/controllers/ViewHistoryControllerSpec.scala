package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import play.api.Play.materializer
import play.api.http.Status.{FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import scala.concurrent.ExecutionContext

class ViewHistoryControllerSpec extends FrontEndTestHelper {
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

  "ViewHistoryController" should {
    "render the view history page" in {
      //mock the api response that returns the users consignments
      setConsignmentTypeResponse(wiremockServer, "standard")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewHistoryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.viewConsignments()
        .apply(FakeRequest(GET, s"/view-history"))
      val viewHistoryPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewHistoryPageAsString, userType = "standard", consignmentExists = false)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      viewHistoryPageAsString.contains(s"""<th scope="col" class="govuk-table__header">Consignment reference</th>
                                          |                  <th scope="col" class="govuk-table__header">Status</th>
                                          |                  <th scope="col" class="govuk-table__header">Date of export</th>
                                          |                  <th scope="col" class="govuk-table__header">Actions</th>""".stripMargin) mustBe true
      viewHistoryPageAsString.contains(s"""View the history of all the consignments you have uploaded and resume incomplete or failed transfers.""") mustBe true
      viewHistoryPageAsString.contains(s"""MOCK-123-TDR""") mustBe true
      viewHistoryPageAsString.contains(s"""<li>Consignment uploaded by: JohnDoe@departmentofcookies.gov.uk</li>
                                          |                          <li>Date started: 19/08/2022</li>
                                          |                          <li>Date of export: 19/08/2022</li>
                                          |                          <li>Number of files: 1200 records</li>""".stripMargin) mustBe true
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewHistoryController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller.viewConsignments()
        .apply(FakeRequest(GET, s"/view-history"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }
}
