package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetDisplayProperties.displayProperties.{DisplayProperties, Variables}
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DisplayPropertiesService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val displayPropertiesClient: GraphQLClient[dp.Data, Variables] = graphqlConfiguration.getClient[dp.Data, dp.Variables]()

  implicit class AttributeHelper(attribute: Option[DisplayProperties.Attributes]) {
    def getStringValue: String = {
      attribute match {
        case Some(a) => a.value.getOrElse("")
        case _       => ""
      }
    }

    def getBoolean: Boolean = {
      attribute match {
        case Some(a) => a.value.contains("true")
        case _       => false
      }
    }
  }

  private def toDataType(dataType: Option[String]): DataType = {
    dataType match {
      case Some("text")     => Text
      case Some("integer")  => Integer
      case Some("boolean")  => Boolean
      case Some("datetime") => DateTime
      case _                => throw new Exception(s"Invalid data type $dataType")
    }
  }

  def toDisplayProperty(p: DisplayProperties): DisplayProperty = {
    val attributes = p.attributes

    val active: Boolean = attributes.find(_.attribute == "Active").getBoolean

    val componentType: String = attributes.find(_.attribute == "ComponentType").getStringValue

    val dataType: DataType = {
      attributes.find(_.attribute == "Datatype") match {
        case Some(dt) => toDataType(dt.value)
        case _        => throw new Exception(s"No datatype")
      }
    }

    val description: String = attributes.find(_.attribute == "Description").getStringValue

    val editable: Boolean = attributes.find(_.attribute == "Editable").getBoolean

    val group: String = attributes.find(_.attribute == "Group").getStringValue

    val guidance: String = attributes.find(_.attribute == "Guidance").getStringValue

    val unitType: String = attributes.find(_.attribute == "UnitType").getStringValue

    val label: String = attributes.find(_.attribute == "Label").getStringValue

    val multiValue: Boolean = attributes.find(_.attribute == "MultiValue").getBoolean

    val displayName: String = attributes.find(_.attribute == "Name").getStringValue

    val summary: String = attributes.find(_.attribute == "Summary").getStringValue

    val ordinal: Int = {
      attributes.find(_.attribute == "Ordinal") match {
        case Some(o) => o.value.get.toInt
        case _       => 0
      }
    }

    val propertyType: String = attributes.find(_.attribute == "PropertyType").getStringValue

    DisplayProperty(
      active,
      componentType,
      dataType,
      description,
      displayName,
      editable,
      group,
      guidance,
      label,
      multiValue,
      ordinal,
      p.propertyName,
      propertyType,
      unitType,
      summary
    )
  }

  private def displayPropertyFilter(dp: DisplayProperty, metadataType: Option[String]): Boolean = {
    dp.active && metadataType.forall(`type` => dp.propertyType.equalsIgnoreCase(`type`))
  }

  def getDisplayProperties(consignmentId: UUID, token: BearerAccessToken, metadataType: Option[String]): Future[List[DisplayProperty]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(displayPropertiesClient, dp.document, token, variables).map(data =>
      data.displayProperties
        .map(toDisplayProperty)
        .filter(displayPropertyFilter(_, metadataType))
        .sortBy(_.ordinal)
    )
  }
}

case class DisplayProperty(
    active: Boolean,
    componentType: String,
    dataType: DataType,
    description: String,
    displayName: String,
    editable: Boolean,
    group: String,
    guidance: String,
    label: String,
    multiValue: Boolean,
    ordinal: Int,
    propertyName: String,
    propertyType: String,
    unitType: String,
    summary: String
)
