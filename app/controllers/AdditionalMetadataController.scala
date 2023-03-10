package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataController._
import graphql.codegen.GetConsignment.getConsignment.GetConsignment
import graphql.codegen.GetConsignmentFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services.{ConsignmentService, DisplayPropertiesService, DisplayProperty}
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.Inject

class AdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity
    with Logging {

  val byClosureType: DisplayProperty => Boolean = (dp: DisplayProperty) => dp.propertyType == "Closure"

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    getStartPageDetails(consignmentId, request.token)
      .map(pageArgs => Ok(views.html.standard.additionalMetadataStart(pageArgs)))
      .recover(err => {
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
}
