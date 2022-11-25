package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.AddClosureMetadataController.{ControllerInfo, File, FormData, PageInfo, ValueSelectedAndDepsToDel}
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, closureType}
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.types.{FileFilters, UpdateFileMetadataInput}
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, CustomMetadataService}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AddClosureMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val cache: AsyncCacheApi
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {
  private val mainFormPageTitle = "Add closure metadata to"
  private val mainFormPageDescription = "Enter metadata for closure fields here."
  private val dependencyFormPageTitle = "Add %s to"
  private val dependencyFormPageDescription =
    s"""Enter a publicly visible %s if, for example, %s sensitive information.
       | For guidance on how to create %s, read our FAQs (opens in a new tab)""".stripMargin

  def addClosureMetadata(consignmentId: UUID, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      formData <- getDefaultFieldsForForm(Set(closureType), consignmentId, request)
      (pageInfo, controllerInfo) <- getInfoForClosurePage(
        consignmentId,
        request,
        isMainForm = true,
        List(s"${closureType.name}-${closureType.value}"),
        formData.formFields,
        fileIds
      )
    } yield {
      val updatedPageInfo = pageInfo.copy(pageTitle = mainFormPageTitle, pageDescription = mainFormPageDescription)
      Ok(views.html.standard.addClosureMetadata(updatedPageInfo, controllerInfo))
    }
  }

  def addClosureMetadataSubmit(isMainForm: Boolean, fieldsAndValuesSelectedOnPrevPage: List[String], consignmentId: UUID, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      validateForm(consignmentId: UUID, isMainForm, fieldsAndValuesSelectedOnPrevPage).flatMap { formData =>
        val validatedFields: List[FormField] = formData.formFields
        if (validatedFields.exists(_.fieldErrors.nonEmpty)) {
          val (formPageTitle, formPageDescription) =
            if (!isMainForm) {
              val fieldNames: List[String] = validatedFields.map(_.fieldName)
              getDependenciesPageTitle(fieldNames)
            } else {
              (mainFormPageTitle, mainFormPageDescription)
            }

          getInfoForClosurePage(consignmentId, request, isMainForm, fieldsAndValuesSelectedOnPrevPage, validatedFields, fileIds).map { case (pageInfo, controllerInfo) =>
            Ok(
              views.html.standard.addClosureMetadata(
                pageInfo.copy(pageTitle = formPageTitle, pageDescription = formPageDescription),
                controllerInfo
              )
            )
          }
        } else {
          val metadataInput: List[UpdateFileMetadataInput] = validatedFields.flatMap {
            case TextField(fieldId, _, _, multiValue, nameAndValue, _, _, _) =>
              List(UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value))
            case DateField(fieldId, _, _, multiValue, day, month, year, _, _, _) =>
              val dateTime: LocalDateTime = LocalDate.of(year.value.toInt, month.value.toInt, day.value.toInt).atTime(LocalTime.MIDNIGHT)
              List(UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", " ")))
            case RadioButtonGroupField(fieldId, _, _, multiValue, _, selectedOption, _, _) =>
              List(UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, stringToBoolean(selectedOption).toString))
            case DropdownField(fieldId, _, _, multiValue, _, selectedOption, _, _) =>
              List(UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, selectedOption.map(_.value).getOrElse("")))
            case CheckboxField(fieldId, _, _, multiValue, _, potentialSelectedOptions, _, _) =>
              val selectedOptions = potentialSelectedOptions.getOrElse(Nil)
              selectedOptions.map(selectedOption => {
                UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, selectedOption.value)
              })
          }
          val propertyNameFieldSelectedAndDeps: Set[ValueSelectedAndDepsToDel] = getValuesThatWereSelectedIfTheyHaveDependencies(formData.metadataProperties, metadataInput)
          saveMetadataAndReturnPage(consignmentId, fileIds, metadataInput, propertyNameFieldSelectedAndDeps)
        }
      }
    }

  def saveMetadataAndReturnPage(
      consignmentId: UUID,
      fileIds: List[UUID],
      metadataInput: List[UpdateFileMetadataInput],
      propertyNameValueSelectedAndDepsToDel: Set[ValueSelectedAndDepsToDel]
  )(implicit request: Request[AnyContent]): Future[Result] = {
    val dependenciesToDelete: List[String] = propertyNameValueSelectedAndDepsToDel.flatMap(_.depsOfNonSelectedValues).toList
    for {
      _ <-
        if (dependenciesToDelete.nonEmpty) Future.successful(dependenciesToDelete)
        else Future.successful(Nil) // "Future.successful" should be replaced with a call to delete dependencies
      _ <- customMetadataService.saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, metadataInput)
    } yield {
      val valueSelectedAndDeps: Set[ValueSelectedAndDepsToDel] = propertyNameValueSelectedAndDepsToDel.filter(_.valueHasDependencies)
      if (valueSelectedAndDeps.nonEmpty) {
        val fieldsAndValuesSelectedOnPrevPage: Set[String] = valueSelectedAndDeps.map { fieldsAndValuesSelectedOnPrevPage =>
          s"${fieldsAndValuesSelectedOnPrevPage.propertyName}-${fieldsAndValuesSelectedOnPrevPage.valueSelected}"
        }
        Redirect(routes.AddClosureMetadataController.addClosureMetadataDependenciesPage(fieldsAndValuesSelectedOnPrevPage.toList, consignmentId, fileIds))
      } else {
        Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, fileIds))
      }
    }
  }

  def addClosureMetadataDependenciesPage(propertyNamesAndFieldsSelected: List[String], consignmentId: UUID, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      propertyNamesAndFieldsSelected match {
        case Nil =>
          Future.successful(Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, fileIds)))
        case fieldsAndValuesSelectedOnPrevPage =>
          for {
            defaultFields <- {
              val staticMetadata: Set[StaticMetadata] = convertNameAndFieldToObject(fieldsAndValuesSelectedOnPrevPage)
              getDefaultFieldsForForm(staticMetadata, consignmentId, request).map { defaultFormData => defaultFormData.formFields }
            }
            (pageInfo, controllerInfo) <- getInfoForClosurePage(consignmentId, request, isMainForm = false, fieldsAndValuesSelectedOnPrevPage, defaultFields, fileIds)
            fieldNames = defaultFields.map(_.fieldName)
            (formPageTitle, formPageDescription) = getDependenciesPageTitle(fieldNames)
            page = Ok(
              views.html.standard.addClosureMetadata(
                pageInfo.copy(pageTitle = formPageTitle, pageDescription = formPageDescription),
                controllerInfo
              )
            )
          } yield page
      }
    }

  private def convertNameAndFieldToObject(fieldsAndValuesSelectedOnPrevPage: List[String]): Set[StaticMetadata] = {
    fieldsAndValuesSelectedOnPrevPage.map { fieldsAndValuesSelectedOnPrevPage =>
      val fieldsAndValuesSelectedOnPrevPageAsArray: Array[String] = fieldsAndValuesSelectedOnPrevPage.split("-")
      StaticMetadata(fieldsAndValuesSelectedOnPrevPageAsArray(0), fieldsAndValuesSelectedOnPrevPageAsArray(1))
    }.toSet
  }

  private def validateForm(consignmentId: UUID, isMainForm: Boolean, fieldsAndValuesSelectedOnPrevPage: List[String])(implicit
      request: Request[AnyContent]
  ): Future[FormData] = {
    val propertyName = if (isMainForm) closureType.name else "Dependency"
    for {
      formData <- cache.getOrElseUpdate[FormData](s"$propertyName-propertiesAndFieldValues") {
        getDefaultFieldsForForm(convertNameAndFieldToObject(fieldsAndValuesSelectedOnPrevPage), consignmentId, request)
      }
      dynamicFormUtils = new DynamicFormUtils(request, formData.formFields)
      formAnswers: Map[String, Seq[String]] = dynamicFormUtils.formAnswersWithValidInputNames
      updatedFormFields: List[FormField] = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(formAnswers)
    } yield FormData(formData.metadataProperties, updatedFormFields)
  }

  private def getDependenciesPageTitle(fieldNames: List[String]): (String, String) = {
    val concatenatedFieldNames: String = if (fieldNames.length > 1) s"${fieldNames.init.mkString(", ")} and ${fieldNames.last}" else fieldNames.head
    val concatenatedFieldNamesWithArticle = GrammarHelper.generateCorrectIndefiniteArticle(concatenatedFieldNames)
    val formPageTitle = dependencyFormPageTitle.format(concatenatedFieldNamesWithArticle)
    val itOrThey: String = if (fieldNames.length > 1) "they contain" else "it contains"
    val formPageDescription: String = dependencyFormPageDescription.format(concatenatedFieldNames, itOrThey, concatenatedFieldNamesWithArticle)
    (formPageTitle, formPageDescription)
  }

  private def getInfoForClosurePage(
      consignmentId: UUID,
      request: Request[AnyContent],
      isMainForm: Boolean,
      fieldsAndValuesSelectedOnPrevPage: List[String],
      defaultFieldsForForm: List[FormField],
      fileIds: List[UUID]
  ): Future[(PageInfo, ControllerInfo)] =
    for {
      consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(FileFilters(None, Option(fileIds), None)))
      updatedFieldsForForm <- {
        cache.set(s"$consignmentId-reference", consignment.consignmentReference, 1.hour)
        // Set the values to those of the first file's metadata until we decide what to do with multiple files.
        updateFormFields(defaultFieldsForForm, consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil))
      }
    } yield {
      val files: List[File] = getFilesFromConsignment(consignment.files)
      // Call to details.parentFolderId.get should be temporary. User shouldn't see this page if the parent ID is empty.
      val pageInfo = PageInfo(request.token.name, consignment.consignmentReference, "", "", updatedFieldsForForm)
      val controllerInfo = ControllerInfo(isMainForm, fieldsAndValuesSelectedOnPrevPage, consignmentId, files)
      (pageInfo, controllerInfo)
    }

  private def getValuesThatWereSelectedIfTheyHaveDependencies(
      dependencyProperties: Set[CustomMetadata],
      metadataInput: List[UpdateFileMetadataInput]
  ): Set[ValueSelectedAndDepsToDel] = {
    val propertiesThatHaveValuesWithDependencies: Set[CustomMetadata] = getPropertiesWhereValuesHaveDependencies(dependencyProperties)
    val valuesThatWereSelectedThatHaveDependencies: Set[ValueSelectedAndDepsToDel] = getValueSelectedIfItHasDependencies(metadataInput, propertiesThatHaveValuesWithDependencies)
    valuesThatWereSelectedThatHaveDependencies
  }

  private def getDefaultFieldsForForm(staticMetadata: Set[StaticMetadata], consignmentId: UUID, request: Request[AnyContent]): Future[FormData] = {
    for {
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      customMetadataUtils = new CustomMetadataUtils(customMetadata)

      dependencyProperties: Set[CustomMetadata] = staticMetadata.flatMap { propertyNameAndValue =>
        getDependenciesFromValue(customMetadataUtils, Set(propertyNameAndValue.name), Some(propertyNameAndValue.value))
      }
      formFields = customMetadataUtils.convertPropertiesToFormFields(dependencyProperties)
    } yield {
      val pageType = if (staticMetadata.exists(_.name == closureType.name)) closureType.name else "Dependency"
      val defaultFormData = FormData(dependencyProperties, formFields)
      cache.set(s"$pageType-propertiesAndFieldValues", defaultFormData, 1.hour)
      defaultFormData
    }
  }

  private def getDependenciesFromValue(
      customMetadataUtils: CustomMetadataUtils,
      propertyNames: Set[String] = Set(closureType.name),
      valueToGetDependenciesFrom: Option[String] = Some(closureType.value)
  ): Set[CustomMetadata] = {
    propertyNames.flatMap { propertyName =>
      valueToGetDependenciesFrom match {
        case Some(valueToGetDependencies) =>
          val valuesByProperties: Map[String, List[CustomMetadata.Values]] = customMetadataUtils.getValuesOfProperties(Set(propertyName))
          val allValuesForProperty: Seq[CustomMetadata.Values] = valuesByProperties(propertyName)
          val value: Seq[CustomMetadata.Values] = allValuesForProperty.filter(_.value.toLowerCase == valueToGetDependencies.toLowerCase)
          val dependencyNames: Seq[String] = value.flatMap(_.dependencies.map(_.name))
          customMetadataUtils.getCustomMetadataProperties(dependencyNames.toSet)
        case None =>
          customMetadataUtils.getCustomMetadataProperties(Set(propertyName))
      }
    }
  }

  private def getFilesFromConsignment(files: List[getConsignmentFilesMetadata.GetConsignment.Files]): List[File] = {
    files.map(file => {
      val filePath = file.fileMetadata.find(_.name == clientSideOriginalFilepath).map(_.value).getOrElse("")
      File(file.fileId, filePath)
    })
  }

  private def stringToBoolean(value: String): Boolean = {
    Try(value.toBoolean) match {
      case Failure(_)     => value == "yes"
      case Success(value) => value
    }
  }

  private def updateFormFields(orderedFieldsForForm: List[FormField], fileMetadata: List[FileMetadata]): Future[List[FormField]] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val metadataMap = fileMetadata.groupBy(_.name).view.mapValues(_.head).toMap
    val selectedFoiExemptionCode: Option[List[FileMetadata]] = fileMetadata.groupBy(_.name).view.toMap.get("FoiExemptionCode")
    Future.successful {
      orderedFieldsForForm.map {
        case dateField: DateField =>
          metadataMap
            .get(dateField.fieldId)
            .map(metadata => DateField.update(dateField, LocalDateTime.parse(metadata.value, formatter)))
            .getOrElse(dateField)
        case dropdownField: DropdownField =>
          metadataMap
            .get(dropdownField.fieldId)
            .map(metadata => DropdownField.update(dropdownField, metadata.value))
            .getOrElse(dropdownField)
        case checkboxField: CheckboxField =>
          metadataMap
            .get(checkboxField.fieldId)
            .map(metadata => CheckboxField.update(checkboxField, selectedFoiExemptionCode.get))
            .getOrElse(checkboxField)
        case radioButtonGroupField: RadioButtonGroupField =>
          metadataMap
            .get(radioButtonGroupField.fieldId)
            .map(metadata => RadioButtonGroupField.update(radioButtonGroupField, stringToBoolean(metadata.value)))
            .getOrElse(radioButtonGroupField)
        case textField: TextField =>
          metadataMap
            .get(textField.fieldId)
            .map(metadata => TextField.update(textField, metadata.value))
            .getOrElse(textField)
      }
    }
  }

  private def getPropertiesWhereValuesHaveDependencies(dependencyProperties: Set[CustomMetadata]): Set[CustomMetadata] =
    dependencyProperties.filter(property => property.values.exists(_.dependencies.nonEmpty))

  private def getValueSelectedIfItHasDependencies(
      fieldsThatWereSelected: List[UpdateFileMetadataInput],
      propertiesThatHaveValuesWithDependencies: Set[CustomMetadata]
  ): Set[ValueSelectedAndDepsToDel] = {
    val propertyAndValueSelected: Set[ValueSelectedAndDepsToDel] = propertiesThatHaveValuesWithDependencies.map { propertyThatHasValuesWithDependencies =>
      val valueSelected: Option[UpdateFileMetadataInput] = fieldsThatWereSelected.find(_.filePropertyName == propertyThatHasValuesWithDependencies.name)
      val valueSelectedAsString: String = valueSelected match {
        case Some(valueSelected) => valueSelected.value
        case None                => "No value selected"
      }

      val valueThatWasSelectedAndDependencies: Map[Boolean, List[Values]] =
        propertyThatHasValuesWithDependencies.values.groupBy(_.value.toLowerCase == valueSelectedAsString.toLowerCase)
      val selectedValueAndDependencies = valueThatWasSelectedAndDependencies(true).head
      ValueSelectedAndDepsToDel(
        propertyThatHasValuesWithDependencies.name,
        selectedValueAndDependencies.value,
        selectedValueAndDependencies.dependencies.nonEmpty,
        valueThatWasSelectedAndDependencies(false).flatMap(_.dependencies.map(_.name))
      )
    }
    propertyAndValueSelected
  }
}

object AddClosureMetadataController {
  case class File(fileId: UUID, name: String)
  case class FormData(metadataProperties: Set[CustomMetadata], formFields: List[FormField])
  case class ValueSelectedAndDepsToDel(propertyName: String, valueSelected: String, valueHasDependencies: Boolean, depsOfNonSelectedValues: List[String])
  case class PageInfo(userName: String, consignmentRef: String, pageTitle: String, pageDescription: String, fieldsToApplyToFile: List[FormField])
  case class ControllerInfo(isMainForm: Boolean, fieldsAndValuesSelectedOnPrevPage: List[String], consignmentId: UUID, files: List[File])
}
