package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.DateUtils
import graphql.codegen.GetConsignmentReviewDetails.getConsignmentReviewDetails.GetConsignmentReviewDetails
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

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
    ConsignmentReviewDetails(
      consignmentId = reviewDetails.consignmentId,
      // Replace ASCII hyphens with non-breaking hyphens (U+2011) so the reference renders on a single line
      // without needing CSS. Copy-paste will yield non-ASCII hyphens; acceptable for a display-only label.
      consignmentReference = reviewDetails.consignmentReference.replace('-', '\u2011'),
      reviewStatus = reviewDetails.reviewStatus,
      transferringBodyName = reviewDetails.transferringBodyName.getOrElse(""),
      seriesName = reviewDetails.seriesName.getOrElse(""),
      lastUpdated = DateUtils.formatWithDaySuffixAndRelative(reviewDetails.lastUpdated)
    )
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
