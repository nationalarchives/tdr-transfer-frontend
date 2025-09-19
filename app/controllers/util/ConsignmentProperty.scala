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

  private def validateWithSchema(data: ObjectMetadata, schema: JsonSchemaDefinition): Map[String, List[ValidationError]] = {
    MetadataValidationJsonSchema.validateWithSingleSchema(schema, Set(data))
  }

  def validateNeutralCitationData(neutralCitationData: NeutralCitationData, schema: JsonSchemaDefinition): Map[String, List[ValidationError]] = {
    val data = ObjectMetadata(
      "data",
      Set(
        Metadata(NCN, neutralCitationData.neutralCitation.getOrElse("")),
        // need yes or no here ad Metadata value expects a string. Converted before final validation
        Metadata(NO_NCN, if (neutralCitationData.noNeutralCitation) "yes" else "no"),
        Metadata(JUDGMENT_REFERENCE, neutralCitationData.judgmentReference.getOrElse(""))
      )
    )
    validateWithSchema(data, schema)
  }

  def validationErrorMessage(errorOption: Option[ValidationError]): Option[String] = {
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
