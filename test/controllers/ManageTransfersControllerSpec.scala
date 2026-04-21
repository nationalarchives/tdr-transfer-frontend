package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.GraphQLConfiguration
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.ExecutionContext

class ManageTransfersControllerSpec extends FrontEndTestHelper {
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

  private val TNAUserType = "tna"

  "ManageTransfersController GET" should {

    "render the manage transfers page for a TNA user" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = TNAUserType, consignmentExists = false)
    }

    "render the page heading and tab navigation links" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("<h1 class=\"govuk-heading-l govuk-!-margin-bottom-5\">Manage transfers</h1>")
      pageAsString must include("""class="govuk-tabs__tab"""")
      pageAsString must include("Requested")
      pageAsString must include("Rejected")
      pageAsString must include("Approved")
      pageAsString must include("Transferred")
      pageAsString must include("All")
    }

    "mark the requested tab as selected by default" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("govuk-tabs__list-item--selected")
      pageAsString must include("""aria-current="page">Requested""")
    }

    "mark the rejected tab as selected when tab=rejected" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(Some("rejected")).apply(FakeRequest(GET, "/admin/manage-transfers?tab=rejected").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""aria-current="page">Rejected""")
      pageAsString must not include """aria-current="page">Requested"""
    }

    "render table column headers" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""<th scope="col" class="govuk-table__header">Consignment</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Status</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Department</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Series</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Last updated</th>""")
    }

    "render consignment data with correct status tags" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""<strong class="govuk-tag govuk-tag--red">Requested</strong>""")
      pageAsString must include regex s"""<a class="govuk-link" href="/admin/metadata-review/[0-9a-f-]+">TDR\u20112024\u2011TEST1</a>""".r
      // sanity: the reference must not contain ASCII hyphens in the rendered HTML
      pageAsString must not include """>TDR-2024-TEST1<"""
      pageAsString must include("TransferringBody1")
      pageAsString must include("SeriesName1")
    }

    "render correctly formatted last updated dates" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)
      val formatter = DateTimeFormatter.ofPattern("d'%s' MMMM yyyy, hh:mma")

      status(response) mustBe OK
      val dateTime = ZonedDateTime.now().minus(1, ChronoUnit.DAYS)
      val formatted = formatter.format(dateTime).format(getDaySuffix(dateTime.getDayOfMonth))
      val date =
        if (formatted.endsWith("AM") || formatted.endsWith("PM")) formatted.dropRight(2) + formatted.takeRight(2).toLowerCase
        else formatted
      pageAsString must include(s"$date (Yesterday)")
    }

    "return 403 if the page is accessed by a non-TNA user" in {
      val controller = new ManageTransfersController(
        getValidKeycloakConfiguration,
        getAuthorisedSecurityComponents,
        new ConsignmentService(new GraphQLConfiguration(app.configuration))
      )
      val response = controller.manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if accessed by a logged-out user" in {
      val controller = new ManageTransfersController(
        getValidKeycloakConfiguration,
        getUnauthorisedSecurityComponents,
        new ConsignmentService(new GraphQLConfiguration(app.configuration))
      )
      val response = controller.manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  private def instantiateController() = new ManageTransfersController(
    getValidTNAUserKeycloakConfiguration(),
    getAuthorisedSecurityComponents,
    new ConsignmentService(new GraphQLConfiguration(app.configuration))
  )

  private def getDaySuffix(day: Int): String = day match {
    case 1 | 21 | 31 => "st"
    case 2 | 22      => "nd"
    case 3 | 23      => "rd"
    case _           => "th"
  }
}
