package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import play.api.Play.materializer
import play.api.http.Status.{FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, ConsignmentStatusesOptions, FrontEndTestHelper}

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

  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
  private val defaultCurrentStatus = CurrentStatus(series = None, transferAgreement = None, upload = None, clientChecks = None, confirmTransfer = None, export = None)
  private val standardType = "standard"
  private val judgmentType = "judgment"

  implicit val ec: ExecutionContext = ExecutionContext.global

  forAll(ConsignmentStatusesOptions.expectedStandardStatesAndStatuses) { (expectedTransferState, currentStatus, actionUrl, transferState, actionText) =>
    {
      s"ViewTransfersController for '$standardType' consignments" should {
        s"render the '$expectedTransferState' action for the given 'consignment status'" in {
          setConsignmentTypeResponse(wiremockServer, "standard")
          val consignment: List[Consignments.Edges] = setConsignmentViewTransfersResponse(wiremockServer, standardType, customStatusValues = List(currentStatus))
          val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
          val consignmentService = new ConsignmentService(graphQLConfiguration)
          val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
          val response = controller
            .viewConsignments()
            .apply(FakeRequest(GET, s"/view-transfers"))
          val viewTransfersPageAsString = contentAsString(response)

          status(response) mustBe OK
          contentType(response) mustBe Some("text/html")
          consignment.map(c => {
            verifyConsignmentRow(viewTransfersPageAsString, c.node, actionUrl, transferState, actionText, standardType)
          })
        }
      }
    }
  }

  forAll(ConsignmentStatusesOptions.expectedJudgmentStatesAndStatuses) { (expectedTransferState, currentStatus, actionUrl, transferState, actionText) =>
    {
      s"ViewTransfersController for '$judgmentType' consignments" should {
        s"render the '$expectedTransferState' action for the given 'consignment status'" in {
          setConsignmentTypeResponse(wiremockServer, judgmentType)
          val consignment: List[Consignments.Edges] = setConsignmentViewTransfersResponse(wiremockServer, judgmentType, customStatusValues = List(currentStatus))

          val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
          val consignmentService = new ConsignmentService(graphQLConfiguration)
          val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
          val response = controller
            .viewConsignments()
            .apply(FakeRequest(GET, s"/view-transfers"))
          val viewTransfersPageAsString = contentAsString(response)

          status(response) mustBe OK
          contentType(response) mustBe Some("text/html")
          consignment.map(c => {
            verifyConsignmentRow(viewTransfersPageAsString, c.node, actionUrl, transferState, actionText, judgmentType)
          })
        }
      }
    }
  }

  "ViewTransfersController" should {
    "render the view transfers page with a list of all user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, standardType)
      val consignments = setConsignmentViewTransfersResponse(wiremockServer, standardType, List(defaultCurrentStatus, defaultCurrentStatus))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = standardType, consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      consignments.foreach(c => {
        verifyConsignmentRow(viewTransfersPageAsString, c.node, "/series", "In Progress", "Resume transfer", standardType)
      })
    }

    "render the view transfers page with list of user's consignments and have 'Contact us' as an Action for consignments" +
      " where the status value were invalid/not recognised" in {
        setConsignmentTypeResponse(wiremockServer, standardType)
        val invalidCurrentStatusValue = CurrentStatus(Some("InvalidStatusValue"), None, None, None, None, None)
        val consignmentsWithAllStatusStates: List[Consignments.Edges] =
          setConsignmentViewTransfersResponse(wiremockServer, standardType, customStatusValues = List(invalidCurrentStatusValue))
        val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
        val consignmentService = new ConsignmentService(graphQLConfiguration)
        val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
        val response = controller
          .viewConsignments()
          .apply(FakeRequest(GET, s"/view-transfers"))
        val viewTransfersPageAsString = contentAsString(response)

        status(response) mustBe OK
        contentType(response) mustBe Some("text/html")

        checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = standardType, consignmentExists = false)
        checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

        val consignmentEdgeForSeries = consignmentsWithAllStatusStates.head
        val expectedActionPage = """mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Consignment Failure (Status value is not valid)"""
        val expectedTransferStatus, expectedAction = "Contact us"
        verifyConsignmentRow(viewTransfersPageAsString, consignmentEdgeForSeries.node, expectedActionPage, expectedTransferStatus, expectedAction, standardType)
      }

    "render the view transfers page with no consignments if the user doesn't have any consignments" in {
      setConsignmentTypeResponse(wiremockServer, standardType)
      setConsignmentViewTransfersResponse(wiremockServer, standardType, noConsignment = true)
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = standardType, consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      viewTransfersPageAsString must include(
        """              <tbody class="govuk-table__body">""" +
          "\n                " +
          "\n              </tbody>"
      )
    }

    "render the view transfers page with a list of all a judgment user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, judgmentType)
      val consignments = setConsignmentViewTransfersResponse(wiremockServer, judgmentType, List(defaultCurrentStatus, defaultCurrentStatus))
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = judgmentType, consignmentExists = false)
      checkForExpectedViewTransfersPageContent(viewTransfersPageAsString)

      consignments.foreach(c => {
        verifyConsignmentRow(viewTransfersPageAsString, c.node, "/before-uploading", "In Progress", "Resume transfer", judgmentType)
      })
    }

    "render the view transfers page with no consignments if the judgment user doesn't have any consignments" in {
      setConsignmentTypeResponse(wiremockServer, judgmentType)
      setConsignmentViewTransfersResponse(wiremockServer, judgmentType, noConsignment = true)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new ViewTransfersController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .viewConsignments()
        .apply(FakeRequest(GET, s"/view-transfers"))
      val viewTransfersPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(viewTransfersPageAsString, userType = judgmentType, consignmentExists = false)
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
      viewTransferPageString: String,
      node: Node,
      actionPage: String,
      transferStatus: String,
      action: String,
      consignmentType: String
  ): Unit = {
    val dateOfTransfer = node.exportDatetime.map(_.format(formatter)).get
    val createdDate = node.createdDatetime.map(_.format(formatter)).get
    val consignmentId = node.consignmentid.getOrElse("no consignment id")
    val transferStatusColour = expectedStatusColour(transferStatus)
    val domainPrefix = if (consignmentType == "standard") { "consignment" }
    else consignmentType
    val expectedActionUrl = if (action == "Contact us") {
      s"""<a href="$actionPage">$action</a>"""
    } else {
      s"""<a href="/$domainPrefix/$consignmentId$actionPage">$action</a>"""
    }

    val expectedSummary =
      s"""
         |                          <strong class="consignment-ref-cell">
         |                            ${node.consignmentReference}
         |                          </strong>
         |""".stripMargin

    val expectedDetails =
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

    val expectedStatusAndDate =
      s"""
         |                    <td class="govuk-table__cell">$createdDate</td>
         |                    <td class="govuk-table__cell ${if (dateOfTransfer == "N/A") "not-applicable" else ""}">$dateOfTransfer</td>
         |                    <td class="govuk-table__cell">
         |                      <strong class="govuk-tag govuk-tag--$transferStatusColour">
         |                        $transferStatus
         |                      </strong>
         |                    </td>
         |                    <td class="govuk-table__cell">$expectedActionUrl</td>
         |""".stripMargin

    viewTransferPageString must include(expectedSummary)
    viewTransferPageString must include(expectedDetails)
    viewTransferPageString must include(expectedStatusAndDate)
  }

  private val expectedStatusColour = Map(
    "In Progress" -> "yellow",
    "Failed" -> "red",
    "Transferred" -> "green",
    "Contact us" -> "red"
  )
}
