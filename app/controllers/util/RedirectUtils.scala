package controllers.util

import controllers.routes
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import services.ConsignmentStatusService
import services.Statuses.{InProgressValue, MetadataReviewType}

import java.util.UUID

object RedirectUtils {
  def redirectIfReviewInProgress(
    consignmentId: UUID,
    consignmentStatuses: Seq[ConsignmentStatuses]
  ): Result => Result = requestedPage => {
    if (ConsignmentStatusService.statusValue(MetadataReviewType)(consignmentStatuses) == InProgressValue) {
      Redirect(routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId))
    } else requestedPage
  }
}
