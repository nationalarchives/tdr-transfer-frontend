package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import play.api.Play.materializer
import play.api.http.Status.{FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.format.DateTimeFormatter
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
    "render the view history page with list of user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val consignments = setConsignmentsHistoryResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewHistoryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-history"))
      val viewHistoryPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewHistoryPageAsString, userType = "standard", consignmentExists = false)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      viewHistoryPageAsString.contains("<h1 class=\"govuk-heading-l\">Transfer history</h1>") mustBe true
      viewHistoryPageAsString.contains(s"""<th scope="col" class="govuk-table__header">Consignment reference</th>
           |                  <th scope="col" class="govuk-table__header">Status</th>
           |                  <th scope="col" class="govuk-table__header">Date of export</th>
           |                  <th scope="col" class="govuk-table__header">Actions</th>""".stripMargin) mustBe true
      viewHistoryPageAsString.contains(s"""View the history of all the consignments you have uploaded and resume incomplete or failed transfers.""") mustBe true

      consignments.foreach(c => verifyConsignmentRow(viewHistoryPageAsString, c.node))
    }

    "render the view history page with no consignments if the user doesn't have any consignments" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentsHistoryResponse(wiremockServer, noConsignment = true)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewHistoryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-history"))
      val viewHistoryPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewHistoryPageAsString, userType = "standard", consignmentExists = false)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      viewHistoryPageAsString.contains("<h1 class=\"govuk-heading-l\">Transfer history</h1>") mustBe true
      viewHistoryPageAsString.contains(s"""<th scope="col" class="govuk-table__header">Consignment reference</th>
           |                  <th scope="col" class="govuk-table__header">Status</th>
           |                  <th scope="col" class="govuk-table__header">Date of export</th>
           |                  <th scope="col" class="govuk-table__header">Actions</th>""".stripMargin) mustBe true
      viewHistoryPageAsString.contains(s"""View the history of all the consignments you have uploaded and resume incomplete or failed transfers.""") mustBe true
      viewHistoryPageAsString.contains(
        """              <tbody class="govuk-table__body">""" +
          "\n                " +
          "\n              </tbody>"
      ) mustBe true
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewHistoryController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-history"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  def verifyConsignmentRow(viewHistoryPageAsString: String, node: Node): Unit = {

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val exportDate = node.exportDatetime.map(_.format(formatter)).get
    val createdDate = node.createdDatetime.map(_.format(formatter)).get
    val status = if (node.currentStatus.`export`.contains("Completed")) "Exported" else "InProgress"
    val summary =
      s"""
         |                          <span class="govuk-details__summary-text">
         |                          ${node.consignmentReference}
         |                          </span>
         |""".stripMargin
    val details =
      s"""
         |                        <div class="govuk-details__text">
         |                          <p class="govuk-body">Please do not delete the original files you exported until you are notified that your records have been preserved.</p>
         |                          <ul class="govuk-list govuk-list--bullet">
         |                            <li>Consignment uploaded by: test@example.com</li>
         |                            <li>Date started: $createdDate</li>
         |                            <li>Date of export: $exportDate</li>
         |                            <li>Number of files: ${node.totalFiles} records</li>
         |                          </ul>
         |                        </div>
         |""".stripMargin
    val statusAndDate =
      s"""
         |                    <td class="govuk-table__cell">
         |                      <strong class="govuk-tag govuk-tag--green">
         |                      $status
         |                      </strong>
         |                    </td>
         |                    <td class="govuk-table__cell">$exportDate</td>
         |                    <td class="govuk-table__cell"></td>
         |""".stripMargin
    viewHistoryPageAsString.contains(summary) mustBe true
    viewHistoryPageAsString.contains(details) mustBe true
    viewHistoryPageAsString.contains(statusAndDate) mustBe true
  }
}
