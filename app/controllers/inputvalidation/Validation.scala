package controllers.inputvalidation

import controllers.inputvalidation.ErrorCode._
import org.apache.commons.lang3.BooleanUtils

import scala.util.control.Exception.allCatch

sealed trait DataType extends AnyRef

object DataType extends AnyRef {
  def get(dataType: String): DataType = {
    dataType match {
      case "Integer"  => Integer
      case "DateTime" => DateTime
      case "Text"     => Text
      case "Boolean"  => Boolean
      case "Decimal"  => Decimal
    }
  }

  case object Integer extends AnyRef with DataType with Product with Serializable
  case object DateTime extends AnyRef with DataType with Product with Serializable
  case object Text extends AnyRef with DataType with Product with Serializable
  case object Boolean extends AnyRef with DataType with Product with Serializable
  case object Decimal extends AnyRef with DataType with Product with Serializable
}

case class MetadataPropertyCriteria(
    name: String,
    displayName: String,
    active: Boolean,
    dataType: DataType,
    editable: Boolean,
    propertyType: String,
    required: Boolean,
    requiredProperty: Option[MetadataPropertyCriteria] = None,
    dependencies: Option[Map[String, List[MetadataPropertyCriteria]]] = None
)

object Validation {

  def validateClosure(row: Map[String, String], dp: List[MetadataPropertyCriteria]): Map[String, String] = {
    val closureStatus = row.get("ClosureType")
    val error: Map[String, String] = closureStatus match {
      case Some("Open") =>
        val invalidData = row.removed("ClosureType").exists(p => p._2 != "" && p._2 != "No")
        if (invalidData) {
          Map("ClosureType" -> CLOSURE_PROPERTIES_EXIST_WHEN_FILE_IS_OPEN)
        } else {
          Map.empty
        }
      case Some("Closed") => validateRow(row, dp)
      case None           => Map("ClosureType" -> CLOSURE_STATUS_IS_MISSING)
    }
    error.filter(_._2 != "")
  }

  def validateRow(row: Map[String, String], dpList: List[MetadataPropertyCriteria]): Map[String, String] = {
    row.map(data => {
      val dp = dpList.find(_.name == data._1).get
      val value = data._2
      val error = dp.dataType match {
        case DataType.Integer =>
          value match {
            case "" if dp.required                  => EMPTY_VALUE_ERROR
            case t if allCatch.opt(t.toInt).isEmpty => NUMBER_ONLY_ERROR
            case t if t.toInt < 0                   => NEGATIVE_NUMBER_ERROR
            case _                                  => ""
          }
        case DataType.Boolean =>
          value match {
            case "" if dp.required => NO_OPTION_SELECTED_ERROR
            case v =>
              dp.dependencies
                .flatMap(
                  _.find(dependencies => dependencies._1.toBoolean == BooleanUtils.toBoolean(v))
                    .flatMap(dependencies => validateRow(row.filter(r => dependencies._2.exists(_.name == r._1)), dependencies._2.map(_.copy(required = true))).values.headOption)
                )
                .getOrElse("")
          }
        case DataType.Text =>
          value match {
            case "" if dp.required => EMPTY_VALUE_ERROR
            case _                 => ""
          }
        case _ => ""
      }
      dp.name -> error
    })
  }
}

object ErrorCode {

  val CLOSURE_STATUS_IS_MISSING = "CLOSURE_STATUS_IS_MISSING"
  val CLOSURE_PROPERTIES_EXIST_WHEN_FILE_IS_OPEN = "CLOSURE_PROPERTIES_EXIST_WHEN_FILE_IS_OPEN"
  val CLOSURE_PROPERTIES_DO_NOT_EXIST_WHEN_FILE_IS_CLOSED = "CLOSURE_PROPERTIES_DO_NOT_EXIST_WHEN_FILE_IS_CLOSED"
  val NUMBER_ONLY_ERROR = "NUMBER_ONLY_ERROR"
  val NEGATIVE_NUMBER_ERROR = "NEGATIVE_NUMBER_ERROR"
  val EMPTY_VALUE_ERROR = "EMPTY_VALUE_ERROR"
  val NO_OPTION_SELECTED_ERROR = "NO_OPTION_SELECTED_ERROR"

}
