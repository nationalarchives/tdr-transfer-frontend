package services

object Statuses {

  sealed trait StatusType {
    val id: String
    val nonJudgmentStatus: Boolean
  }

  sealed trait StatusValue { val value: String }

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

  case object CompletedValue extends StatusValue { val value: String = "Completed" }

  case object CompletedWithIssuesValue extends StatusValue { val value: String = "CompletedWithIssues" }

  case object InProgressValue extends StatusValue { val value: String = "InProgress" }

  case object FailedValue extends StatusValue { val value: String = "Failed" }

  def toStatusType(statusType: String): StatusType = {
    statusType match {
      case ExportType.id            => ExportType
      case ConfirmTransferType.id   => ConfirmTransferType
      case ServerFFIDType.id        => ServerFFIDType
      case ServerChecksumType.id    => ServerChecksumType
      case ServerAntivirusType.id   => ServerAntivirusType
      case ClientChecksType.id      => ClientChecksType
      case UploadType.id            => UploadType
      case TransferAgreementType.id => TransferAgreementType
      case SeriesType.id            => SeriesType
      case _                        => UnrecognisedType
    }
  }
}
