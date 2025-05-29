package services

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import services.Statuses._

class StatusesSpec extends AnyWordSpec with MockitoSugar {

  "StatusTypes" should {
    "have the correct field values" in {
      Statuses.SeriesType.id should equal("Series")
      Statuses.SeriesType.nonJudgmentStatus shouldBe true

      Statuses.TransferAgreementType.id should equal("TransferAgreement")
      Statuses.TransferAgreementType.nonJudgmentStatus shouldBe true

      Statuses.ClientChecksType.id should equal("ClientChecks")
      Statuses.ClientChecksType.nonJudgmentStatus shouldBe false

      Statuses.UploadType.id should equal("Upload")
      Statuses.UploadType.nonJudgmentStatus shouldBe false

      Statuses.ServerAntivirusType.id should equal("ServerAntivirus")
      Statuses.ServerAntivirusType.nonJudgmentStatus shouldBe false

      Statuses.ServerChecksumType.id should equal("ServerChecksum")
      Statuses.ServerChecksumType.nonJudgmentStatus shouldBe false

      Statuses.ServerFFIDType.id should equal("ServerFFID")
      Statuses.ServerFFIDType.nonJudgmentStatus shouldBe false

      Statuses.ConfirmTransferType.id should equal("ConfirmTransfer")
      Statuses.ConfirmTransferType.nonJudgmentStatus shouldBe true

      Statuses.ExportType.id should equal("Export")
      Statuses.ExportType.nonJudgmentStatus shouldBe false

      Statuses.UnrecognisedType.id should equal("Unrecognised")
      Statuses.UnrecognisedType.nonJudgmentStatus shouldBe false
    }
  }

  "StatusValues" should {
    "have the correct value" in {
      Statuses.CompletedValue.value should equal("Completed")
      Statuses.FailedValue.value should equal("Failed")
      Statuses.CompletedWithIssuesValue.value should equal("CompletedWithIssues")
      Statuses.InProgressValue.value should equal("InProgress")
    }
  }

  "toStatusType" should {
    "return the correct 'status type' based on input string" in {
      Statuses.toStatusType("Series") shouldBe SeriesType
      Statuses.toStatusType("TransferAgreement") shouldBe TransferAgreementType
      Statuses.toStatusType("ClientChecks") shouldBe ClientChecksType
      Statuses.toStatusType("Upload") shouldBe UploadType
      Statuses.toStatusType("ServerAntivirus") shouldBe ServerAntivirusType
      Statuses.toStatusType("ServerChecksum") shouldBe ServerChecksumType
      Statuses.toStatusType("ServerFFID") shouldBe ServerFFIDType
      Statuses.toStatusType("ConfirmTransfer") shouldBe ConfirmTransferType
      Statuses.toStatusType("Export") shouldBe ExportType
      Statuses.toStatusType("someRandomValue") shouldBe UnrecognisedType
    }
  }
}
