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

      ConfirmTransferType.id should equal("ConfirmTransfer")
      ConfirmTransferType.nonJudgmentStatus shouldBe true

      DraftMetadataType.id should equal("DraftMetadata")
      DraftMetadataType.nonJudgmentStatus shouldBe true

      ExportType.id should equal("Export")
      ExportType.nonJudgmentStatus shouldBe false

      MetadataReviewType.id should equal("MetadataReview")
      MetadataReviewType.nonJudgmentStatus shouldBe true

      SeriesType.id should equal("Series")
      SeriesType.nonJudgmentStatus shouldBe true

      ServerAntivirusType.id should equal("ServerAntivirus")
      ServerAntivirusType.nonJudgmentStatus shouldBe false

      ServerChecksumType.id should equal("ServerChecksum")
      ServerChecksumType.nonJudgmentStatus shouldBe false

      ServerFFIDType.id should equal("ServerFFID")
      ServerFFIDType.nonJudgmentStatus shouldBe false

      ServerRedactionType.id should equal("ServerRedaction")
      ServerRedactionType.nonJudgmentStatus shouldBe false

      TransferAgreementType.id should equal("TransferAgreement")
      TransferAgreementType.nonJudgmentStatus shouldBe true

      UnrecognisedType.id should equal("Unrecognised")
      UnrecognisedType.nonJudgmentStatus shouldBe false

      UploadType.id should equal("Upload")
      UploadType.nonJudgmentStatus shouldBe false
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

  "clientChecksStatuses" should {
    "contain the correct status types" in {
      clientChecksStatuses.size shouldBe 5
      clientChecksStatuses.contains(ClientChecksType) shouldBe true
      clientChecksStatuses.contains(ServerAntivirusType) shouldBe true
      clientChecksStatuses.contains(ServerChecksumType) shouldBe true
      clientChecksStatuses.contains(ServerFFIDType) shouldBe true
      clientChecksStatuses.contains(ServerRedactionType) shouldBe true
    }
  }
}
