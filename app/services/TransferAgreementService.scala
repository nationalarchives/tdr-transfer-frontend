package services

import controllers.{TransferAgreementData, TransferAgreementPart2Data}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementService @Inject() (val dynamoService: DynamoService)(implicit val ec: ExecutionContext) {
  def addTransferAgreementPart1(consignmentId: UUID, formData: TransferAgreementData): Future[Unit] = {
    for {
      _ <- dynamoService.setFieldToValue(consignmentId, "publicRecord", formData.publicRecord)
      _ <- dynamoService.setFieldToValue(consignmentId, "crownCopyright", formData.crownCopyright)
      _ <- dynamoService.setFieldToValue(consignmentId, "english", formData.english)
      _ <- dynamoService.setFieldToValue(consignmentId, "status_TransferAgreement", "InProgress")
    } yield()
  }

  def addTransferAgreementPart2(consignmentId: UUID, formData: TransferAgreementPart2Data): Future[Unit] = {
    for {
      _ <- dynamoService.setFieldToValue(consignmentId, "droAppraisalSelection", formData.droAppraisalSelection)
      _ <- dynamoService.setFieldToValue(consignmentId, "droSensitivity", formData.droSensitivity)
      _ <- dynamoService.setFieldToValue(consignmentId, "openRecords", formData.openRecords.getOrElse(""))
      _ <- dynamoService.setFieldToValue(consignmentId, "status_TransferAgreement", "Completed")
    } yield()
  }
}
