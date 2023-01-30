package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.InputNameAndValue
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, closureType, fileType}
import graphql.codegen.types.UpdateFileMetadataInput
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.AsyncCacheApi
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class AdditionalMetadataClosureStatusController @Inject() (
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val cache: AsyncCacheApi
) extends TokenSecurity {

  val closureStatusForm: Form[ClosureStatusFormData] = Form(
    mapping(
      "closureStatus" -> boolean
        .verifying("You must confirm this closure has been approved before continuing.", b => b)
    )(ClosureStatusFormData.apply)(ClosureStatusFormData.unapply)
  )

  val closureStatusField: InputNameAndValue = InputNameAndValue("closureStatus", "Yes, I confirm")

  private val additionalProperties: List[String] = List(clientSideOriginalFilepath, fileType)

  def getClosureStatusPage(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        closureProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some(metadataType)).map(_.map(_.summary))
        consignment <- consignmentService.getConsignmentFileMetadata(
          consignmentId,
          request.token.bearerAccessToken,
          Some(metadataType),
          Some(fileIds),
          Some(additionalProperties)
        )
        response <-
          if (consignment.files.nonEmpty) {
            val filePaths = consignment.files.flatMap(_.fileMetadata).filter(_.name == clientSideOriginalFilepath).map(_.value)
            val areAllFilesClosed = consignmentService.areAllFilesClosed(consignment)
            cache.set(s"$consignmentId-data", (consignment.consignmentReference, filePaths, closureProperties), 1.hour)
            Future(
              Ok(
                views.html.standard.additionalMetadataClosureStatus(
                  consignmentId,
                  metadataType,
                  filePaths,
                  fileIds,
                  closureStatusForm,
                  closureStatusField,
                  areAllFilesClosed,
                  consignment.consignmentReference,
                  closureProperties,
                  request.token.name
                )
              )
            )
          } else {
            Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
          }
      } yield response
  }

  def submitClosureStatus(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val errorFunction: Form[ClosureStatusFormData] => Future[Result] = { formWithErrors: Form[ClosureStatusFormData] =>
        for {
          (consignmentRef, filePaths, closureProperties) <- cache.getOrElseUpdate(s"$consignmentId-data", 1.hour)(
            for {
              closureProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some(metadataType)).map(_.map(_.summary))
              consignment <- consignmentService.getConsignmentFileMetadata(
                consignmentId,
                request.token.bearerAccessToken,
                Some(metadataType),
                Some(fileIds),
                Some(additionalProperties)
              )
              filePaths = consignment.files.flatMap(_.fileMetadata).filter(_.name == clientSideOriginalFilepath).map(_.value)
            } yield (consignment.consignmentReference, filePaths, closureProperties)
          )
        } yield {
          BadRequest(
            views.html.standard.additionalMetadataClosureStatus(
              consignmentId,
              metadataType,
              filePaths,
              fileIds,
              formWithErrors,
              closureStatusField,
              areAllFilesClosed = false,
              consignmentRef,
              closureProperties,
              request.token.name
            )
          )
        }
      }

      val successFunction: ClosureStatusFormData => Future[Result] = { _ =>
        val metadataInput = UpdateFileMetadataInput(filePropertyIsMultiValue = false, closureType.name, closureType.value)
        customMetadataService
          .saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, List(metadataInput))
          .map { _ =>
            Redirect(routes.AddAdditionalMetadataController.addAdditionalMetadata(consignmentId, metadataType, fileIds))
          }
      }

      closureStatusForm.bindFromRequest().fold(errorFunction, successFunction)
  }
}

case class ClosureStatusFormData(closureStatus: Boolean)
