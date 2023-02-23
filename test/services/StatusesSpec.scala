package services

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class StatusesSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  "StatusTypes" should {
    "have the correct id" in {
      Statuses.SeriesType.id should equal("Series")
      Statuses.UploadType.id should equal("Upload")
      Statuses.TransferAgreementType.id should equal("TransferAgreement")
      Statuses.ExportType.id should equal("Export")
    }
  }

  "StatusValues" should {
    "have the correct value" in {
      Statuses.CompletedValue.value should equal("Completed")
      Statuses.FailedValue.value should equal("Failed")
      Statuses.InProgressValue.value should equal("InProgress")
    }
  }
}
