package services

import controllers.routes
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import play.api.mvc.Call
import services.ConsignmentStatusService.statusValue

import java.util.UUID

object Statuses {

  val SEQUENCE: Seq[StatusType] =
    Seq(SeriesType, TransferAgreementType, UploadType, ClientChecksType, MetadataType, MetadataReviewType, ConfirmTransferType)

  sealed trait StatusType {
    val id: String
    val nonJudgmentStatus: Boolean
    lazy val dependencies: Set[StatusType] = SEQUENCE.takeWhile(s => s != this).toSet
  }

  sealed trait PageCorrelating { val baseRoute: UUID => Call }

  sealed trait StatusValue {
    val value: String
  }

  object StatusValue {
    def apply(id: String): StatusValue = id match {
      case EnteredValue.value             => EnteredValue
      case NotEnteredValue.value          => NotEnteredValue
      case IncompleteValue.value          => IncompleteValue
      case CompletedWithIssuesValue.value => CompletedWithIssuesValue
      case CompletedValue.value           => CompletedValue
      case InProgressValue.value          => InProgressValue
      case FailedValue.value              => FailedValue
    }
  }

  case object SeriesType extends StatusType with PageCorrelating {
    val id: String = "Series"
    val nonJudgmentStatus: Boolean = true
    val baseRoute: UUID => Call = routes.SeriesDetailsController.seriesDetails
  }

  case object UploadType extends StatusType with PageCorrelating {
    val id: String = "Upload"
    val nonJudgmentStatus: Boolean = false
    val baseRoute: UUID => Call = routes.UploadController.uploadPage
  }

  case object TransferAgreementType extends StatusType with PageCorrelating {
    val id: String = "TransferAgreement"
    val nonJudgmentStatus: Boolean = true
    val baseRoute: UUID => Call = routes.TransferAgreementPart1Controller.transferAgreement
  }

  case object ClientChecksType extends StatusType with PageCorrelating {
    val id: String = "ClientChecks"
    val nonJudgmentStatus: Boolean = false
    val baseRoute: UUID => Call = routes.FileChecksController.fileCheckProgress
  }

  case object ServerAntivirusType extends StatusType {
    val id: String = "ServerAntivirus"
    val nonJudgmentStatus: Boolean = false
  }

  case object ServerChecksumType extends StatusType {
    val id: String = "ServerChecksum"
    val nonJudgmentStatus: Boolean = false
  }

  case object ServerFFIDType extends StatusType {
    val id: String = "ServerFFID"
    val nonJudgmentStatus: Boolean = false
  }

  case object ConfirmTransferType extends StatusType with PageCorrelating {
    val id: String = "ConfirmTransfer"
    val nonJudgmentStatus: Boolean = true
    val baseRoute: UUID => Call = routes.ConfirmTransferController.confirmTransfer
  }

  case object ExportType extends StatusType {
    val id: String = "Export"
    val nonJudgmentStatus: Boolean = false
  }

  case object UnrecognisedType extends StatusType {
    val id: String = "Unrecognised"
    val nonJudgmentStatus: Boolean = false
  }

  case object DescriptiveMetadataType extends StatusType {
    val id: String = "DescriptiveMetadata"
    val nonJudgmentStatus: Boolean = true
  }

  case object ClosureMetadataType extends StatusType {
    val id: String = "ClosureMetadata"
    val nonJudgmentStatus: Boolean = true
  }

  case object DraftMetadataType extends StatusType {
    val id: String = "DraftMetadata"
    val nonJudgmentStatus: Boolean = true
    val baseRoute: UUID => Call = routes.AdditionalMetadataEntryMethodController.additionalMetadataEntryMethodPage
  }

  case object MetadataType extends StatusType with PageCorrelating {
    val id: String = "Metadata"
    val nonJudgmentStatus: Boolean = true
    val baseRoute: UUID => Call = routes.AdditionalMetadataEntryMethodController.additionalMetadataEntryMethodPage

    def value(metadataStatuses: List[ConsignmentStatuses]): StatusValue = {
      val combine: (StatusValue, StatusValue) => StatusValue = (a, b) => {
        if (a == CompletedValue && b == CompletedValue) CompletedValue
        else if (a == InProgressValue || b == InProgressValue) InProgressValue
        else if (a == CompletedWithIssuesValue || b == CompletedWithIssuesValue) CompletedWithIssuesValue
        else NotEnteredValue
      }
      combine(
        combine(
          statusValue(DescriptiveMetadataType)(metadataStatuses),
          statusValue(ClosureMetadataType)(metadataStatuses)
        ),
        statusValue(DraftMetadataType)(metadataStatuses)
      )
    }
  }
  
  case object MetadataReviewType extends StatusType with PageCorrelating {
    val id: String = "MetadataReview"
    val nonJudgmentStatus: Boolean = true
    lazy val baseRoute: UUID => Call = ???
  }

  case object EnteredValue extends StatusValue { val value: String = "Entered" }

  case object NotEnteredValue extends StatusValue { val value: String = "NotEntered" }

  case object IncompleteValue extends StatusValue { val value: String = "Incomplete" }

  case object CompletedValue extends StatusValue { val value: String = "Completed" }

  case object CompletedWithIssuesValue extends StatusValue { val value: String = "CompletedWithIssues" }

  case object InProgressValue extends StatusValue { val value: String = "InProgress" }

  case object FailedValue extends StatusValue { val value: String = "Failed" }

  def toStatusType(statusType: String): StatusType = {
    statusType match {
      case ExportType.id              => ExportType
      case ConfirmTransferType.id     => ConfirmTransferType
      case ServerFFIDType.id          => ServerFFIDType
      case ServerChecksumType.id      => ServerChecksumType
      case ServerAntivirusType.id     => ServerAntivirusType
      case ClientChecksType.id        => ClientChecksType
      case UploadType.id              => UploadType
      case TransferAgreementType.id   => TransferAgreementType
      case SeriesType.id              => SeriesType
      case DescriptiveMetadataType.id => DescriptiveMetadataType
      case ClosureMetadataType.id     => ClosureMetadataType
      case DraftMetadataType.id       => DraftMetadataType
      case MetadataReviewType.id      => MetadataReviewType
      case _                          => UnrecognisedType
    }
  }
}
