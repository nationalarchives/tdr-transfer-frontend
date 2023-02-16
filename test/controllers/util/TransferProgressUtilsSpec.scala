package controllers.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import testUtils.ConsignmentStatusesOptions
import TransferProgressUtils.{ExportFailed, TransferState}

import java.util.UUID

class TransferProgressUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {

  private val transferProgressUtils = new TransferProgressUtils()

  "toTransferState" should "return the correct standard 'transfer state' based on a consignment statuses and type" in {
    ConsignmentStatusesOptions.expectedStandardStatesAndStatuses.foreach(s => {
      val currentStatus = s._2
      transferProgressUtils.toTransferState(currentStatus, "standard") shouldBe s._1
    })
  }

  "toTransferState" should "return the correct judgment 'transfer state' based on a consignment statuses and type" in {
    ConsignmentStatusesOptions.expectedJudgmentStatesAndStatuses.foreach(s => {
      val currentStatus = s._2
      transferProgressUtils.toTransferState(currentStatus, "judgment") shouldBe s._1
    })
  }

  "transferStateToStandardAction" should "generate the correct 'user action' values based on the 'transfer state' for a standard consignment" in {
    ConsignmentStatusesOptions.expectedStandardStatesAndStatuses.foreach(s => {
      val transferState = s._1
      val consignmentId = UUID.randomUUID()
      val expectedValues = ConsignmentStatusesOptions.transferStateToExpectedStandardAction(transferState)
      val userAction = transferProgressUtils.transferStateToStandardAction(transferState, consignmentId, "TEST-TDR-2023-X", "standard")
      userAction.actionUrl.contains("judgment") shouldBe false
      userAction.actionUrl.contains(expectedValues._1) shouldBe true
      userAction.transferStatus should equal(expectedValues._2)
      userAction.actionText should equal(expectedValues._3)
    })
  }

  "transferStateToStandardAction" should "generate the correct 'user action' values based on the 'transfer state' for a judgment consignment" in {
    ConsignmentStatusesOptions.expectedJudgmentStatesAndStatuses.foreach(s => {
      val transferState: TransferState = s._1
      val consignmentId = UUID.randomUUID()
      val expectedValues = ConsignmentStatusesOptions.transferStateToExpectedJudgmentAction(transferState)
      val userAction = transferProgressUtils.transferStateToStandardAction(transferState, consignmentId, "TEST-TDR-2023-X", "judgment")
      val containsJudgment = transferState match {
        case ExportFailed => false
        case _            => true
      }
      userAction.actionUrl.contains("judgment") shouldBe containsJudgment
      userAction.actionUrl.endsWith(expectedValues._1) shouldBe true
      userAction.transferStatus should equal(expectedValues._2)
      userAction.actionText should equal(expectedValues._3)
    })
  }
}
