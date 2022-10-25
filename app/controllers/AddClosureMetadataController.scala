package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.AddClosureMetadataController.File
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, closureType, descriptionPublic}
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.{FileFilters, UpdateFileMetadataInput}
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AddClosureMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                             val graphqlConfiguration: GraphQLConfiguration,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val consignmentService: ConsignmentService,
                                             val customMetadataService: CustomMetadataService,
                                             val cache: AsyncCacheApi)
                                            (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def addClosureMetadata(consignmentId: UUID, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        //This is another API call to get the parent ID but this won't be needed once the JS solution is in so we can live with it.
        details <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(FileFilters(None, Option(fileIds), None)))
        defaultFieldForm <- getDefaultFieldsForForm(consignmentId, request)
        updatedFieldsForForm <- {
          cache.set(s"$consignmentId-reference", consignment.consignmentReference, 1.hour)
          //Set the values to those of the first file's metadata until we decide what to do with multiple files.
          updateFormFields(defaultFieldForm, consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil))
        }
      } yield {
        val files = getFilesFromConsignment(consignment.files.filter(file => fileIds.contains(file.fileId)))
        //Call to details.parentFolderId.get should be temporary. User shouldn't see this page if the parent ID is empty.
        Ok(views.html.standard.addClosureMetadata(consignmentId, consignment.consignmentReference, updatedFieldsForForm, request.token.name, files, details.parentFolderId.get))

      }
  }

  def addClosureMetadataSubmit(consignmentId: UUID, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        defaultFieldValues <- cache.getOrElseUpdate[List[FormField]]("fieldValues") {
          getDefaultFieldsForForm(consignmentId, request)
        }
        dynamicFormUtils = new DynamicFormUtils(request, defaultFieldValues)
        formAnswers: Map[String, Seq[String]] = dynamicFormUtils.formAnswersWithValidInputNames

        result <- {
          val updatedFormFields: List[FormField] = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(formAnswers)
          if (updatedFormFields.exists(_.fieldErrors.nonEmpty)) {
            for {
              //This is another API call to get the parent ID but this won't be needed once the JS solution is in so we can live with it.
              details <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
              consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken)
              consignmentRef <- cache.getOrElseUpdate[String](s"$consignmentId-reference") {
                consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
              }
            } yield {
              val files = getFilesFromConsignment(consignment.files.filter(file => fileIds.contains(file.fileId)))
              Ok(views.html.standard.addClosureMetadata(consignmentId, consignmentRef, updatedFormFields, request.token.name, files, details.parentFolderId.get))
            }
          } else {
            val metadataInput: List[UpdateFileMetadataInput] = updatedFormFields map {
              case TextField(fieldId, _, _, multiValue, nameAndValue, _, _, _) =>
                UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value)
              case DateField(fieldId, _, _, multiValue, day, month, year, _, _, _) =>
                val dateTime: LocalDateTime = LocalDate.of(year.value.toInt, month.value.toInt, day.value.toInt).atTime(LocalTime.MIDNIGHT)
                UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", " "))
              case RadioButtonGroupField(fieldId, _, _, multiValue, _, selectedOption, _, _) =>
                UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, stringToBoolean(selectedOption).toString)
              case DropdownField(fieldId, _, _, multiValue, _, selectedOption, _, _) =>
                UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, selectedOption.map(_.value).getOrElse(""))
            }
            customMetadataService.saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, metadataInput).map(_ => {
              Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, fileIds))
            })
          }
        }
      } yield result
  }

  private def getDefaultFieldsForForm(consignmentId: UUID, request: Request[AnyContent]): Future[List[FormField]] = {
    for {
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      customMetadataUtils = new CustomMetadataUtils(customMetadata)
      propertyName = Set(closureType)
      value = "Closed"

      dependencyProperties: Set[CustomMetadata] = getDependenciesFromValue(customMetadataUtils, propertyName, value)
        .filterNot(_.name == descriptionPublic)

      formFields = customMetadataUtils.convertPropertiesToFormFields(dependencyProperties)
    } yield {
      cache.set("fieldValues", formFields, 1.hour)
      formFields
    }
  }

  private def getDependenciesFromValue(customMetadataUtils: CustomMetadataUtils,
                                       propertyName: Set[String],
                                       valueToGetDependenciesFrom: String): Set[CustomMetadata] = {
    val valuesByProperties: Map[String, List[CustomMetadata.Values]] = customMetadataUtils.getValuesOfProperties(propertyName)
    val allValuesForProperty: Seq[CustomMetadata.Values] = valuesByProperties(propertyName.head)
    val value: Seq[CustomMetadata.Values] = allValuesForProperty.filter(_.value == valueToGetDependenciesFrom)
    val dependencyNames: Seq[String] = value.flatMap(_.dependencies.map(_.name))
    customMetadataUtils.getCustomMetadataProperties(dependencyNames.toSet)
  }

  private def getFilesFromConsignment(files: List[getConsignmentFilesMetadata.GetConsignment.Files]): List[File] = {
    files.map(file => {
      val filePath = file.fileMetadata.find(_.name == clientSideOriginalFilepath).map(_.value).getOrElse("")
      File(file.fileId, filePath)
    })
  }

  private def stringToBoolean(value: String): Boolean = {
    Try(value.toBoolean) match {
      case Failure(_) => value == "yes"
      case Success(value) => value
    }
  }

  private def updateFormFields(orderedFieldsForForm: List[FormField], fileMetadata: List[FileMetadata]): Future[List[FormField]] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val metadataMap = fileMetadata.groupBy(_.name).view.mapValues(_.head).toMap
    Future.successful {
      orderedFieldsForForm.map {
        case dateField: DateField => metadataMap.get(dateField.fieldId)
          .map(metadata => DateField.update(dateField, LocalDateTime.parse(metadata.value, formatter))).getOrElse(dateField)
        case dropdownField: DropdownField => metadataMap.get(dropdownField.fieldId)
          .map(metadata => DropdownField.update(dropdownField, metadata.value)).getOrElse(dropdownField)
        case radioButtonGroupField: RadioButtonGroupField => metadataMap.get(radioButtonGroupField.fieldId)
          .map(metadata => RadioButtonGroupField.update(radioButtonGroupField, stringToBoolean(metadata.value))).getOrElse(radioButtonGroupField)
        case textField: TextField =>
          metadataMap.get(textField.fieldId)
            .map(metadata => TextField.update(textField, metadata.value)).getOrElse(textField)
      }
    }
  }
}

object AddClosureMetadataController {
  case class File(fileId: UUID, name: String)
}
