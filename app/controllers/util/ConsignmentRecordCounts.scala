package controllers.util

import graphql.codegen.GetConsignmentDetailsForMetadataReview.{getConsignmentDetailsForMetadataReview => gcdfmr}

object ConsignmentRecordCounts {
  def openRecords(consignment: gcdfmr.GetConsignment): Int =
    consignment.totalFiles - consignment.totalClosedRecords - consignment.totalRetainedRecords
}
