package testUtils

import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.ConsignmentStatuses
import org.scalatest.prop.TableFor5
import org.scalatest.prop.Tables.Table
import services.Statuses._

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

object ConsignmentStatusesOptions {
  private val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  val completed: Option[String] = Some("Completed")
  val inProgress: Option[String] = Some("InProgress")
  val completedWithIssues: Option[String] = Some("CompletedWithIssues")
  val failed: Option[String] = Some("Failed")

  private val beforeUpload = List()
  private val seriesInProgress: Map[StatusType, StatusValue] = Map.empty
  private val seriesCompleted: Map[StatusType, StatusValue] = Map(SeriesType -> CompletedValue)
  private val taInProgress: Map[StatusType, StatusValue] = Map(TransferAgreementType -> InProgressValue)
  private val taCompleted: Map[StatusType, StatusValue] = Map(TransferAgreementType -> CompletedValue)
  private val uploadInProgress: Map[StatusType, StatusValue] = Map(UploadType -> InProgressValue)
  private val uploadFailed: Map[StatusType, StatusValue] = Map(UploadType -> FailedValue)
  private val uploadWithIssues: Map[StatusType, StatusValue] = Map(UploadType -> CompletedWithIssuesValue)
  private val uploadCompleted: Map[StatusType, StatusValue] = Map(UploadType -> CompletedValue)
  private val clientChecksInProgress: Map[StatusType, StatusValue] = Map(ClientChecksType -> InProgressValue)
  private val clientChecksFailed: Map[StatusType, StatusValue] = Map(ClientChecksType -> FailedValue)
  private val clientChecksWithIssues: Map[StatusType, StatusValue] = Map(ClientChecksType -> CompletedWithIssuesValue)
  private val clientChecksCompleted: Map[StatusType, StatusValue] = Map(ClientChecksType -> CompletedValue)
  private val antivirusInProgress: Map[StatusType, StatusValue] = Map(ServerAntivirusType -> InProgressValue)
  private val antivirusFailed: Map[StatusType, StatusValue] = Map(ServerAntivirusType -> FailedValue)
  private val antivirusWithIssues: Map[StatusType, StatusValue] = Map(ServerAntivirusType -> CompletedWithIssuesValue)
  private val antivirusCompleted: Map[StatusType, StatusValue] = Map(ServerAntivirusType -> CompletedValue)
  private val checksumInProgress: Map[StatusType, StatusValue] = Map(ServerChecksumType -> InProgressValue)
  private val checksumFailed: Map[StatusType, StatusValue] = Map(ServerChecksumType -> FailedValue)
  private val checksumWithIssues: Map[StatusType, StatusValue] = Map(ServerChecksumType -> CompletedWithIssuesValue)
  private val checksumCompleted: Map[StatusType, StatusValue] = Map(ServerChecksumType -> CompletedValue)
  private val ffidInProgress: Map[StatusType, StatusValue] = Map(ServerFFIDType -> InProgressValue)
  private val ffidFailed: Map[StatusType, StatusValue] = Map(ServerFFIDType -> FailedValue)
  private val ffidWithIssues: Map[StatusType, StatusValue] = Map(ServerFFIDType -> CompletedWithIssuesValue)
  private val ffidCompleted: Map[StatusType, StatusValue] = Map(ServerFFIDType -> CompletedValue)
  private val descriptiveMetadataNotEntered: Map[StatusType, StatusValue] = Map(DescriptiveMetadataType -> NotEnteredValue)
  private val descriptiveMetadataEntered: Map[StatusType, StatusValue] = Map(DescriptiveMetadataType -> EnteredValue)
  private val closureMetadataNotEntered: Map[StatusType, StatusValue] = Map(ClosureMetadataType -> NotEnteredValue)
  private val closureMetadataIncomplete: Map[StatusType, StatusValue] = Map(ClosureMetadataType -> IncompleteValue)
  private val closureMetadataComplete: Map[StatusType, StatusValue] = Map(ClosureMetadataType -> CompletedValue)
  private val confirmCompleted: Map[StatusType, StatusValue] = Map(ConfirmTransferType -> CompletedValue)
  private val exportInProgress: Map[StatusType, StatusValue] = Map(ExportType -> InProgressValue)
  private val exportFailed: Map[StatusType, StatusValue] = Map(ExportType -> FailedValue)
  private val exportCompleted: Map[StatusType, StatusValue] = Map(ExportType -> CompletedValue)

  private val defaultStatuses: Map[StatusType, StatusValue] = descriptiveMetadataNotEntered ++ closureMetadataNotEntered

  val expectedStandardStatesAndStatuses: TableFor5[String, List[ConsignmentStatuses], String, String, String] = Table(
    ("expected transfer state", "statuses", "action url", "transfer state", "action text"),
    ("series in progress", generateStatuses(seriesInProgress), "/series", "In Progress", "Resume transfer"),
    (
      "series completed",
      generateStatuses(
        seriesCompleted
      ),
      "/transfer-agreement",
      "In Progress",
      "Resume transfer"
    ),
    (
      "transfer agreement in progress",
      generateStatuses(
        seriesCompleted ++ taInProgress
      ),
      "/transfer-agreement-continued",
      "In Progress",
      "Resume transfer"
    ),
    (
      "transfer agreement completed",
      generateStatuses(
        seriesCompleted ++ taCompleted
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "upload in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadInProgress
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "upload failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadFailed
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "upload completed with issues",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadWithIssues
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "upload completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadCompleted
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "client checks in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadCompleted ++ clientChecksInProgress
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "client checks failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadCompleted ++ clientChecksFailed
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "client checks completed with issues",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadCompleted ++ clientChecksWithIssues
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "client checks completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadCompleted ++ clientChecksCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "client checks abandoned",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ uploadInProgress ++ clientChecksInProgress,
        modifiedDateTime = None
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "av file check in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "av file check failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "av file check completed with issues",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "av file check completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "checksum file check in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "checksum file check failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "checksum file check completed with issues",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "checksum file check completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "FFID file check completed with issues",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "all file checks completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted
      ),
      "/file-checks-results",
      "In Progress",
      "Resume transfer"
    ),
    (
      "descriptive metadata entered only",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ descriptiveMetadataEntered ++ closureMetadataNotEntered,
        includeDefaultStatuses = false
      ),
      "/additional-metadata",
      "In Progress",
      "Resume transfer"
    ),
    (
      "descriptive metadata entered and incomplete closure metadata",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ descriptiveMetadataEntered ++ closureMetadataIncomplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata",
      "In Progress",
      "Resume transfer"
    ),
    (
      "descriptive metadata entered and complete closure metadata",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ descriptiveMetadataEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata",
      "In Progress",
      "Resume transfer"
    ),
    (
      "closure metadata incomplete only",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ descriptiveMetadataNotEntered ++ closureMetadataIncomplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata",
      "In Progress",
      "Resume transfer"
    ),
    (
      "closure metadata complete only",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ descriptiveMetadataNotEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata",
      "In Progress",
      "Resume transfer"
    ),
    (
      "confirm transfer completed with no additional metadata",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted
      ),
      "/transfer-complete",
      "In Progress",
      "Resume transfer"
    ),
    (
      "confirm transfer completed with additional metadata entered",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ descriptiveMetadataEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/transfer-complete",
      "In Progress",
      "Resume transfer"
    ),
    (
      "confirm transfer completed with descriptive metadata only entered",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ descriptiveMetadataEntered ++ closureMetadataNotEntered,
        includeDefaultStatuses = false
      ),
      "/transfer-complete",
      "In Progress",
      "Resume transfer"
    ),
    (
      "confirm transfer completed with closure metadata only entered",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ descriptiveMetadataNotEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/transfer-complete",
      "In Progress",
      "Resume transfer"
    ),
    (
      "export in progress",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportInProgress
      ),
      "/additional-metadata/download-metadata/csv",
      "Transferred",
      "Download report"
    ),
    (
      "export in progress with metadata entered",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportInProgress ++ descriptiveMetadataEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata/download-metadata/csv",
      "Transferred",
      "Download report"
    ),
    (
      "export failed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportFailed
      ),
      "mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Export failure",
      "Failed",
      "Contact us"
    ),
    (
      "export failed with metadata entered",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportFailed ++ descriptiveMetadataEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Export failure",
      "Failed",
      "Contact us"
    ),
    (
      "export completed",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportCompleted
      ),
      "/additional-metadata/download-metadata/csv",
      "Transferred",
      "Download report"
    ),
    (
      "export completed with metadata entered",
      generateStatuses(
        seriesCompleted ++ taCompleted ++ clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportCompleted ++ descriptiveMetadataEntered ++ closureMetadataComplete,
        includeDefaultStatuses = false
      ),
      "/additional-metadata/download-metadata/csv",
      "Transferred",
      "Download report"
    )
  )

  val expectedJudgmentStatesAndStatuses: TableFor5[String, List[ConsignmentStatuses], String, String, String] = Table(
    ("expected transfer state", "statuses", "action url", "transfer state", "action text"),
    (
      "before upload",
      beforeUpload,
      "/before-uploading",
      "In Progress",
      "Resume transfer"
    ),
    (
      "upload in progress",
      generateStatuses(
        uploadInProgress
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "upload failed",
      generateStatuses(
        uploadFailed
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "upload completed",
      generateStatuses(
        uploadCompleted
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "client checks in progress",
      generateStatuses(
        uploadCompleted ++ clientChecksInProgress
      ),
      "/upload",
      "In Progress",
      "Resume transfer"
    ),
    (
      "client checks failed",
      generateStatuses(
        uploadCompleted ++ clientChecksFailed
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "client checks completed with issues",
      generateStatuses(
        uploadCompleted ++ clientChecksWithIssues
      ),
      "/upload",
      "Failed",
      "View errors"
    ),
    (
      "client checks completed",
      generateStatuses(
        uploadCompleted ++ clientChecksCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "av file check in progress",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "av file check failed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "av file check completed with issues",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "av file check completed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "checksum file check in progress",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "checksum file check failed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "checksum file check completed with issues",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "checksum file check completed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check in progress",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidInProgress
      ),
      "/file-checks",
      "In Progress",
      "Resume transfer"
    ),
    (
      "FFID file check failed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidFailed
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "FFID file check completed with issues",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidWithIssues
      ),
      "/file-checks-results",
      "Failed",
      "View errors"
    ),
    (
      "all file checks completed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted
      ),
      "/file-checks-results",
      "In Progress",
      "Resume transfer"
    ),
    (
      "export in progress",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportInProgress
      ),
      "/transfer-complete",
      "Transferred",
      "View"
    ),
    (
      "export failed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportFailed
      ),
      "mailto:nationalArchives.email?subject=Ref: consignment-ref-1 - Export failure",
      "Failed",
      "Contact us"
    ),
    (
      "export completed",
      generateStatuses(
        clientChecksCompleted ++ uploadCompleted ++ antivirusCompleted ++ checksumCompleted ++ ffidCompleted ++ confirmCompleted ++ exportCompleted
      ),
      "/transfer-complete",
      "Transferred",
      "View"
    )
  )

  def generateStatuses(
      statuses: Map[StatusType, StatusValue],
      consignmentId: UUID = UUID.randomUUID(),
      modifiedDateTime: Option[ZonedDateTime] = Some(someDateTime),
      includeDefaultStatuses: Boolean = true
  ): List[ConsignmentStatuses] = {

    val statusesToGenerate = if (includeDefaultStatuses) { statuses ++ defaultStatuses }
    else { statuses }

    statusesToGenerate
      .map(s => {
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, s._1.id, s._2.value, someDateTime, modifiedDateTime)
      })
      .toList
  }
}
