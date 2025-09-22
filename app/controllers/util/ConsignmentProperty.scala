package controllers.util

import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.validation.Metadata
import uk.gov.nationalarchives.tdr.validation.schema.MetadataValidationJsonSchema.ObjectMetadata
import uk.gov.nationalarchives.tdr.validation.schema.{JsonSchemaDefinition, MetadataValidationJsonSchema, ValidationError}

import java.util.Properties
import scala.io.Source

object ConsignmentProperty {
  lazy val tdrDataLoadHeaderMapper: String => String = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")
  private lazy val properties = getMessageProperties
  private lazy val metadataConfiguration = ConfigUtils.loadConfiguration
  val NCN = "judgment_neutral_citation"
  val NO_NCN = "judgment_no_neutral_citation"
  val JUDGMENT_REFERENCE = "judgment_reference"
  val JUDGMENT_TYPE = "judgment_type"
  val JUDGMENT_UPDATE = "judgment_update"
  val JUDGMENT_UPDATE_TYPE = "judgment_update_type"
  val JUDGMENT_UPDATE_DETAILS = "judgment_update_details"

  private def validateWithSchema(data: ObjectMetadata, schema: JsonSchemaDefinition): Map[String, List[ValidationError]] = {
    MetadataValidationJsonSchema.validateWithSingleSchema(schema, Set(data))
  }

  def validateFormData(data: Map[String, String], schema: List[JsonSchemaDefinition]): Map[String, List[ValidationError]] = {
    val objectMetadata = ObjectMetadata("data", data.map(kv => Metadata(kv._1, kv._2)).toSet)
    schema.foldLeft(Map.empty[String, List[ValidationError]]) { (error, schema) =>
      val errors = validateWithSchema(objectMetadata, schema)
      error ++ errors.map { case (k, v) => k -> (v ++ error.getOrElse(k, Nil)) }
    }
  }

  def getErrorMessage(errorOption: Option[ValidationError]): Option[String] = {
    errorOption.map(ve => {
      properties.getProperty(s"${ve.validationProcess}.${ve.property}.${ve.errorKey}", s"${ve.validationProcess}.${ve.property}.${ve.errorKey}")
    })
  }

  private def getMessageProperties: Properties = {
    val source = Source.fromURL(getClass.getResource("/validation-messages/validation-messages.properties"))
    val properties = new Properties()
    properties.load(source.bufferedReader())
    properties
  }

  case class NeutralCitationData(neutralCitation: Option[String] = None, noNeutralCitation: Boolean = false, judgmentReference: Option[String] = None)
}
