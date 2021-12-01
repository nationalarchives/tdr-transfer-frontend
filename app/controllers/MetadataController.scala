package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import controllers.MetadataController._
import graphql.codegen.Metadata.metadata.Metadata
import graphql.codegen.types.{DataType, PropertyType}
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{FileMetadataService, MetadataService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

class MetadataController @Inject()(val controllerComponents: SecurityComponents,
                                   val keycloakConfiguration: KeycloakConfiguration,
                                   val metadataService: MetadataService,
                                   val fileMetadataService: FileMetadataService,
                                   val cache: AsyncCacheApi
                                  )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def metadataHome(consignmentId: UUID, fileId: UUID): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.standard.metadata(consignmentId, fileId))
  }

  def metadataComplete(consignmentId: UUID, fileId: UUID) = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.standard.metadataSuccess(consignmentId, fileId))
  }

  def submitMetadata(consignmentId: UUID, fileId: UUID, fieldType: String): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map())
    val mappedForm: Map[String, Option[String]] = form.map(f => f._1 -> f._2.headOption)
    getMetadata(consignmentId, request.token.bearerAccessToken, fieldType).flatMap(m => {
      val errors = getErrors(mappedForm, m)
      val newForm = createForm(m, mappedForm)
      if (errors.nonEmpty) {
        Future(BadRequest(views.html.standard.metadataFields(consignmentId, fileId, fieldType, newForm, errors.toMap)))
      } else {
        val filteredForm = newForm.filter(_.value.format.nonEmpty)
        fileMetadataService.submitMetadata(fileId, filteredForm, request.token.bearerAccessToken)
          .map(_ => {
            val valuesWithDependencies: List[String] = getDependencies(filteredForm, m, 1)
            if(valuesWithDependencies.nonEmpty) {
              val url = routes.MetadataController.displayMetadataDependencies(consignmentId, fileId, fieldType).url
              Redirect(url, Map("dependencies" -> valuesWithDependencies))
            } else {
              Redirect(routes.MetadataController.metadataComplete(consignmentId, fileId))
            }

          })
      }
    }
    )
  }


  private def getDependencies(filteredForm: List[MetadataForm], metadataList: List[Metadata], level: Int): List[String] = {
    filteredForm.flatMap(each => {
      metadataList.filter(_.name == each.name).flatMap(meta => {
        if(level == 1) {
          meta.values.filter(_.value == each.value.format).flatMap(_.dependencies).map(_.name)
        } else {
          meta.values.flatMap(_.dependencies.flatMap(_.values)).filter(_.value == each.value.format).flatMap(_.dependencies).map(_.name)
        }
      })
    })
  }

  private def getErrors(mappedForm: Map[String, Option[String]], m: List[Metadata]): List[(String, String)] = {
    m.flatMap(metadata => {
      metadata.dataType match {
        case DataType.DateTime =>
          val formDateValue = createFormDateValue(mappedForm, metadata.name)
          if (!formDateValue.isValid && !formDateValue.isEmpty) {
            metadata.name -> "Date is in invalid format" :: Nil
          } else {
            Nil
          }
        case DataType.Integer =>
          val rawValue = mappedForm.getOrElse(metadata.name, None)
          if (rawValue.isDefined && rawValue.flatMap(_.toIntOption).isEmpty) {
          metadata.name -> "Value must be a number" :: Nil
        } else {
          Nil
        }
        case _ => Nil
      }
    })
  }

  private def getMetadata(consignmentId: UUID, token: BearerAccessToken, fieldType: String): Future[List[Metadata]] = {
    def filterFields(metadata: Metadata): Boolean = metadata.propertyGroup.getOrElse("").toLowerCase == fieldType && metadata.propertyType != PropertyType.System

    cache.get[List[Metadata]](consignmentId.toString).flatMap {
      case Some(metadata) =>
        Future(metadata.filter(filterFields))
      case None =>
        for {
          metadata <- metadataService.getConsignmentMetadataFields(token)
          _ <- cache.set(consignmentId.toString, metadata, 1.second)
        } yield metadata.filter(filterFields)
    }
  }

  def createFormDateValue(propertyMap: Map[String, Option[String]], fieldName: String): FormDateValue = {
    FormDateValue(
      propertyMap.getOrElse(s"$fieldName-day", Some("")).getOrElse(""),
      propertyMap.getOrElse(s"$fieldName-month", Some("")).getOrElse(""),
      propertyMap.getOrElse(s"$fieldName-year", Some("")).getOrElse(""),
    )
  }

  def createForm(metadataFields: List[Metadata], propertyMap: Map[String, Option[String]]): List[MetadataForm] = {
    metadataFields.map(field => {
      val rawValue: String = propertyMap.getOrElse(field.name, field.defaultValue).getOrElse("")
      val formValue: FormValue = field.dataType match {
        case DataType.DateTime =>
          val date: Regex = """(\d\d\d\d)-(\d\d)-(\d\d)""".r
          rawValue match {
            case date(year, month, day) => FormDateValue(day, month, year)
            case _ => createFormDateValue(propertyMap, field.name)
          }

        case _ => if (field.values.length <= 1) {
          FormTextValue(rawValue)
        } else {
          FormSelectValue(rawValue, field.values.map(_.value))
        }
      }
      MetadataForm(field.name, field.fullName.getOrElse(field.name), formValue, field.editable)
    })
  }

  def displayMetadataDependencies(consignmentId: UUID, fileId: UUID, fieldType: String) = secureAction.async { implicit request: Request[AnyContent] =>
    val dependencies = request.queryString.getOrElse("dependencies", Nil)
    getMetadata(consignmentId: UUID, request.token.bearerAccessToken, fieldType).flatMap(meta => {
      createPopulatedForm(consignmentId, fileId, fieldType, request.token.bearerAccessToken, meta.filter(m => dependencies.contains(m.name))).map(form => {
        Ok(views.html.standard.metadataFields(consignmentId, fileId, fieldType, form))
      })
    })
  }

  def displayMetadata(consignmentId: UUID, fileId: UUID, fieldType: String): Action[AnyContent] = secureAction.async {
    implicit request: Request[AnyContent] =>
      getMetadata(consignmentId, request.token.bearerAccessToken, fieldType).flatMap(metadata => {
        createPopulatedForm(consignmentId, fileId, fieldType, request.token.bearerAccessToken, metadata).map(form => {
          Ok(views.html.standard.metadataFields(consignmentId, fileId, fieldType, form))
        })
      })
  }

  private def createPopulatedForm(consignmentId: UUID, fileId: UUID, fieldType: String, token: BearerAccessToken, metadata: List[Metadata]): Future[List[MetadataForm]] = {
    fileMetadataService.getConsignmentMetadata(consignmentId, fileId, token, fieldType).map(fm => {
      val propertyMap = fm.map(f => f.propertyName -> Some(f.value)).toMap
      createForm(metadata, propertyMap)
    })
  }
}

object MetadataController {
  implicit class DateStringUtils(date: String) {
    def leftPad: String = date.toIntOption.map(d => f"$d%02d").getOrElse("")

    def inRange(start: Int, end: Int): Boolean = start until end contains date.toInt

    def isDay: Boolean = date.toIntOption.isDefined && inRange(1, 31)

    def isMonth: Boolean = date.toIntOption.isDefined && inRange(1, 13)

    def isYear: Boolean = date.toIntOption.isDefined
  }

  trait FormValue {
    def format: String
  }

  case class MetadataForm(name: String, fullName: String, value: FormValue, editable: Boolean)

  case class FormTextValue(value: String) extends FormValue {
    override def format: String = value
  }

  case class FormSelectValue(value: String, options: List[String]) extends FormValue {
    override def format: String = value
  }

  case class FormDateValue(day: String = "", month: String = "", year: String = "") extends FormValue {
    def format: String = if (isEmpty || partial) "" else s"$year-${month.leftPad}-${day.leftPad}"

    def partial: Boolean = day == "" || month == "" || year == ""

    def isEmpty: Boolean = day == "" && month == "" && year == ""

    def isValid: Boolean = {
      val date: Regex = """(\d\d\d\d)-(\d{1,2})-(\d{1,2})""".r
      format match {
        case date(year, month, day) => day.isDay && month.isMonth && year.isYear
        case _ => false
      }
    }
  }
}
