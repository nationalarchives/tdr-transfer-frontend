package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.DateUtils
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.ConsignmentStatuses
import graphql.codegen.types.ConsignmentOrderField.CreatedAtTimestamp
import graphql.codegen.types.Direction.Descending
import graphql.codegen.types.{ConsignmentFilters, ConsignmentOrderBy}
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService
import services.Statuses._
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.SkippedValue

import java.util.UUID
import javax.inject.Inject

class ViewTransfersController @Inject() (
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  private val statusColours: Map[String, String] =
    Map(InProgress.value -> "yellow", InReview.value -> "yellow", Failed.value -> "red", ContactUs.value -> "red", Transferred.value -> "green")

  implicit class ConsignmentStatusesHelper(statuses: List[ConsignmentStatuses]) {
    def containsStatuses(statusTypes: StatusType*): Boolean = {
      statusTypes.foldLeft(false)((contains, statusType) => contains || statuses.exists(_.statusType == statusType.id))
    }

    def statusValue(statusType: StatusType): Option[String] = {
      statuses.find(_.statusType == statusType.id).map(_.value)
    }

    def filterNonJudgmentStatuses: List[ConsignmentStatuses] = {
      statuses.map(s => toStatusType(s.statusType) -> s).filterNot(_._1.nonJudgmentStatus).map(_._2)
    }
  }

  def viewConsignments(pageNumber: Int = 1): Action[AnyContent] = standardUserAction { implicit request: Request[AnyContent] =>
    val consignmentFilters = ConsignmentFilters(Some(request.token.userId), None)
    val orderBy = ConsignmentOrderBy(CreatedAtTimestamp, Descending)
    for {
      consignmentTransfers <- consignmentService.getConsignments(
        pageNumber - 1,
        applicationConfig.numberOfItemsOnViewTransferPage,
        consignmentFilters,
        orderBy,
        request.token.bearerAccessToken
      )
      consignments = consignmentTransfers.edges match {
        case Some(edges) => edges.flatMap(createView)
        case None        => Nil
      }
    } yield Ok(
      views.html.viewTransfers(consignments, pageNumber, consignmentTransfers.totalPages.getOrElse(1), request.token.name, request.token.email, request.token.isJudgmentUser)
    )
  }

  private def createView(edges: Option[Edges]): Option[ConsignmentTransfers] =
    edges.map { edge =>
      val userAction: UserAction = toUserAction(edge.node)

      ConsignmentTransfers(
        edge.node.consignmentid,
        edge.node.consignmentReference,
        userAction.transferStatus,
        statusColours(userAction.transferStatus),
        userAction,
        edge.node.exportDatetime.map(edt => DateUtils.format(edt, "dd/MM/yyyy HH:mm")).getOrElse("N/A"),
        edge.node.createdDatetime.map(cdt => DateUtils.format(cdt, "dd/MM/yyyy HH:mm")).getOrElse(""),
        edge.node.totalFiles
      )
    }

  private def toUserAction(consignment: Node): UserAction = {
    val judgmentType = consignment.consignmentType.contains("judgment")
    val consignmentId = consignment.consignmentid.get
    val consignmentRef = consignment.consignmentReference
    val statuses = consignment.consignmentStatuses

    val statusesToCheck: List[ConsignmentStatuses] = if (judgmentType) {
      statuses.filterNonJudgmentStatuses
    } else {
      statuses
    }

    statusesToCheck match {
      case s if s.containsStatuses(ExportType) =>
        ExportAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s, consignmentReference = Some(consignmentRef))
      case s if s.statusValue(ConfirmTransferType).contains(CompletedValue.value) => ConfirmTransferAction.validateStatuses(consignmentId, statuses = s)
      case s if s.containsStatuses(MetadataReviewType)                            => MetadataReviewAction.validateStatuses(consignmentId, statuses = s)
      case s if s.containsStatuses(DraftMetadataType)                             =>
        DraftMetadataAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case s if s.containsStatuses(ServerAntivirusType, ServerChecksumType, ServerFFIDType) =>
        FileChecksAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case s if s.containsStatuses(ClientChecksType, UploadType) =>
        ClientSideChecksAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case s if s.containsStatuses(TransferAgreementType) =>
        TransferAgreementAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case s if s.containsStatuses(SeriesType) =>
        SeriesAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case s if s.isEmpty => StartAction.validateStatuses(consignmentId, judgmentTransfer = Some(judgmentType), statuses = s)
      case _              => ContactUsAction.validateStatuses(consignmentId, consignmentReference = Some(consignmentRef), statuses = Nil)
    }
  }
}

case class ConsignmentTransfers(
    consignmentId: Option[UUID],
    reference: String,
    status: String,
    statusColour: String,
    userAction: UserAction,
    dateOfTransfer: String,
    dateStarted: String,
    numberOfFiles: Int
)

trait StatusAction {
  val transferStatusDefault: TransferStatus
  val actionTextDefault: ActionText
  val requiredCompletedStatuses: List[StatusType]

  private def generateMissingStatusUserActions(consignmentId: UUID): Map[StatusType, UserAction] =
    Map(
      SeriesType -> UserAction(InProgress.value, routes.SeriesDetailsController.seriesDetails(consignmentId).url, Resume.value)
    )

  protected def findActionStatus(actionStatus: StatusType, statuses: List[ConsignmentStatuses]): Option[ConsignmentStatuses] = {
    statuses.find(_.statusType == actionStatus.id)
  }

  protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String] = None
  ): UserAction

  def validateStatuses(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String] = None
  ): UserAction = {

    val defaultUserActions = generateMissingStatusUserActions(consignmentId)
    val statusesPresent = statuses.map(_.statusType)
    val statusesRequired = requiredCompletedStatuses.map(_.id)

    val missingStatuses = statusesRequired.diff(statusesPresent)

    missingStatuses match {
      case _ if missingStatuses.contains(SeriesType.id) =>
        defaultUserActions(SeriesType)
      case _ => toUserAction(consignmentId, statuses, judgmentTransfer, consignmentReference)
    }
  }
}

case object ConfirmTransferAction extends StatusAction {
  override val transferStatusDefault: TransferStatus = InProgress
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String] = None
  ): UserAction = {
    UserAction(transferStatusDefault.value, routes.TransferCompleteController.transferComplete(consignmentId).url, actionTextDefault.value)
  }
}

case object MetadataReviewAction extends StatusAction {
  override val transferStatusDefault: TransferStatus = InReview
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String] = None
  ): UserAction = {
    UserAction(transferStatusDefault.value, routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId).url, actionTextDefault.value)
  }
}

case object TransferAgreementAction extends StatusAction {
  private val actionStatus: StatusType = TransferAgreementType
  override val transferStatusDefault: TransferStatus = InProgress
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String]
  ): UserAction = {
    val transferAgreementStatus = statuses.find(_.statusType == actionStatus.id).get

    val url = transferAgreementStatus.value match {
      case v if v == InProgressValue.value => routes.TransferAgreementPart2Controller.transferAgreement(consignmentId).url
      case _                               => routes.UploadController.uploadPage(consignmentId).url
    }

    UserAction(transferStatusDefault.value, url, actionTextDefault.value)
  }
}

case object SeriesAction extends StatusAction {
  private val actionStatus: StatusType = SeriesType
  override val transferStatusDefault: TransferStatus = InProgress
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String]
  ): UserAction = {
    val seriesStatus = findActionStatus(actionStatus, statuses).get

    seriesStatus.value match {
      case v if v == InProgressValue.value =>
        UserAction(transferStatusDefault.value, routes.SeriesDetailsController.seriesDetails(consignmentId).url, actionTextDefault.value)
      case _ =>
        UserAction(transferStatusDefault.value, routes.TransferAgreementPart1Controller.transferAgreement(consignmentId).url, actionTextDefault.value)
    }
  }
}

case object ExportAction extends StatusAction {
  private val actionStatus: StatusType = ExportType
  override val transferStatusDefault: TransferStatus = Transferred
  override val actionTextDefault: ActionText = Download
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String]
  ): UserAction = {

    val (url, actionText) = judgmentTransfer match {
      case Some(value) if value => (routes.TransferCompleteController.judgmentTransferComplete(consignmentId).url, View.value)
      case _                    => (routes.DownloadMetadataController.downloadMetadataFile(consignmentId, None).url, actionTextDefault.value)
    }

    val exportStatus = findActionStatus(actionStatus, statuses).get

    exportStatus.value match {
      // Even though export is InProgress once a user clicks export there is nothing else they can do, hence setting the status to transferred
      case InProgressValue.value | CompletedValue.value => UserAction(transferStatusDefault.value, url, actionText)
      case FailedValue.value                            =>
        UserAction(Failed.value, s"""mailto:%s?subject=Ref: ${consignmentReference.get} - Export failure""", ContactUs.value)
      case _ => ContactUsAction.toUserAction(consignmentId, consignmentReference = consignmentReference)
    }
  }
}

case object DraftMetadataAction extends StatusAction {
  private val actionStatus: StatusType = DraftMetadataType
  override val transferStatusDefault: TransferStatus = InProgress
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String]
  ): UserAction = {
    val draftMetadataStatus = findActionStatus(actionStatus, statuses).get
    draftMetadataStatus.value match {
      case CompletedValue.value | SkippedValue.value =>
        UserAction(transferStatusDefault.value, routes.DownloadMetadataController.downloadMetadataPage(consignmentId).url, actionTextDefault.value)
      case CompletedWithIssuesValue.value =>
        UserAction(
          transferStatusDefault.value,
          routes.DraftMetadataChecksResultsController.draftMetadataChecksResultsPage(consignmentId).url,
          actionTextDefault.value
        )
      case _ => UserAction(transferStatusDefault.value, routes.PrepareMetadataController.prepareMetadata(consignmentId).url, actionTextDefault.value)
    }
  }
}

case object FileChecksAction extends StatusAction {
  override val transferStatusDefault: TransferStatus = InProgress
  override val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  protected def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String]
  ): UserAction = {
    val (checksUrl, resultsUrl) = judgmentTransfer match {
      case Some(value) if value =>
        (
          routes.FileChecksController.judgmentFileChecksPage(consignmentId, None).url,
          routes.FileChecksResultsController.judgmentFileCheckResultsPage(consignmentId, None).url
        )
      case _ =>
        (routes.FileChecksController.fileChecksPage(consignmentId, None).url, routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url)
    }

    val fileChecksStatuses: List[String] = statuses
      .map(s => toStatusType(s.statusType) -> s)
      .filter(_._1.fileCheckStatus)
      .map(_._2)
      .map(_.value)

    fileChecksStatuses match {
      case fcs if fcs.contains(FailedValue.value) || fcs.contains(CompletedWithIssuesValue.value) =>
        UserAction(Failed.value, resultsUrl, Errors.value)
      case fcs if fcs.contains(InProgressValue.value) || fcs.size < 4 =>
        UserAction(transferStatusDefault.value, checksUrl, actionTextDefault.value)
      case _ =>
        UserAction(transferStatusDefault.value, resultsUrl, actionTextDefault.value)
    }
  }
}

case object ClientSideChecksAction extends StatusAction {
  val transferStatusDefault: TransferStatus = InProgress
  val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  override def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String] = None
  ): UserAction = {
    val (uploadUrl, checksUrl) = judgmentTransfer match {
      case Some(value) if value =>
        (
          routes.UploadController.judgmentUploadPage(consignmentId).url,
          routes.FileChecksController.judgmentFileChecksPage(consignmentId, None).url
        )
      case _ =>
        (
          routes.UploadController.uploadPage(consignmentId).url,
          routes.FileChecksController.fileChecksPage(consignmentId, None).url
        )
    }

    val checkStatuses = statuses.filter(s => s.statusType == ClientChecksType.id || s.statusType == UploadType.id)
    val checkValues = checkStatuses.map(_.value)
    val abandoned = checkValues.size == 2 && checkValues.forall(_ == InProgressValue.value) && checkStatuses.flatMap(_.modifiedDatetime).isEmpty

    checkValues match {
      case csc if csc.contains(FailedValue.value) || csc.contains(CompletedWithIssuesValue.value) || abandoned =>
        UserAction(Failed.value, uploadUrl, Errors.value)
      case csc if csc.contains(InProgressValue.value) || csc.size < 2 =>
        UserAction(InProgress.value, uploadUrl, Resume.value)
      case _ =>
        UserAction(InProgress.value, checksUrl, Resume.value)
    }
  }
}
case object StartAction extends StatusAction {
  val transferStatusDefault: TransferStatus = InProgress
  val actionTextDefault: ActionText = Resume
  override val requiredCompletedStatuses: List[StatusType] = Nil

  def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses] = Nil,
      judgmentTransfer: Option[Boolean],
      consignmentReference: Option[String] = None
  ): UserAction = {
    val startUrl = judgmentTransfer match {
      case Some(value) if value => routes.BeforeUploadingController.beforeUploading(consignmentId).url
      case _                    => routes.SeriesDetailsController.seriesDetails(consignmentId).url
    }
    UserAction(transferStatusDefault.value, startUrl, actionTextDefault.value)
  }

  override def validateStatuses(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses],
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String] = None
  ): UserAction = {
    judgmentTransfer match {
      case Some(value) if value => toUserAction(consignmentId, judgmentTransfer = judgmentTransfer)
      case _                    => super.validateStatuses(consignmentId, statuses, judgmentTransfer, consignmentReference)
    }
  }
}

case object ContactUsAction extends StatusAction {
  val transferStatusDefault: TransferStatus = ContactUs
  val actionTextDefault: ActionText = ContactUs
  override val requiredCompletedStatuses: List[StatusType] = Nil

  def toUserAction(
      consignmentId: UUID,
      statuses: List[ConsignmentStatuses] = Nil,
      judgmentTransfer: Option[Boolean] = None,
      consignmentReference: Option[String]
  ): UserAction = {
    UserAction(transferStatusDefault.value, s"mailto:%s?subject=Ref: ${consignmentReference.get} - Issue With Transfer", actionTextDefault.value)
  }
}

case class UserAction(transferStatus: String, actionUrl: String, actionText: String)

sealed trait ActionText {
  val value: String
}

sealed trait TransferStatus {
  val value: String
}

case object Errors extends ActionText {
  val value: String = "View errors"
}

case object View extends ActionText {
  val value: String = "View"
}

case object Download extends ActionText {
  val value: String = "Download metadata"
}

case object Resume extends ActionText {
  val value: String = "Resume transfer"
}

case object ContactUs extends ActionText with TransferStatus {
  val value: String = "Contact us"
}

case object InProgress extends TransferStatus {
  val value: String = "In Progress"
}

case object InReview extends TransferStatus {
  val value: String = "In Review"
}

case object Failed extends TransferStatus {
  val value: String = "Failed"
}

case object Transferred extends TransferStatus {
  val value: String = "Transferred"
}
