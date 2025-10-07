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

import java.time.{LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext

class MetadataReviewControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  private val TNAUserType = "tna"

  implicit val ec: ExecutionContext = ExecutionContext.global

  "MetadataReviewController GET" should {

    "render the metadata review page" in {
      setGetConsignmentsForMetadataReviewResponse(wiremockServer)
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new MetadataReviewController(getValidTNAUserKeycloakConfiguration(), getAuthorisedSecurityComponents, consignmentService)
      val response = controller
        .metadataReviews()
        .apply(FakeRequest(GET, s"/metadata-review").withCSRFToken)
      val metadataReviewPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewPageAsString, userType = TNAUserType, consignmentExists = false)
      checkForExpectedMetadataReviewPageContent(metadataReviewPageAsString)
    }

    "return 403 if the metadata review page is accessed by a non TNA user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new MetadataReviewController(getValidKeycloakConfiguration, getAuthorisedSecurityComponents, consignmentService)
      val response = controller
        .metadataReviews()
        .apply(FakeRequest(GET, s"/metadata-review").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new MetadataReviewController(getValidKeycloakConfiguration, getUnauthorisedSecurityComponents, consignmentService)
      val response = controller
        .metadataReviews()
        .apply(FakeRequest(GET, s"/metadata-review").withCSRFToken)

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  def checkForExpectedMetadataReviewPageContent(metadataReviewPageAsString: String): Unit = {
    metadataReviewPageAsString must include("<h1 class=\"govuk-heading-l\">Metadata Reviews</h1>")
    metadataReviewPageAsString must include("""<th scope="col" class="govuk-table__header metadata-review-column-width">Consignment</th>""")
    metadataReviewPageAsString must include("""<th scope="col" class="govuk-table__header">Status</th>""")
    metadataReviewPageAsString must include("""<th scope="col" class="govuk-table__header">Department</th>""")
    metadataReviewPageAsString must include("""<th scope="col" class="govuk-table__header">Series</th>""")
    metadataReviewPageAsString must include("""<th scope="col" class="govuk-table__header metadata-review-column-width">Date submitted</th>""")
    metadataReviewPageAsString must include(s"""<th scope="col" class="govuk-table__header">
         |              <span class="govuk-visually-hidden">Actions</span>
         |            </th>""".stripMargin)

    val formatter = DateTimeFormatter.ofPattern("d'%s' MMMM yyyy")

    (1 to 4)
      .foreach(count => {
        val dateTime = if (count == 1) ZonedDateTime.now() else ZonedDateTime.now().minus(count, ChronoUnit.DAYS)
        val dayOfMonth = dateTime.getDayOfMonth
        val date = formatter.format(dateTime).format(getDaySuffix(dayOfMonth))
        val noOfDays = LocalDate.now().toEpochDay - dateTime.toLocalDate.toEpochDay
        val days = noOfDays match {
          case 0   => "Today"
          case 1   => "Yesterday"
          case day => s"$day days ago"
        }
        metadataReviewPageAsString must include(s"""<th scope="row" class="govuk-table__header">TDR-2024-TEST$count</th>""")
        metadataReviewPageAsString must include(s"""<strong class="tdr-tag tdr-tag--green">Requested</strong>""")
        metadataReviewPageAsString must include(s"""<td class="govuk-table__cell">TransferringBody$count</td>""")
        metadataReviewPageAsString must include(s"""<td class="govuk-table__cell">SeriesName$count</td>""")
        metadataReviewPageAsString must include(s"""<td class="govuk-table__cell">$date <br> ($days)</td>""")
      })
  }

  private def getDaySuffix(day: Int): String = day match {
    case 1 | 21 | 31 => "st"
    case 2 | 22      => "nd"
    case 3 | 23      => "rd"
    case _           => "th"
  }
}
