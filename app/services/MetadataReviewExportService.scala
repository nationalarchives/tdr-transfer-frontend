package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.{DateUtils, ExcelUtils}
import graphql.codegen.GetConsignmentDetailsForMetadataReview.{getConsignmentDetailsForMetadataReview => gcdfmr}
import graphql.codegen.GetConsignmentReviewDetails.getConsignmentReviewDetails.GetConsignmentReviewDetails
import uk.gov.nationalarchives.tdr.common.utils.statuses.MetadataReviewLogAction._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class MetadataReviewExportService @Inject() (
    consignmentService: ConsignmentService,
    keycloakConfiguration: KeycloakConfiguration,
    applicationConfig: ApplicationConfig
)(implicit val executionContext: ExecutionContext) {
  private val excelDateFormat = "yyyy-MM-dd'T'HH:mm"
  private val header = List(
    "Consignment Ref",
    "Current Status",
    "User Email Address",
    "Last Updated",
    "Department",
    "Series",
    "No. Records",
    "No. Open",
    "No. Closed",
    "Submission No.",
    "Date Submitted",
    "Review Action",
    "Date Reviewed",
    "Reviewed By",
    "Reason for Action"
  )
  def generateReviewHistoryExcel(token: BearerAccessToken): Future[Array[Byte]] = {
    for {
      reviewDetails <- consignmentService.getConsignmentReviewDetails(None, token)
      filteredDetails = reviewDetails.filterNot(rd => applicationConfig.seriesNameFilters.exists(filter => rd.seriesName.contains(filter)))
      consignmentDetails <- batchedTraverse(filteredDetails) { rd =>
        consignmentService.getConsignmentDetailForMetadataReview(rd.consignmentId, token).map(rd -> _)
      }
      allUserIds = collectUserIds(consignmentDetails.map { case (_, consignment) => consignment })
      userEmails <- resolveUserEmails(allUserIds)
    } yield {
      val rows = consignmentDetails.flatMap { case (reviewDetail, consignment) =>
        buildRowsForConsignment(reviewDetail, consignment, userEmails)
      }
      ExcelUtils.writeExcel("Metadata Review History", header :: rows)
    }
  }
  private def collectUserIds(consignments: List[gcdfmr.GetConsignment]): Set[UUID] = {
    consignments.flatMap { consignment =>
      consignment.userid :: consignment.metadataReviewLogs.map(_.userId)
    }.toSet
  }
  private def resolveUserEmails(userIds: Set[UUID]): Future[Map[UUID, String]] = {
    val lookups = batchedTraverse(userIds.toList) { userId =>
      keycloakConfiguration
        .userDetails(userId.toString)
        .map(details => userId -> details.email)
        .recover { case _ => userId -> "" }
    }
    lookups.map(_.toMap)
  }
  private def batchedTraverse[A, B](items: List[A], batchSize: Int = 5)(f: A => Future[B]): Future[List[B]] = {
    items.grouped(batchSize).foldLeft(Future.successful(List.empty[B])) { (accFuture, batch) =>
      accFuture.flatMap { acc =>
        Future.traverse(batch)(f).map(acc ++ _)
      }
    }
  }
  private def buildRowsForConsignment(
      reviewDetail: GetConsignmentReviewDetails,
      consignment: gcdfmr.GetConsignment,
      userEmails: Map[UUID, String]
  ): List[List[String]] = {
    val consignmentRef = reviewDetail.consignmentReference
    val currentStatus = reviewDetail.reviewStatus
    val userEmail = userEmails.getOrElse(consignment.userid, "")
    val lastUpdated = DateUtils.format(reviewDetail.lastUpdated, excelDateFormat)
    val department = reviewDetail.transferringBodyName.getOrElse("")
    val series = reviewDetail.seriesName.getOrElse("")
    val totalRecords = consignment.totalFiles.toString
    val totalClosed = consignment.totalClosedRecords.toString
    val totalOpen = (consignment.totalFiles - consignment.totalClosedRecords).toString
    val commonFields = List(consignmentRef, currentStatus, userEmail, lastUpdated, department, series, totalRecords, totalOpen, totalClosed)
    val sortedLogs = consignment.metadataReviewLogs.sortBy(_.eventTime.toInstant.toEpochMilli)
    val submissions = sortedLogs.filter(_.action == Submission.value)
    val reviews = sortedLogs.filter(log => log.action == Rejection.value || log.action == Approval.value)
    val submissionRows = submissions.zipWithIndex.map { case (submission, index) =>
      val submissionNum = (index + 1).toString
      val submissionDate = DateUtils.format(submission.eventTime, excelDateFormat)
      reviews.lift(index) match {
        case Some(review) =>
          val logAction = MetadataReviewLogAction(review.action)
          val reviewedBy = userEmails.getOrElse(review.userId, "")
          val reason = review.metadataReviewNotes.getOrElse("")
          commonFields ++ List(submissionNum, submissionDate, logAction.reviewStatus.value, DateUtils.format(review.eventTime, excelDateFormat), reviewedBy, reason)
        case None =>
          commonFields ++ List(submissionNum, submissionDate, "", "", "", "")
      }
    }
    val transferredRow =
      if (reviewDetail.reviewStatus == Confirmation.reviewStatus.value)
        List(commonFields ++ List(submissions.size.toString, lastUpdated, Confirmation.reviewStatus.value, "", "", ""))
      else
        List.empty
    submissionRows ++ transferredRow
  }
}
