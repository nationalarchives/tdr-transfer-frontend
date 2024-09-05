package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataController._
import graphql.codegen.GetConsignment.getConsignment.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.{GetConsignmentFiles, GetConsignmentStatus}
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.Results.Redirect
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses._
import services.{ConsignmentService, ConsignmentStatusService, DisplayPropertiesService, DisplayProperty}
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.Inject

class AdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentStatusService: ConsignmentStatusService
) extends TokenSecurity
    with Logging {

  val byClosureType: DisplayProperty => Boolean = (dp: DisplayProperty) => dp.propertyType == "Closure"

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    (for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      pageArgs <- getStartPageDetails(consignmentId, request.token)
    } yield {
      val statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, UploadType)
      val uploadStatus: Option[String] = statusesToValue.get(UploadType).flatten
      uploadStatus match {
        case Some(CompletedValue.value) =>
          redirectIfReviewInProgress(consignmentId, consignmentStatuses)(Ok(views.html.standard.additionalMetadataStart(pageArgs)))
        case Some(InProgressValue.value) | None =>
          Redirect(routes.UploadController.uploadPage(consignmentId))
      }
    }).recover(err => {
      logger.error(err.getMessage, err)
      Forbidden(views.html.forbiddenError(request.token.name, isLoggedIn = true, isJudgmentUser = false))
    })
  }

  private def getStartPageDetails(consignmentId: UUID, token: Token) = {
    for {
      consignment <- consignmentService.getConsignmentDetails(consignmentId, token.bearerAccessToken)
      (closurePropertiesSummaries, descriptivePropertiesSummaries) <- displayPropertiesService
        .getDisplayProperties(consignmentId, token.bearerAccessToken, None)
        .map(_.partition(byClosureType))
        .map(m => (m._1.map(_.summary), m._2.map(_.summary)))
    } yield {
      AdditionalMetadataStartPage(
        consignment.consignmentReference,
        consignmentId,
        token.name,
        closurePropertiesSummaries,
        descriptivePropertiesSummaries,
        getValue(consignment.consignmentStatuses, ClosureMetadataType),
        getValue(consignment.consignmentStatuses, DescriptiveMetadataType)
      )
    }
  }

  def validate(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    (for {
      pageArgs <- getStartPageDetails(consignmentId, request.token)
      statuses <- consignmentService.getConsignmentFilesData(consignmentId, request.token.bearerAccessToken)
    } yield {
      val allErrors = getErrors(statuses, List("descriptive", "closure"))
      if (allErrors.nonEmpty) {
        val args = pageArgs.copy(errors = allErrors)
        Ok(views.html.standard.additionalMetadataStart(args))
      } else {
        Redirect(routes.DownloadMetadataController.downloadMetadataPage(consignmentId))
      }
    }).recover(err => {
      logger.error(err.getMessage, err)
      Forbidden(views.html.forbiddenError(request.token.name, isLoggedIn = true, isJudgmentUser = false))
    })
  }

  private def getErrors(statuses: GetConsignmentFiles.getConsignmentFiles.GetConsignment, metadataTypes: List[String]): Seq[(String, List[String])] = {
    metadataTypes.flatMap(metadataType => {
      val statusType = metadataType match {
        case "descriptive" => DescriptiveMetadataType
        case "closure"     => ClosureMetadataType
      }
      val incompleteCount = statuses.files.flatMap(_.fileStatuses).filter(_.statusType == statusType.id).count(_.statusValue == IncompleteValue.value)
      if (incompleteCount > 0) {
        val record = if (incompleteCount == 1) "record" else "records"
        Seq((s"$metadataType-metadata", s"There is incomplete $metadataType metadata associated with $incompleteCount $record" :: Nil))
      } else {
        Nil
      }
    })

  }

  def getValue(statuses: List[GetConsignment.ConsignmentStatuses], statusType: StatusType): MetadataProgress = {
    val notEntered = MetadataProgress("NOT ENTERED", "grey")
    statuses.find(_.statusType == statusType.id).map(_.value).map {
      case NotEnteredValue.value => notEntered
      case CompletedValue.value  => MetadataProgress("ENTERED", "blue")
      case IncompleteValue.value => MetadataProgress("INCOMPLETE", "red")
      case _                     => notEntered
    } getOrElse notEntered
  }
}

object AdditionalMetadataController {
  case class MetadataProgress(value: String, colour: String)
  case class AdditionalMetadataStartPage(
      consignmentRef: String,
      consignmentId: UUID,
      name: String,
      closurePropertiesSummaries: List[String],
      descriptivePropertiesSummaries: List[String],
      closureStatus: MetadataProgress,
      descriptiveStatus: MetadataProgress,
      errors: Seq[(String, Seq[String])] = Nil
  )
  
  def redirectIfReviewInProgress(
    consignmentId: UUID,
    consignmentStatuses: Seq[ConsignmentStatuses]
  ): Result => Result = requestedPage => {
    if (ConsignmentStatusService.statusValue(MetadataReviewType)(consignmentStatuses) == InProgressValue) {
      Redirect(routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId))
    } else requestedPage
  }
}
