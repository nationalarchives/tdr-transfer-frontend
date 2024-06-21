package services

import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results.Redirect
import services.Statuses.{
  ClientChecksType,
  CompletedValue,
  CompletedWithIssuesValue,
  DTAReviewType,
  InProgressValue,
  MetadataType,
  PageCorrelating,
  SeriesType,
  StatusType,
  StatusValue,
  TransferAgreementType,
  UploadType
}

import java.time.ZonedDateTime
import java.util.UUID

class StatusControlPOCSpec extends AnyWordSpec with MockitoSugar {

  case class TestController()
  val dummyConsignmentId: UUID = UUID.randomUUID()
  def toDummyConsignmentStatuses(statusMap: Map[StatusType, StatusValue]): Seq[ConsignmentStatuses] =
    statusMap.map { case (statusType, statusValue) =>
      ConsignmentStatuses(
        consignmentStatusId = UUID.randomUUID(),
        consignmentId = dummyConsignmentId,
        statusType = statusType.id,
        value = statusValue.value,
        createdDatetime = ZonedDateTime.now(),
        modifiedDatetime = Some(ZonedDateTime.now())
      )
    }.toSeq

  "A status aware controller with the DTA review page status" should {
    val controller =
      new TestController with StatusAware { val pageStatus: StatusType with PageCorrelating = DTAReviewType }

    "redirect to the transfer agreement type base route when only series type has been completed" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(SeriesType -> CompletedValue))
      controller.statusAwareRedirect(dummyConsignmentId, recordedStatuses) shouldBe
        Some(Redirect(TransferAgreementType.baseRoute(dummyConsignmentId)))
    }
    "redirect to the upload type base route when the series type and transfer agreement have been completed" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(SeriesType -> CompletedValue, TransferAgreementType -> CompletedValue))
      controller.statusAwareRedirect(dummyConsignmentId, recordedStatuses) shouldBe
        Some(Redirect(UploadType.baseRoute(dummyConsignmentId)))
    }
    "redirect to the client checks type base route when the series type, transfer agreement and upload have been completed" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(SeriesType -> CompletedValue, TransferAgreementType -> CompletedValue, UploadType -> CompletedValue))
      controller.statusAwareRedirect(dummyConsignmentId, recordedStatuses) shouldBe
        Some(Redirect(ClientChecksType.baseRoute(dummyConsignmentId)))
    }
    "redirect to the metadata type base route when the series type, transfer agreement, upload and client checks have been completed" in {
      val recordedStatuses =
        toDummyConsignmentStatuses(Map(SeriesType -> CompletedValue, TransferAgreementType -> CompletedValue, UploadType -> CompletedValue, ClientChecksType -> CompletedValue))
      controller.statusAwareRedirect(dummyConsignmentId, recordedStatuses) shouldBe
        Some(Redirect(MetadataType.baseRoute(dummyConsignmentId)))
    }
    "give no redirect when the series type, transfer agreement, upload, client checks and metadata entry have been completed" in {
      val recordedStatuses = toDummyConsignmentStatuses(
        Map(
          SeriesType -> CompletedValue,
          TransferAgreementType -> CompletedValue,
          UploadType -> CompletedValue,
          ClientChecksType -> CompletedValue,
          MetadataType -> CompletedValue
        )
      )
      controller.statusAwareRedirect(dummyConsignmentId, recordedStatuses) shouldBe None
    }
  }

  "A status aware controller with the Metadata page status" should {
    val controller =
      new TestController with StatusAware { val pageStatus: StatusType with PageCorrelating = MetadataType }
    "not be interactable when DTA review is in progress" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(DTAReviewType -> InProgressValue))
      controller.shouldBeInteractable(recordedStatuses) should equal(false)
    }
    "not be interactable when DTA review is completed" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(DTAReviewType -> CompletedValue))
      controller.shouldBeInteractable(recordedStatuses) should equal(false)
    }
    "be interactable when DTA review has been rejected" in {
      val recordedStatuses = toDummyConsignmentStatuses(Map(DTAReviewType -> CompletedWithIssuesValue))
      controller.shouldBeInteractable(recordedStatuses) should equal(true)
    }
    "be interactable when no DTA review status is present" in {
      val recordedStatuses = Seq.empty[ConsignmentStatuses]
      controller.shouldBeInteractable(recordedStatuses) should equal(true)
    }
  }
}
