package services

object Statuses {

  sealed trait StatusType { val id: String }

  sealed trait StatusValue { val value: String }

  case object SeriesType extends StatusType { val id: String = "Series" }

  case object UploadType extends StatusType { val id: String = "Upload" }

  case object TransferAgreementType extends StatusType { val id: String = "TransferAgreement" }

  case object ExportType extends StatusType { val id: String = "Export" }

  case object CompletedValue extends StatusValue { val value: String = "Completed" }

  case object InProgressValue extends StatusValue { val value: String = "InProgress" }

  case object FailedValue extends StatusValue { val value: String = "Failed" }
}
