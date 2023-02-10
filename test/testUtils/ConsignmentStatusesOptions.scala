package testUtils

import controllers.util.TransferProgressUtils._
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import org.scalatest.prop.{TableFor10, TableFor4, TableFor5}
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
    UploadFailed -> ("/upload", "Failed", "Resume transfer"),
    UploadCompleted -> ("/upload", "In Progress", "Resume transfer"),
    ClientChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    ClientChecksFailed -> ("/file-checks", "Failed", "View errors"),
    ClientChecksCompleted -> ("/file-checks", "In Progress", "Resume transfer"),
    FileChecksInProgress -> ("/file-checks", "In Progress", "Resume transfer"),
    FileChecksFailed -> ("/file-checks-results", "Failed", "View errors"),
    FileChecksCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ConfirmTransferCompleted -> ("/file-checks-results", "In Progress", "Resume transfer"),
    ExportInProgress -> ("/additional-metadata/download-metadata/csv", "In Progress", "Download report"),
    ExportFailed -> ("mailto:%s?subject=Ref: TEST-TDR-2023-X - Export failure", "Failed", "Contact us"),
    ExportCompleted -> ("/additional-metadata/download-metadata/csv", "Transferred", "Download report"),
  )

  val expectedStandardStatesAndStatuses: TableFor5[TransferState, CurrentStatus, String, String, String] = Table(
    ("expectedState", "current status", "action url", "transfer state", "action text"),
    (SeriesInProgress, CurrentStatus(None, None, None, None, None, None, None, None, None), "", "", ""),
    (SeriesCompleted, CurrentStatus(completed, None, None, None, None, None, None, None, None), "", "", ""),
    (TransferAgreementInProgress, CurrentStatus(completed, inProgress, None, None, None, None, None, None, None), "", "", ""),
    (TransferAgreementCompleted, CurrentStatus(completed, completed, None, None, None, None, None, None, None), "", "", ""),
    (UploadInProgress, CurrentStatus(completed, completed, inProgress, None, None, None, None, None, None), "", "", ""),
    (UploadFailed, CurrentStatus(completed, completed, failed, None, None, None, None, None, None), "", "", ""),
    (UploadFailed, CurrentStatus(completed, completed, completedWithIssues, None, None, None, None, None, None), "", "", ""),
    (UploadCompleted, CurrentStatus(completed, completed, completed, None, None, None, None, None, None), "", "", ""),
    (ClientChecksInProgress, CurrentStatus(completed, completed, completed, inProgress, None, None, None, None, None), "", "", ""),
    (ClientChecksFailed, CurrentStatus(completed, completed, completed, failed, None, None, None, None, None), "", "", ""),
    (ClientChecksFailed, CurrentStatus(completed, completed, completed, completedWithIssues, None, None, None, None, None), "", "", ""),
    (ClientChecksCompleted, CurrentStatus(completed, completed, completed, completed, None, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, inProgress, None, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, failed, None, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completedWithIssues, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, inProgress, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, failed, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completedWithIssues, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, inProgress, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, failed, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, completedWithIssues, None, None), "", "", ""),
    (FileChecksCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, None, None), "", "", ""),
    (ConfirmTransferCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, None), "", "", ""),
    (ExportInProgress, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, inProgress), "", "", ""),
    (ExportFailed, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, failed), "", "", ""),
    (ExportCompleted, CurrentStatus(completed, completed, completed, completed, completed, completed, completed, completed, completed), "", "", "")
  )

  val expectedJudgmentStatesAndStatuses: TableFor5[TransferState, CurrentStatus, String, String, String] = Table(
    ("expectedState", "current status", "action url", "transfer state", "action text"),
    (BeforeUpload, CurrentStatus(None, None, None, None, None, None, None, None, None), "", "", ""),
    (UploadInProgress, CurrentStatus(None, None, inProgress, None, None, None, None, None, None), "", "", ""),
    (UploadFailed, CurrentStatus(None, None, failed, None, None, None, None, None, None), "", "", ""),
    (UploadFailed, CurrentStatus(None, None, completedWithIssues, None, None, None, None, None, None), "", "", ""),
    (UploadCompleted, CurrentStatus(None, None, completed, None, None, None, None, None, None), "", "", ""),
    (ClientChecksInProgress, CurrentStatus(None, None, completed, inProgress, None, None, None, None, None), "", "", ""),
    (ClientChecksFailed, CurrentStatus(None, None, completed, failed, None, None, None, None, None), "", "", ""),
    (ClientChecksFailed, CurrentStatus(None, None, completed, completedWithIssues, None, None, None, None, None), "", "", ""),
    (ClientChecksCompleted, CurrentStatus(None, None, completed, completed, None, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, inProgress, None, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, failed, None, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completedWithIssues, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, None, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, inProgress, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, failed, None, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completedWithIssues, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, completed, None, None, None), "", "", ""),
    (FileChecksInProgress, CurrentStatus(None, None, completed, completed, completed, completed, inProgress, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completed, failed, None, None), "", "", ""),
    (FileChecksFailed, CurrentStatus(None, None, completed, completed, completed, completed, completedWithIssues, None, None), "", "", ""),
    (FileChecksCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, None, None), "", "", ""),
    (ConfirmTransferCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, None), "", "", ""),
    (ExportInProgress, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, inProgress), "", "", ""),
    (ExportFailed, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, failed), "", "", ""),
    (ExportCompleted, CurrentStatus(None, None, completed, completed, completed, completed, completed, completed, completed), "", "", "")
  )

  def generateConsignmentEdges(consignmentId: UUID, consignmentType: String): List[Some[Edges]] = {
    expectedStandardStatesAndStatuses
      .zip(LazyList.from(1))
      .map(e => {
        val orderNumber = e._2
        val someDateTime = Some(ZonedDateTime.of(LocalDateTime.of(2022, 3, 10 + orderNumber, orderNumber, 0), ZoneId.systemDefault()))
        Some(
          Edges(
            Node(
              consignmentid = Some(UUID.randomUUID()),
              consignmentReference = s"TEST-TDR-0000-GBorderNumber",
              consignmentType = Some(consignmentType),
              exportDatetime = someDateTime,
              createdDatetime = someDateTime,
              currentStatus = e._1._2,
              totalFiles = orderNumber
            ),
            "Cursor"
          )
        )
      })
      .toList
  }
}
