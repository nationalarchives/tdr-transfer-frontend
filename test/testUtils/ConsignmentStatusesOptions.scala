package testUtils

import controllers.util.TransferProgressUtils._
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import org.scalatest.prop.{TableFor10, TableFor2, TableFor4, TableFor5}
import org.scalatest.prop.Tables.Table

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

object ConsignmentStatusesOptions {
  val completed: Option[String] = Some("Completed")
  val inProgress: Option[String] = Some("InProgress")
  val completedWithIssues: Option[String] = Some("CompletedWithIssues")
  val failed: Option[String] = Some("Failed")

  val transferStateToExpectedStandardAction: Map[TransferState, (String, String, String)] = Map(
    SeriesInProgress -> ("/series", "In Progress", "Resume transfer"),
    SeriesCompleted -> ("/transfer-agreement", "In Progress", "Resume transfer"),
    TransferAgreementInProgress -> ("/transfer-agreement-continued", "In Progress", "Resume transfer"),
    TransferAgreementCompleted -> ("/upload", "In Progress", "Resume transfer"),
    BeforeUpload -> ("/before-uploading", "In Progress", "Resume transfer"),
    UploadInProgress -> ("/upload", "In Progress", "Resume transfer"),
    UploadFailed -> ("/upload", "Failed", "View errors"),
    UploadCompleted -> ("/upload", "In Progress", "Resume transfer"),
    ClientChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    ClientChecksFailed -> ("/file-checks-results", "Failed", "View errors"),
    ClientChecksCompleted -> ("/file-checks-file-checks", "In Progress", "Resume transfer"),
    FileChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    FileChecksFailed -> ("/file-checks-results", "Failed", "View errors"),
    FileChecksCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ConfirmTransferCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ExportInProgress -> ("/additional-metadata/download-metadata/csv", "In Progress", "Download report"),
    ExportFailed -> ("mailto:%s?subject=Ref: TEST-TDR-2023-X - Export failure", "Failed", "Contact us"),
    ExportCompleted -> ("/additional-metadata/download-metadata/csv", "Transferred", "Download report")
  )

  val transferStateToExpectedJudgmentAction: Map[TransferState, (String, String, String)] = Map(
    BeforeUpload -> ("/before-uploading", "In Progress", "Resume transfer"),
    UploadInProgress -> ("/upload", "In Progress", "Resume transfer"),
    UploadFailed -> ("/upload", "Failed", "Resume transfer"),
    UploadCompleted -> ("/upload", "In Progress", "Resume transfer"),
    ClientChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    ClientChecksFailed -> ("/file-checks", "Failed", "View errors"),
    ClientChecksCompleted -> ("/file-checks", "In Progress", "Resume transfer"),
    FileChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    FileChecksFailed -> ("/file-checks-results", "Failed", "View errors"),
    FileChecksCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ConfirmTransferCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ExportInProgress -> ("/transfer-complete", "Transferred", "View"),
    ExportFailed -> ("mailto:%s?subject=Ref: TEST-TDR-2023-X - Export failure", "Failed", "Contact us"),
    ExportCompleted -> ("/transfer-complete", "Transferred", "View")
  )

  val expectedStandardStatesAndStatuses: TableFor5[TransferState, CurrentStatus, String, String, String] = Table(
    ("expectedState", "current status", "action url", "transfer state", "action text"),
    (SeriesInProgress, CurrentStatus(None, None, None, None, None, None, None, None, None), "/series", "In Progress", "Resume transfer"),
    (SeriesCompleted, CurrentStatus(completed, None, None, None, None, None, None, None, None), "/transfer-agreement", "In Progress", "Resume transfer"),
    (TransferAgreementInProgress, CurrentStatus(completed, inProgress, None, None, None, None, None, None, None), "/transfer-agreement-continued", "In Progress", "Resume transfer"),
    (TransferAgreementCompleted, CurrentStatus(completed, completed, None, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    (UploadInProgress, CurrentStatus(completed, completed, inProgress, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    (UploadFailed, CurrentStatus(completed, completed, failed, None, None, None, None, None, None), "/upload", "Failed", "View errors"),
    (UploadFailed, CurrentStatus(completed, completed, completedWithIssues, None, None, None, None, None, None), "/upload", "Failed", "View errors"),
    (UploadCompleted, CurrentStatus(completed, completed, completed, None, None, None, None, None, None), "/upload", "In Progress", "Resume transfer"),
    (ClientChecksInProgress, CurrentStatus(completed, completed, completed, inProgress, None, None, None, None, None), "/file-checks", "In Progress", "Resume transfer"),
    (ClientChecksFailed, CurrentStatus(completed, completed, completed, failed, None, None, None, None, None), "/file-checks-results", "Failed", "View errors"),
    (ClientChecksFailed, CurrentStatus(completed, completed, completed, completedWithIssues, None, None, None, None, None), "/file-checks-results", "Failed", "View errors"),
    (ClientChecksCompleted, CurrentStatus(completed, completed, completed, completed, None, None, None, None, None), "/file-checks-results", "In Progress", "Resume transfer"),
//    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, inProgress, None, None, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, failed, None, None, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completedWithIssues, None, None, None, None), "", "", ""),
//    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, None, None, None, None), "", "", ""),
//    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, inProgress, None, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, failed, None, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completedWithIssues, None, None, None), "", "", ""),
//    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, None, None, None), "", "", ""),
//    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, inProgress, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, failed, None, None), "", "", ""),
//    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, completedWithIssues, None, None), "", "", ""),
//    (FileChecksCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, None, None), "", "", ""),
    (ConfirmTransferCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, None), "/file-checks-results", "In Progress", "Resume transfer"),
    (ExportInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, inProgress), "/additional-metadata/download-metadata/csv", "In Progress", "Download report"),
    (ExportFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, failed), "mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Export failure", "Failed", "Contact us"),
//    (ExportCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, completed), "", "", "")
  )

  val expectedJudgmentStatesAndStatuses: TableFor2[TransferState, CurrentStatus] = Table(
    ("expectedState", "current status"),
    (BeforeUpload, CurrentStatus(None, None, None, None, None, None, None, None, None)),
    (UploadInProgress, CurrentStatus(None, None, inProgress, None, None, None, None, None, None)),
    (UploadFailed, CurrentStatus(None, None, failed, None, None, None, None, None, None)),
    (UploadFailed, CurrentStatus(None, None, completedWithIssues, None, None, None, None, None, None)),
    (UploadCompleted, CurrentStatus(None, None, completed, None, None, None, None, None, None)),
    (ClientChecksInProgress, CurrentStatus(None, None, completed, inProgress, None, None, None, None, None)),
    (ClientChecksFailed, CurrentStatus(None, None, completed, failed, None, None, None, None, None)),
    (ClientChecksFailed, CurrentStatus(None, None, completed, completedWithIssues, None, None, None, None, None)),
    (ClientChecksCompleted, CurrentStatus(None, None, completed, completed, None, None, None, None, None)),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, inProgress, None, None, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, failed, None, None, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completedWithIssues, None, None, None, None)),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, None, None, None, None)),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, inProgress, None, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, failed, None, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completedWithIssues, None, None, None)),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, completed, None, None, None)),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, completed, inProgress, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completed, failed, None, None)),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completed, completedWithIssues, None, None)),
    (FileChecksCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, None, None)),
    (ConfirmTransferCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, None)),
    (ExportInProgress, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, inProgress)),
    (ExportFailed, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, failed)),
    (ExportCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, completed))
  )

  def generateConsignmentEdges(statuses: List[CurrentStatus], consignmentId: UUID, consignmentType: String): List[Some[Edges]] = {
    //val statuses = if (consignmentType == "judgment") { expectedJudgmentStatesAndStatuses } else { expectedStandardStatesAndStatuses }
    statuses
      .zip(LazyList.from(1))
      .map(e => {
        val orderNumber = e._2
        val someDateTime = Some(ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault()))
        Some(
          Edges(
            Node(
              consignmentid = Some(consignmentId),
              consignmentReference = s"consignment-ref-$orderNumber",
              consignmentType = Some(consignmentType),
              exportDatetime = someDateTime,
              createdDatetime = someDateTime,
              currentStatus = e._1,
              totalFiles = orderNumber
            ),
            "Cursor"
          )
        )
      })
      .toList
  }
}
