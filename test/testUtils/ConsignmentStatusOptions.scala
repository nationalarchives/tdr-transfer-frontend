package testUtils

import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import org.scalatest.prop.TableFor5
import org.scalatest.prop.Tables.Table

object ConsignmentStatusesOptions {
  val completed: Option[String] = Some("Completed")
  val inProgress: Option[String] = Some("InProgress")
  val completedWithIssues: Option[String] = Some("CompletedWithIssues")
  val failed: Option[String] = Some("Failed")

  val commonStatesAndStatuses: TableFor5[String, CurrentStatus, String, String, String] = Table(
    ("expected transfer state", "current status", "action url", "transfer state", "action text"),
    ("upload in progress", CurrentStatus(completed, completed, inProgress, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    ("upload failed", CurrentStatus(completed, completed, failed, None, None, None, None, None, None), "/upload", "Failed", "View errors"),
    ("upload completed with issues", CurrentStatus(completed, completed, completedWithIssues, None, None, None, None, None, None), "/upload", "Failed", "View errors"),
    ("upload completed", CurrentStatus(completed, completed, completed, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    ("client checks in progress", CurrentStatus(completed, completed, completed, inProgress, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    ("client checks failed", CurrentStatus(completed, completed, completed, failed, None, None, None, None, None), "/upload", "Failed", "View errors"),
    (
      "client checks completed with issues",
      CurrentStatus(completed, completed, completed, completedWithIssues, None, None, None, None, None),
      "/upload",
      "Failed",
      "View errors"
    ),
    ("client checks completed", CurrentStatus(completed, completed, completed, completed, None, None, None, None, None), "/file-checks", "In Progress", "Resume transfer"),
    ("av file check in progress", CurrentStatus(completed, completed, completed, completed, inProgress, None, None, None, None), "/file-checks", "In Progress", "Resume transfer"),
    ("av file check failed", CurrentStatus(completed, completed, completed, completed, failed, None, None, None, None), "/file-checks-results", "Failed", "View errors"),
    (
      "av file check completed with issues",
      CurrentStatus(completed, completed, completed, completed, completedWithIssues, None, None, None, None),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    ("av file check completed", CurrentStatus(completed, completed, completed, completed, completed, None, None, None, None), "/file-checks", "In Progress", "Resume transfer"),
    (
      "checksum file check in progress",
      CurrentStatus(completed, completed, completed, completed, completed, inProgress, None, None, None),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    ("checksum file check failed", CurrentStatus(completed, completed, completed, completed, completed, failed, None, None, None), "/file-checks-results", "Failed", "View errors"),
    (
      "checksum file check completed with issues",
      CurrentStatus(completed, completed, completed, completed, completed, completedWithIssues, None, None, None),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "checksum file check completed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, None, None, None),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check in progress",
      CurrentStatus(completed, completed, completed, completed, completed, completed, inProgress, None, None),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check failed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, failed, None, None),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "FFID file check completed with issues",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completedWithIssues, None, None),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "all file checks completed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completed, None, None),
      "/file-checks-results",
      "In Progress",
      "Resume transfer"
    )
  )

  val expectedStandardStatesAndStatuses: TableFor5[String, CurrentStatus, String, String, String] = Table(
    ("expected transfer state", "current status", "action url", "transfer state", "action text"),
    ("series in progress", CurrentStatus(None, None, None, None, None, None, None, None, None), "/series", "In Progress", "Resume transfer"),
    ("series completed", CurrentStatus(completed, None, None, None, None, None, None, None, None), "/transfer-agreement", "In Progress", "Resume transfer"),
    (
      "transfer agreement in progress",
      CurrentStatus(completed, inProgress, None, None, None, None, None, None, None),
      "/transfer-agreement-continued",
      "In Progress",
      "Resume transfer"
    ),
    ("transfer agreement completed", CurrentStatus(completed, completed, None, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    (
      "confirm transfer completed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, None),
      "/file-checks-results",
      "In Progress",
      "Resume transfer"
    ),
    (
      "export in progress",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, inProgress),
      "/additional-metadata/download-metadata/csv",
      "In Progress",
      "Download report"
    ),
    (
      "export failed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, failed),
      "mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Export failure",
      "Failed",
      "Contact us"
    ),
    (
      "export completed",
      CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, completed),
      "/additional-metadata/download-metadata/csv",
      "Transferred",
      "Download report"
    )
  ) ++ commonStatesAndStatuses

  val expectedJudgmentStatesAndStatuses: TableFor5[String, CurrentStatus, String, String, String] = Table(
    ("expected transfer state", "current status", "action url", "transfer state", "action text"),
    ("before upload", CurrentStatus(None, None, None, None, None, None, None, None, None), "/before-uploading", "In Progress", "Resume transfer"),
    ("export completed", CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, completed), "/transfer-complete", "Transferred", "View")
  ) ++ commonStatesAndStatuses
}
