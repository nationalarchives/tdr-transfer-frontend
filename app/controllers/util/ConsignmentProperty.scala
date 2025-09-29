package controllers.util

import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.validation.Metadata
import uk.gov.nationalarchives.tdr.validation.schema.MetadataValidationJsonSchema.ObjectMetadata
import uk.gov.nationalarchives.tdr.validation.schema.{JsonSchemaDefinition, MetadataValidationJsonSchema, ValidationError}

import java.util.Properties
import scala.io.Source

object ConsignmentProperty {
  lazy val tdrDataLoadHeaderMapper: String => String = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")
  private lazy val metadataConfiguration = ConfigUtils.loadConfiguration
  private lazy val properties = getMessageProperties

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
}
