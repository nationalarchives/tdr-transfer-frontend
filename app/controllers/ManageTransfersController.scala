package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.GetConsignmentReviewDetails.getConsignmentReviewDetails.GetConsignmentReviewDetails
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class ManageTransfersController @Inject() (
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig
) extends TokenSecurity {

  private val validTabs = Set("requested", "rejected", "approved", "transferred", "all")

  def manageTransfers(tab: Option[String]): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockMetadataReviewV2) {
      Future.successful(NotFound(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      val activeTab = tab.map(_.toLowerCase).filter(validTabs.contains).getOrElse("requested")
      val statusFilter = if (activeTab == "all") None else Some(activeTab.capitalize)
      consignmentService
        .getConsignmentReviewDetails(statusFilter, request.token.bearerAccessToken)
        .map { reviewDetails =>
          Ok(views.html.tna.manageTransfers(reviewDetails.map(toConsignmentReviewDetails), activeTab))
        }
    }
  }

  private def toConsignmentReviewDetails(reviewDetails: GetConsignmentReviewDetails): ConsignmentReviewDetails = {
    val formatter = DateTimeFormatter.ofPattern("d'%s' MMMM yyyy, hh:mma")
    ConsignmentReviewDetails(
      consignmentId = reviewDetails.consignmentId,
      // Replace ASCII hyphens with non-breaking hyphens (U+2011) so the reference renders on a single line
      // without needing CSS. Copy-paste will yield non-ASCII hyphens; acceptable for a display-only label.
      consignmentReference = reviewDetails.consignmentReference.replace('-', '\u2011'),
      reviewStatus = reviewDetails.reviewStatus,
      transferringBodyName = reviewDetails.transferringBodyName.getOrElse(""),
      seriesName = reviewDetails.seriesName.getOrElse(""),
      lastUpdated = formatDate(formatter, reviewDetails.lastUpdated)
    )
  }

  private def formatDate(formatter: DateTimeFormatter, dateTime: ZonedDateTime): String = {
    val formatted = formatter.format(dateTime).format(getDaySuffix(dateTime.getDayOfMonth))
    // Lowercase only the trailing AM/PM so the month stays capitalised, e.g. "20th April 2026, 10:32am"
    val date =
      if (formatted.endsWith("AM") || formatted.endsWith("PM")) formatted.dropRight(2) + formatted.takeRight(2).toLowerCase
      else formatted
    val noOfDays = LocalDate.now().toEpochDay - dateTime.toLocalDate.toEpochDay
    val days = noOfDays match {
      case 0   => "Today"
      case 1   => "Yesterday"
      case day => s"$day days ago"
    }
    s"$date ($days)"
  }

  private def getDaySuffix(day: Int): String = day match {
    case 1 | 21 | 31 => "st"
    case 2 | 22      => "nd"
    case 3 | 23      => "rd"
    case _           => "th"
  }
}

case class ConsignmentReviewDetails(
    consignmentId: UUID,
    consignmentReference: String,
    reviewStatus: String,
    transferringBodyName: String,
    seriesName: String,
    lastUpdated: String
)
