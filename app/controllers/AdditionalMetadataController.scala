package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataController._
import controllers.inputvalidation.{DataType, MetadataPropertyCriteria, Validation}
import controllers.util.CsvUtils
import graphql.codegen.GetConsignment.getConsignment.GetConsignment
import graphql.codegen.GetConsignmentFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services._
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val displayPropertiesService: DisplayPropertiesService,
    val customMetadataService: CustomMetadataService,
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
          Ok(views.html.standard.additionalMetadataStart(pageArgs))
        case Some(InProgressValue.value) | None =>
          Redirect(routes.UploadController.uploadPage(consignmentId))
      }
    }).recover(err => {
      logger.error(err.getMessage, err)
      Forbidden(views.html.forbiddenError(request.token.name, isLoggedIn = true, isJudgmentUser = false))
    })
  }
  def createMetadataPropertyCriteria(dp: DisplayProperty, dependencies: Option[Map[String, List[MetadataPropertyCriteria]]] = None): MetadataPropertyCriteria = {
    MetadataPropertyCriteria(
      dp.propertyName,
      dp.displayName,
      dp.active,
      DataType.get(dp.dataType.toString),
      dp.editable,
      dp.propertyType,
      dp.required,
      dependencies = dependencies
    )
  }

  def uploadAdditionalMetadata(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    {
      val body = request.body.asMultipartFormData
      val fileP = body.flatMap(_.file("file-upload"))
      val result = if (fileP.isDefined) {
        val file = fileP.get

        for {
          displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some("closure"))
          customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
        } yield {
          val metadataPropertyCriteria = displayProperties.map(dp => {
            val dependencies = customMetadata
              .find(_.name == dp.propertyName)
              .get
              .values
              .map(v => v.value -> v.dependencies.map(d => createMetadataPropertyCriteria(displayProperties.find(_.propertyName == d.name).get)))
              .toMap
            createMetadataPropertyCriteria(dp, Some(dependencies))
          })

          val data = CsvUtils.readCsv(fileP.get.ref.toFile)
          val updatedData =
            data.map(
              _.filter(row => metadataPropertyCriteria.exists(_.displayName == row._1)).map(row => metadataPropertyCriteria.find(_.displayName == row._1).get.name -> row._2).toMap
            )
          val error = updatedData.map(row => Validation.validateClosure(row, metadataPropertyCriteria))
          Ok(error.toString)
        }
      } else Future(BadRequest("Upload Error"))
      result
    }
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
