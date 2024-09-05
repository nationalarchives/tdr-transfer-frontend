package services

object Statuses {

  sealed trait StatusType {
    val id: String
    val nonJudgmentStatus: Boolean
  }

  sealed trait StatusValue { val value: String }

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
  case object SeriesType extends StatusType {
    val id: String = "Series"
    val nonJudgmentStatus: Boolean = true
  }

  case object UploadType extends StatusType {
    val id: String = "Upload"
    val nonJudgmentStatus: Boolean = false
  }

  case object TransferAgreementType extends StatusType {
    val id: String = "TransferAgreement"
    val nonJudgmentStatus: Boolean = true
  }

  case object ClientChecksType extends StatusType {
    val id: String = "ClientChecks"
    val nonJudgmentStatus: Boolean = false
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

  case object ConfirmTransferType extends StatusType {
    val id: String = "ConfirmTransfer"
    val nonJudgmentStatus: Boolean = true
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
  }

  case object MetadataReviewType extends StatusType {
    val id: String = "MetadataReview"
    val nonJudgmentStatus: Boolean = true
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
