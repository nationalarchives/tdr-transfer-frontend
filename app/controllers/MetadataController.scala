package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import controllers.MetadataController._
import graphql.codegen.Metadata.metadata.Metadata
import graphql.codegen.types.DataType
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

  implicit class OptionUtils(opt: Option[String]) {
    def noneIfBlank: Option[String] = opt.flatMap(s => if(s.isBlank) {None} else {Some(s)})
  }


  def metadataHome(consignmentId: UUID, fileId: UUID): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.standard.metadata(consignmentId, fileId))
  }

  def submitMetadata(consignmentId: UUID, fileId: UUID, fieldType: String): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map())
    val mappedForm: Map[String, Option[String]] = form.map(f => f._1 -> f._2.headOption)
    getMetadata(consignmentId, request.token.bearerAccessToken, fieldType).flatMap(m => {
      val errors: List[(String, String)] = m.flatMap(metadata => {
        metadata.dataType match {
          case DataType.DateTime =>
            val dateValues: List[(String, Option[String])] = List("day", "month", "year").map(name => {
              name -> mappedForm(s"${metadata.name}-$name").noneIfBlank
            })
            val emptyValues = dateValues.filter(_._2.isEmpty)
            if(List(1,2)contains emptyValues.length) {
              emptyValues.map(e => e._1 -> s"${e._1.capitalize} cannot be empty")
            } else {
              Nil
            }
          case _ => Nil
        }
      })
      createForm(consignmentId, request.token.bearerAccessToken, fieldType, mappedForm).map(newForm => {
        if(errors.nonEmpty) {
          BadRequest(views.html.standard.metadataFields(consignmentId, fileId, fieldType, newForm, errors.toMap))
        } else {
          Ok
        }
      })
    }
    )
  }

  private def getMetadata(consignmentId: UUID, token: BearerAccessToken, fieldType: String): Future[List[Metadata]] = cache.get[List[Metadata]](consignmentId.toString).flatMap {
    case Some(metadata) =>
      Future(metadata)
    case None =>

      for {
        metadata <- metadataService.getConsignmentMetadataFields(token)
        _ <- cache.set(consignmentId.toString, metadata, 1.second)
      } yield metadata.filter(m => m.propertyGroup.getOrElse("").toLowerCase == fieldType && m.propertyType != graphql.codegen.types.PropertyType.System)
  }

  def createForm(consignmentId: UUID, token: BearerAccessToken, fieldType: String, propertyMap: Map[String, Option[String]]): Future[List[MetadataForm]] = {
    getMetadata(consignmentId, token, fieldType).map(metadataFields => {
      metadataFields.map(field => {
        val rawValue: String = propertyMap.getOrElse(field.name, field.defaultValue).getOrElse("")
        val formValue: FormValue = field.dataType match {
          case DataType.DateTime =>
            val date: Regex = """(\d\d\d\d)-(\d\d)-(\d\d)""".r
            rawValue match {
              case date(year, month, day) => FormDateValue(day, month, year)
              case _ => FormDateValue()
            }
          case _ => if(field.values.length <= 1) {
            FormTextValue(rawValue)
          } else {
            FormSelectValue(rawValue, field.values.map(_.value))
          }
        }
        MetadataForm(field.name, field.fullName.getOrElse(field.name), formValue, field.editable)
      })
    })

  }

  def displayMetadata(consignmentId: UUID, fileId: UUID, fieldType: String): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    fileMetadataService.getConsignmentMetadata(consignmentId, fileId, request.token.bearerAccessToken).flatMap(fm => {
      val propertyMap = fm.map(f => f.propertyName -> Some(f.value)).toMap
      createForm(consignmentId, request.token.bearerAccessToken, fieldType, propertyMap)
    }).map(form => {
      Ok(views.html.standard.metadataFields(consignmentId, fileId, fieldType, form))
    })
  }
}

object MetadataController {
  trait FormValue
  case class MetadataForm(name: String, fullName: String, value: FormValue, editable: Boolean)
  case class FormTextValue(value: String) extends FormValue
  case class FormSelectValue(value: String, options: List[String]) extends FormValue
  case class FormDateValue(day: String = "", month: String = "", year: String = "") extends FormValue
}
