package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import play.api.Play.materializer
import play.api.http.Status.{FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext

class ViewTransfersControllerSpec extends FrontEndTestHelper {
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

  "ViewTransfersController" should {
    "render the view transfers page with list of user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val consignmentsWithAllPossibleCurrentStatusStates: List[Consignments.Edges] = setConsignmentsHistoryResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = "standard", consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      consignmentsWithAllPossibleCurrentStatusStates.zipWithIndex.foreach { case (consignmentEdge, index) =>
        verifyConsignmentRow(viewTransfersPageAsString, "standard", consignmentEdge.node, index)
      }
    }

    "render the view transfers page with no consignments if the user doesn't have any consignments" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentsHistoryResponse(wiremockServer, noConsignment = true)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = "standard", consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      viewTransfersPageAsString must include(
        """              <tbody class="govuk-table__body">""" +
          "\n                " +
          "\n              </tbody>"
      )
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  def checkForExpectedViewTransfersPageContent(viewTransfersPageAsString: String): Unit = {
    viewTransfersPageAsString must include("<h1 class=\"govuk-heading-l\">View Transfers</h1>")
    viewTransfersPageAsString must include(
      s"""                    <th scope="col" class="govuk-table__header">Reference</th>
          |                    <th scope="col" class="govuk-table__header">Date started</th>
          |                    <th scope="col" class="govuk-table__header">Date transferred</th>
          |                    <th scope="col" class="govuk-table__header">Status</th>
          |                    <th scope="col" class="govuk-table__header">Actions</th>""".stripMargin
    )
    viewTransfersPageAsString must include(s"""View the history of all the consignments you have uploaded and resume incomplete or failed transfers.""")
    viewTransfersPageAsString must include(
      """        <a href="/homepage" role="button" draggable="false" class="govuk-button govuk-button--primary">
        |            Back to homepage
        |        </a>""".stripMargin
    )
  }

  def verifyConsignmentRow(viewTransfersPageAsString: String, consignmentType: String, node: Node, index: Int): Unit = {

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val exportDate = node.exportDatetime.map(_.format(formatter)).get
    val createdDate = node.createdDatetime.map(_.format(formatter)).get
    val (transferStatus, actionUrlWithNoConsignmentId): (String, String) =
      if (consignmentType == "judgment") ("", "") else allPossibleStatusesAndActionUrlsForStandardUsers(index) // need to implement one for judgments
    val transferStatusColour = getStatusColour(transferStatus)
    val actionUrl = actionUrlWithNoConsignmentId.format(node.consignmentid.getOrElse("NO CONSIGNMENT RETURNED"))
    val summary =
      s"""
         |                          <strong class="consignmentRefCell">
         |                            ${node.consignmentReference}
         |                          </strong>
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
         |                    <td class="govuk-table__cell">$createdDate</td>
         |                    <td class="govuk-table__cell ${if (exportDate == "N/A") "not-applicable" else ""}">$exportDate</td>
         |                    <td class="govuk-table__cell">
         |                      <strong class="govuk-tag govuk-tag--$transferStatusColour">
         |                        $transferStatus
         |                      </strong>
         |                    </td>
         |                    <td class="govuk-table__cell">$actionUrl</td>
         |""".stripMargin

    viewTransfersPageAsString must include(summary)
    viewTransfersPageAsString must include(details)
    viewTransfersPageAsString must include(statusAndDate)
  }

  private val getStatusColour = Map(
    "In Progress" -> "yellow",
    "Failed" -> "red",
    "Transferred" -> "green"
  )

  private val allPossibleStatusesAndActionUrlsForStandardUsers: List[(String, String)] = List(
    ("In Progress", """<a href="/consignment/%s/series">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/transfer-agreement">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/transfer-agreement-continued">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/upload">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/upload">Resume transfer</a>"""),
    ("Failed", """<a href="/consignment/%s/upload">View errors</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-check-progress">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-checks-results">Resume transfer</a>"""),
    ("Failed", """<a href="/consignment/%s/file-checks-results">View errors</a>"""),
    ("Failed", """<a href="/consignment/%s/file-checks-results">View errors</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-checks-results">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/additional-metadata/download-metadata/csv">Download report</a>"""),
    ("Transferred", """<a href="/consignment/%s/additional-metadata/download-metadata/csv">Download report</a>"""),
    ("Failed", """<a href="mailto:nationalArchives.email?subject=Ref: TEST-TDR-2022-GB6 - export failure">Contact us</a>""")
  )
}
