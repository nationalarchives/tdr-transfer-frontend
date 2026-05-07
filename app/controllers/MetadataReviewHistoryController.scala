package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.DateUtils
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import uk.gov.nationalarchives.tdr.common.utils.statuses.MetadataReviewLogAction.Submission

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object MetadataReviewHistoryController {
  case class HistoryRow(
      submissionNo: Int,
      dateSubmitted: String,
      status: String,
      dateReviewed: Option[String],
      reviewedBy: Option[String],
      reviewedByEmail: Option[String],
      note: Option[String]
  )
}

@Singleton
class MetadataReviewHistoryController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val messagingService: MessagingService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport
    with Logging {

  import MetadataReviewHistoryController.HistoryRow

  def getConsignmentMetadataHistory(consignmentId: UUID): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockMetadataReviewV2) {
      Future.successful(NotFound(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      for {
        consignment <- consignmentService.getConsignmentDetailForMetadataReview(consignmentId, request.token.bearerAccessToken)
        userDetails <- keycloakConfiguration.userDetails(consignment.userid.toString)
        sortedLogs = consignment.metadataReviewLogs.sortBy(_.eventTime)
        submissions = sortedLogs.filter(_.action == Submission.value)
        reviews = sortedLogs.filterNot(_.action == Submission.value)
        reviewerIds = reviews.map(_.userId.toString).distinct
        detailEntries <- Future.sequence(reviewerIds.map(id => keycloakConfiguration.userDetails(id).map(d => id -> d)))
        detailMap = detailEntries.toMap
        historyRows = submissions.zipWithIndex.map { case (submission, idx) =>
          val review = reviews.lift(idx)
          val reviewer = review.flatMap(r => detailMap.get(r.userId.toString))
          HistoryRow(
            submissionNo = idx + 1,
            dateSubmitted = DateUtils.formatWithDaySuffix(submission.eventTime),
            status = review.map(_.action).getOrElse(Submission.value),
            dateReviewed = review.map(r => DateUtils.formatWithDaySuffix(r.eventTime)),
            reviewedBy = reviewer.map(d => s"${d.firstName} ${d.lastName}"),
            reviewedByEmail = reviewer.map(_.email),
            note = review.flatMap(_.metadataReviewNotes)
          )
        }.reverse
      } yield {
        Ok(
          views.html.tna.metadataReviewHistory(
            consignmentId,
            consignment,
            historyRows,
            userDetails.email,
            request.token.isTransferAdviser
          )
        )
      }
    }
  }


}
