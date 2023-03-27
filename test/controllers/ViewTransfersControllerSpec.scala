package controllers

import org.scalatest.matchers.should.Matchers._
import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.ConsignmentStatuses
import play.api.Play.materializer
import play.api.http.Status.{FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import services.Statuses.SeriesType
import testUtils.{CheckPageForStaticElements, ConsignmentStatusesOptions, FrontEndTestHelper}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
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
  private val standardType = "standard"
  private val judgmentType = "judgment"

  implicit val ec: ExecutionContext = ExecutionContext.global

  forAll(ConsignmentStatusesOptions.expectedStandardStatesAndStatuses) { (expectedTransferState, statuses, actionUrl, transferState, actionText) =>
    {
      s"ViewTransfersController for '$standardType' consignments" should {
        s"render the '$expectedTransferState' action for the given 'consignment status'" in {
          setConsignmentTypeResponse(wiremockServer, "standard")
          val consignment: List[Consignments.Edges] = setConsignmentViewTransfersResponse(wiremockServer, standardType, statuses = statuses)
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

  forAll(ConsignmentStatusesOptions.expectedJudgmentStatesAndStatuses) { (expectedTransferState, statuses, actionUrl, transferState, actionText) =>
    {
      s"ViewTransfersController for '$judgmentType' consignments" should {
        s"render the '$expectedTransferState' action for the given 'consignment status'" in {
          setConsignmentTypeResponse(wiremockServer, judgmentType)
          val consignment: List[Consignments.Edges] = setConsignmentViewTransfersResponse(wiremockServer, judgmentType, statuses = statuses)

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
      val consignments = setConsignmentViewTransfersResponse(wiremockServer, standardType, statuses = List())

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
        val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
        val invalidConsignmentStatus = ConsignmentStatuses(UUID.randomUUID, UUID.randomUUID, SeriesType.id, "InvalidStatusValue", someDateTime, None)

        setConsignmentTypeResponse(wiremockServer, standardType)
        val consignmentsWithAllStatusStates: List[Consignments.Edges] =
          setConsignmentViewTransfersResponse(wiremockServer, standardType, statuses = List(invalidConsignmentStatus))
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
        val expectedActionPage = """mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Issue With Transfer"""
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

      viewTransfersPageAsString must not include "<tbody"
    }

    "render the view transfers page with a list of all a judgment user's consignments" in {
      setConsignmentTypeResponse(wiremockServer, judgmentType)
      val consignments = setConsignmentViewTransfersResponse(wiremockServer, judgmentType, statuses = List())
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

      viewTransfersPageAsString must not include "<tbody"
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

  "ActionText" should {
    "have the correct value" in {
      ContactUs.value should equal("Contact us")
      Download.value should equal("Download report")
      Errors.value should equal("View errors")
      Resume.value should equal("Resume transfer")
      View.value should equal("View")
    }
  }

  "TransferStatus" should {
    "have the correct value" in {
      ContactUs.value should equal("Contact us")
      Failed.value should equal("Failed")
      InProgress.value should equal("In Progress")
      Transferred.value should equal("Transferred")
    }
  }

  def checkForExpectedViewTransfersPageContent(viewTransfersPageAsString: String): Unit = {
    viewTransfersPageAsString must include("<h1 class=\"govuk-heading-l\">View Transfers</h1>")
    viewTransfersPageAsString must include(
      s"""            <th scope="col" class="govuk-table__header">Reference</th>
         |            <th scope="col" class="govuk-table__header">Date started</th>
         |            <th scope="col" class="govuk-table__header">Date transferred</th>
         |            <th scope="col" class="govuk-table__header">Status</th>
         |            <th scope="col" class="govuk-table__header">Actions</th>""".stripMargin
    )
    viewTransfersPageAsString must include(
      s"""View the history of all the consignments you have uploaded. You can also resume incomplete transfers or view the errors of failed transfers."""
    )
    viewTransfersPageAsString must include(
      """      <a href="/homepage" role="button" draggable="false" class="govuk-button govuk-button--primary">
        |        Back to homepage
        |      </a>""".stripMargin
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
      s"""<a href="$actionPage" class="govuk-link govuk-link--no-visited-state">$action</a>"""
    } else {
      s"""<a href="/$domainPrefix/$consignmentId$actionPage" class="govuk-link govuk-link--no-visited-state">$action</a>"""
    }

    val expectedTableData =
      s"""              <th scope="row" class="govuk-table__header">${node.consignmentReference}</th>
         |              <td class="govuk-table__cell">$createdDate</td>
         |              <td class="govuk-table__cell ${if (dateOfTransfer == "N/A") "not-applicable" else ""}">$dateOfTransfer</td>
         |              <td class="govuk-table__cell">
         |                <strong class="tdr-tag tdr-tag--$transferStatusColour">$transferStatus</strong>
         |              </td>
         |              <td class="govuk-table__cell">
         |                <div class="tdr-link-group">
         |                  $expectedActionUrl
         |                </div>
         |              </td>""".stripMargin

    val expectedDescriptionList =
      s"""                  <dl class="tdr-dlist tdr-transfers-extra__list">
         |                    <dt class="govuk-body-m float govuk-!-margin-bottom-0">Number of files</dt>
         |                    <dd class="govuk-!-font-size-36">${node.totalFiles}</dd>
         |                  </dl>
         |                  <dl class="tdr-dlist tdr-transfers-extra__list">
         |                    <dt class="govuk-body-m float govuk-!-margin-bottom-0">Uploaded by</dt>
         |                    <dd>
         |                      <a href="mailto:test@example.com">test@example.com</a>
         |                    </dd>
         |                  </dl>""".stripMargin

    val expectedWarningText =
      s"""    <strong class="govuk-warning-text__text">
         |        <span class="govuk-warning-text__assistive">Warning</span>
         |        You must not delete the original records of this transfer as they are not yet preserved. You will receive an email once preservation has taken place. If you do not receive an email, contact <a href="mailto:tdr@nationalarchives.gov.uk.">tdr@nationalarchives.gov.uk</a>.
         |    </strong>""".stripMargin

    viewTransferPageString must include(expectedTableData)
    viewTransferPageString must include(expectedDescriptionList)
    if (transferStatus == "Transferred") {
      viewTransferPageString must include(expectedWarningText)
    }
  }

  private val expectedStatusColour = Map(
    "In Progress" -> "yellow",
    "Failed" -> "red",
    "Transferred" -> "green",
    "Contact us" -> "red"
  )
}
