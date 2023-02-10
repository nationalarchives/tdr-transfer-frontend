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
import scala.collection.immutable.ListMap
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

  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

  implicit val ec: ExecutionContext = ExecutionContext.global

  "ViewTransfersController" should {
    "render the view transfers page with list of user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val consignmentsWithAllStatusStates: List[Consignments.Edges] = setConsignmentsHistoryResponse(wiremockServer)

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

      consignmentsWithAllStatusStates.zipWithIndex.foreach { case (consignmentEdge, index) =>
        verifyConsignmentRow(viewTransfersPageAsString, "standard", consignmentEdge.node, index)
      }
    }

    "render the view transfers page with list of user's consignments and have 'Contact us' as an Action for consignments" +
      " where the status value were invalid/not recognised" in {
        setConsignmentTypeResponse(wiremockServer, "standard")
        val invalidConsignmentStatusTypesOrValues: Map[String, List[Option[String]]] =
          ListMap(
            "series" -> List(Some("InvalidStatusValue")),
            "statusType1" -> List(None),
            "statusType2" -> List(None),
            "statusType3" -> List(None),
            "statusType4" -> List(None),
            "statusType5" -> List(None),
            "statusType6" -> List(None),
            "statusType7" -> List(None),
            "statusType8" -> List(None)
          )

        val consignmentsWithAllStatusStates: List[Consignments.Edges] = setConsignmentsHistoryResponse(wiremockServer, customStatusValues = invalidConsignmentStatusTypesOrValues)

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

        val consignmentEdgeForSeries = consignmentsWithAllStatusStates.head
        val contactUsStatusAndActionUrls: List[(String, String)] =
          List(("Contact us", """<a href="mailto:nationalArchives.email?subject=Ref: TEST-TDR-2022-GB1 - Consignment Failure (Status value is not valid)">Contact us</a>"""))
        verifyConsignmentRow(viewTransfersPageAsString, "standard", consignmentEdgeForSeries.node, 0, contactUsStatusAndActionUrls)
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

    "render the view transfers page with list of judgment user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val consignmentsWithAllStatusStates: List[Consignments.Edges] = setConsignmentsHistoryResponse(wiremockServer, "judgment")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = "judgment", consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      consignmentsWithAllStatusStates.zipWithIndex.foreach { case (consignmentEdge, index) =>
        verifyConsignmentRow(viewTransfersPageAsString, "judgment", consignmentEdge.node, index, judgmentStatusesAndActionUrls)
      }
    }

    "render the view transfers page with no consignments if the judgment user doesn't have any consignments" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentsHistoryResponse(wiremockServer, "judgment", noConsignment = true)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = "judgment", consignmentExists = false)
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

  def verifyConsignmentRow(
      viewTransfersPageAsString: String,
      consignmentType: String,
      node: Node,
      index: Int,
      statusesAndActionUrls: List[(String, String)] = standardStatusesAndActionUrls
  ): Unit = {
    val dateOfTransfer = node.exportDatetime.map(_.format(formatter)).get
    val createdDate = node.createdDatetime.map(_.format(formatter)).get
    val (transferStatus, actionUrlWithNoConsignmentId): (String, String) = statusesAndActionUrls(index)
    val transferStatusColour = getStatusColour(transferStatus)
    val actionUrl = actionUrlWithNoConsignmentId.format(node.consignmentid.getOrElse("NO CONSIGNMENT RETURNED"))
    val summary =
      s"""
         |                          <strong class="consignment-ref-cell">
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
         |                            <li>Date of transfer: $dateOfTransfer</li>
         |                            <li>Number of files: ${node.totalFiles} records</li>
         |                          </ul>
         |                        </div>
         |""".stripMargin
    val statusAndDate =
      s"""
         |                    <td class="govuk-table__cell">$createdDate</td>
         |                    <td class="govuk-table__cell ${if (dateOfTransfer == "N/A") "not-applicable" else ""}">$dateOfTransfer</td>
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
    "Transferred" -> "green",
    "Contact us" -> "red"
  )

  private val standardStatusesAndActionUrls: List[(String, String)] = List(
    ("In Progress", """<a href="/consignment/%s/series">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/transfer-agreement">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/transfer-agreement-continued">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/upload">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/upload">Resume transfer</a>"""),
    ("Failed", """<a href="/consignment/%s/upload">View errors</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-checks">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-checks-results">Resume transfer</a>"""),
    ("Failed", """<a href="/consignment/%s/file-checks-results">View errors</a>"""),
    ("Failed", """<a href="/consignment/%s/file-checks-results">View errors</a>"""),
    ("In Progress", """<a href="/consignment/%s/file-checks-results">Resume transfer</a>"""),
    ("In Progress", """<a href="/consignment/%s/additional-metadata/download-metadata/csv">Download report</a>"""),
    ("Transferred", """<a href="/consignment/%s/additional-metadata/download-metadata/csv">Download report</a>"""),
    ("Failed", """<a href="mailto:nationalArchives.email?subject=Ref: TEST-TDR-2022-GB6 - Export failure">Contact us</a>""")
  )

  private val judgmentStatusesAndActionUrls: List[(String, String)] = List(
    ("In Progress", """<a href="/judgment/%s/before-uploading">Resume transfer</a>"""),
    ("In Progress", """<a href="/judgment/%s/upload">Resume transfer</a>"""),
    ("Failed", """<a href="/judgment/%s/upload">View errors</a>"""),
    ("In Progress", """<a href="/judgment/%s/file-checks">Resume transfer</a>"""),
    ("In Progress", """<a href="/judgment/%s/file-checks-results">Resume transfer</a>"""),
    ("Failed", """<a href="/judgment/%s/file-checks-results">View errors</a>"""),
    ("Failed", """<a href="/judgment/%s/file-checks-results">View errors</a>"""),
    ("In Progress", """<a href="/judgment/%s/transfer-complete">View</a>"""),
    ("Transferred", """<a href="/judgment/%s/transfer-complete">View</a>"""),
    ("Failed", """<a href="mailto:nationalArchives.judgmentsEmail?subject=Ref: TEST-TDR-2022-GB6 - Export failure">Contact us</a>""")
  )
}
