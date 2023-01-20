package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import graphql.codegen.types.ConsignmentFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

// deliberately not using .get for Maps so that it throws an exception as we should be alerted if values in DB have changed
class ViewTransfersController @Inject() (val consignmentService: ConsignmentService, val keycloakConfiguration: KeycloakConfiguration, val controllerComponents: SecurityComponents)
    extends TokenSecurity {

  // ConsignmentStatusTypes in the order they were updated
  val validStandardConsignmentStatusTypes: List[String] = List("series", "transferAgreement", "upload", "clientChecks", "confirmTransfer", "export")
  val validJudgmentConsignmentStatusTypes: List[String] = List("upload", "clientChecks", "export")

  val statusColours: Map[String, String] = Map("In Progress" -> "yellow", "Failed" -> "red", "Transferred" -> "green")

  def viewConsignments(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val consignmentFilters = ConsignmentFilters(Some(request.token.userId), None)
    for {
      consignmentTransfers <- consignmentService.getConsignments(consignmentFilters, request.token.bearerAccessToken)
      consignments = consignmentTransfers.edges match {
        case Some(edges) => edges.flatMap(createView)
        case None        => Nil
      }
    } yield {
      val consignmentType = if (request.token.isJudgmentUser) "judgments" else "standard"
      Ok(views.html.viewTransfers(consignments, request.token.name, request.token.email, consignmentType))
    }
  }

  private def createView(edges: Option[Edges]): Option[ConsignmentTransfers] = {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    edges.map { edge =>
      val mostRecentStatus: MostRecentConsignmentStatus = getConsignmentStatus(edge.node.currentStatus, edge.node.consignmentType.getOrElse("standard"))
      val mostRecentStatusValue = (if (mostRecentStatus.statusType == "export") "Export" else "") + mostRecentStatus.statusValue
      val actionUrl: String =
        getActionUrl(mostRecentStatus, edge.node.consignmentid.get, edge.node.consignmentReference, edge.node.consignmentType.getOrElse("standard"))
      val actionUrlTitle = actionUrlText(
        mostRecentStatus.statusType,
        mostRecentStatusValue
      )
      val userActions = UserAction(actionUrl, actionUrlTitle)
      val transferStatus = convertConsignmentStatusValuesToTransferStatuses(mostRecentStatusValue)

      ConsignmentTransfers(
        edge.node.consignmentid,
        edge.node.consignmentReference,
        transferStatus,
        statusColours(transferStatus),
        userActions,
        edge.node.exportDatetime.map(_.format(formatter)).getOrElse(""),
        edge.node.createdDatetime.map(_.format(formatter)).getOrElse(""),
        edge.node.totalFiles
      )
    }
  }

  private def getConsignmentStatus(currentStatus: CurrentStatus, consignmentType: String): MostRecentConsignmentStatus = {
    val currentStatusTypes: List[String] = currentStatus.productElementNames.toList
    val currentStatusValues: List[Option[String]] = currentStatus.productIterator.toList.asInstanceOf[List[Option[String]]]
    val consignmentStatusTypesAndValsInDb: Map[String, Option[String]] = currentStatusTypes.zip(currentStatusValues).toMap

    val consignmentStatusTypes: List[String] = if (consignmentType == "judgment") validJudgmentConsignmentStatusTypes else validStandardConsignmentStatusTypes

    val indexOfConsignmentStatusAfterClientChecks: Int = consignmentStatusTypes.indexOf("clientChecks") + 1
    val statusTypeOfStatusAfterClientChecks: String = consignmentStatusTypes(indexOfConsignmentStatusAfterClientChecks)
    val statusValueOfStatusAfterClientChecks: Option[String] = consignmentStatusTypesAndValsInDb(statusTypeOfStatusAfterClientChecks)

    val mostRecentStatus: MostRecentConsignmentStatus =
      consignmentStatusTypes.collectFirst {
        case consignmentStatusType
            if !consignmentStatusTypesAndValsInDb(consignmentStatusType).contains("Completed") // if value in DB for statusType is not "Completed", display a link to page
            // if statusType is clientChecks but value of statusType, AFTER clientChecks, ISN'T "Completed" then take user to clientChecks (file checks) page
              || consignmentStatusType == "clientChecks" && !statusValueOfStatusAfterClientChecks.contains("Completed")
              || "export" == consignmentStatusType => // no matter the value of "export", always display an action
          val consignmentStatusValue = consignmentStatusTypesAndValsInDb(consignmentStatusType).getOrElse("")

          if (consignmentType == "judgment" && consignmentStatusType == "upload" && consignmentStatusValue == "") {
            MostRecentConsignmentStatus("beforeYouUpload", consignmentStatusValue)
          } else {
            MostRecentConsignmentStatus(consignmentStatusType, consignmentStatusValue)
          }
      }.get
    mostRecentStatus
  }

  // scalastyle:off cyclomatic.complexity
  private def getActionUrl(mostRecentStatus: MostRecentConsignmentStatus, consignmentId: UUID, consignmentRef: String, consignmentType: String): String = {
    if (mostRecentStatus.statusValue == "Failed" && mostRecentStatus.statusType != "clientChecks") {
      // We expect only Export and Client Checks to have a Failed value but there are weird edge cases.
      s"""mailto:%s?subject=Ref: $consignmentRef - ${mostRecentStatus.statusType} failure"""
    } else {
      mostRecentStatus.statusType match {
        case "beforeYouUpload" if consignmentType == "judgment" => routes.BeforeUploadingController.beforeUploading(consignmentId).url
        case "series"                                           => routes.SeriesDetailsController.seriesDetails(consignmentId).url
        case "transferAgreement" =>
          mostRecentStatus.statusValue match {
            case ""           => routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId).url
            case "InProgress" => routes.TransferAgreementComplianceController.transferAgreement(consignmentId).url
          }
        case "upload" =>
          consignmentType match {
            case "judgment" => routes.UploadController.judgmentUploadPage(consignmentId).url
            case _          => routes.UploadController.uploadPage(consignmentId).url
          }
        case "clientChecks" =>
          mostRecentStatus.statusValue match {
            case "" | "InProgress" =>
              consignmentType match {
                case "judgment" => routes.FileChecksController.judgmentFileChecksPage(consignmentId).url
                case _          => routes.FileChecksController.fileCheckProgress(consignmentId).url
              }
            case "Completed" | "CompletedWithIssues" | "Failed" =>
              consignmentType match {
                case "judgment" => routes.FileChecksResultsController.judgmentFileCheckResultsPage(consignmentId).url
                case _          => routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url
              }
          }
        case "confirmTransfer" => routes.ConfirmTransferController.confirmTransfer(consignmentId).url
        case "export" =>
          mostRecentStatus.statusValue match {
            case "InProgress" | "Completed" => routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url
          }
      }
    }
  }

  private def convertConsignmentStatusValuesToTransferStatuses(consignmentStatusValue: String): String = consignmentStatusValue match {
    case "ExportCompleted"                                                               => "Transferred"
    case "CompletedWithIssues" | "Failed" | "ExportCompletedWithIssues" | "ExportFailed" => "Failed"
    case _                                                                               => s"In Progress"
  }

  private def actionUrlText(consignmentStatusType: String, consignmentStatusValue: String): String = {
    val errorsThatUsersCanView = List("upload", "clientChecks")
    consignmentStatusValue match {
      case "" | "InProgress"                                                                          => "Resume transfer"
      case "Completed" if consignmentStatusType == "clientChecks"                                     => "Resume transfer"
      case "ExportCompleted" | "ExportInProgress"                                                     => "Download report"
      case "CompletedWithIssues" | "Failed" if errorsThatUsersCanView.contains(consignmentStatusType) => "View errors"
      case "CompletedWithIssues" | "Failed" | "ExportFailed"                                          => "Contact us"
    }
  }
}

case class ConsignmentTransfers(
    consignmentId: Option[UUID],
    reference: String,
    status: String,
    statusColour: String,
    userAction: UserAction,
    dateOfExport: String,
    dateStarted: String,
    numberOfFiles: Int
)
case class MostRecentConsignmentStatus(statusType: String, statusValue: String)
case class UserAction(actionUrl: String, actionText: String)
