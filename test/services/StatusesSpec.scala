package services

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import services.Statuses._

class StatusesSpec extends AnyWordSpec with MockitoSugar {
  "StatusTypes" should {
    "have the correct field values" in {
      ClientChecksType.id should equal("ClientChecks")
      ClientChecksType.nonJudgmentStatus shouldBe false
      ClientChecksType.fileCheckStatus shouldBe false

      ConfirmTransferType.id should equal("ConfirmTransfer")
      ConfirmTransferType.nonJudgmentStatus shouldBe true
      ConfirmTransferType.fileCheckStatus shouldBe false

      DraftMetadataType.id should equal("DraftMetadata")
      DraftMetadataType.nonJudgmentStatus shouldBe true
      DraftMetadataType.fileCheckStatus shouldBe false

      ExportType.id should equal("Export")
      ExportType.nonJudgmentStatus shouldBe false
      ExportType.fileCheckStatus shouldBe false

      MetadataReviewType.id should equal("MetadataReview")
      MetadataReviewType.nonJudgmentStatus shouldBe true
      MetadataReviewType.fileCheckStatus shouldBe false

      SeriesType.id should equal("Series")
      SeriesType.nonJudgmentStatus shouldBe true
      SeriesType.fileCheckStatus shouldBe false

      ServerAntivirusType.id should equal("ServerAntivirus")
      ServerAntivirusType.nonJudgmentStatus shouldBe false
      ServerAntivirusType.fileCheckStatus shouldBe true

      ServerChecksumType.id should equal("ServerChecksum")
      ServerChecksumType.nonJudgmentStatus shouldBe false
      ServerChecksumType.fileCheckStatus shouldBe true

      ServerFFIDType.id should equal("ServerFFID")
      ServerFFIDType.nonJudgmentStatus shouldBe false
      ServerFFIDType.fileCheckStatus shouldBe true

      ServerRedactionType.id should equal("ServerRedaction")
      ServerRedactionType.nonJudgmentStatus shouldBe false
      ServerRedactionType.fileCheckStatus shouldBe true

      TransferAgreementType.id should equal("TransferAgreement")
      TransferAgreementType.nonJudgmentStatus shouldBe true
      TransferAgreementType.fileCheckStatus shouldBe false

      UnrecognisedType.id should equal("Unrecognised")
      UnrecognisedType.nonJudgmentStatus shouldBe false
      UnrecognisedType.fileCheckStatus shouldBe false

      UploadType.id should equal("Upload")
      UploadType.nonJudgmentStatus shouldBe false
      UploadType.fileCheckStatus shouldBe false
    }
  }

  "StatusValues" should {
    "have the correct value" in {
      CompletedValue.value should equal("Completed")
      CompletedWithIssuesValue.value should equal("CompletedWithIssues")
      FailedValue.value should equal("Failed")
      InProgressValue.value should equal("InProgress")
    }
  }

  "toStatusType" should {
    "return the correct 'status type' based on input string" in {
      toStatusType("ClientChecks") shouldBe ClientChecksType
      toStatusType("ConfirmTransfer") shouldBe ConfirmTransferType
      toStatusType("DraftMetadata") shouldBe DraftMetadataType
      toStatusType("Export") shouldBe ExportType
      toStatusType("MetadataReview") shouldBe MetadataReviewType
      toStatusType("Series") shouldBe SeriesType
      toStatusType("ServerAntivirus") shouldBe ServerAntivirusType
      toStatusType("ServerChecksum") shouldBe ServerChecksumType
      toStatusType("ServerFFID") shouldBe ServerFFIDType
      toStatusType("ServerRedaction") shouldBe ServerRedactionType
      toStatusType("someRandomValue") shouldBe UnrecognisedType
      toStatusType("TransferAgreement") shouldBe TransferAgreementType
      toStatusType("Upload") shouldBe UploadType
    }
  }
}
