package controllers.util

import graphql.codegen.types.DataType._
import org.scalatest.PrivateMethodTester
import services.DisplayProperty
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.validation
import uk.gov.nationalarchives.tdr.validation.MetadataCriteria

class MetadataValidationUtilsSpec extends FrontEndTestHelper with PrivateMethodTester {

  "createMetadataValidation" should {
    "create metadata validation with criteria" in {

      val metadataValidationUtils = MetadataValidationUtils.createMetadataValidation(getDisplayProperties, getCustomMetadataDataObject.customMetadata)
      metadataValidationUtils must not be null
    }
  }

  "createCriteria" should {
    "create criteria for given display properties" in {

      val createCriteria = PrivateMethod[List[MetadataCriteria]](Symbol("createCriteria"))

      val criteria = MetadataValidationUtils invokePrivate createCriteria(getClosureDisplayProperties, getCustomMetadataDataObject.customMetadata, getDisplayProperties)

      criteria must be(List(expectedCriteria))
    }
  }

  def getClosureDisplayProperties: List[DisplayProperty] = {
    List(
      DisplayProperty(true, "", Text, "", "", true, "1", None, "", false, 1, "ClosureType", "closure", "", "", "", None, true)
    )
  }

  def getDisplayProperties: List[DisplayProperty] = {
    List(
      DisplayProperty(true, "", Text, "", "", true, "1", None, "", false, 1, "ClosureType", "closure", "", "", "", None, true),
      DisplayProperty(true, "", Text, "", "", true, "2", None, "", true, 1, "FoiExemptionCode", "closure", "", "", "", None, true),
      DisplayProperty(true, "", Boolean, "", "", true, "2", None, "", false, 1, "TitleClosed", "closure", "", "", "", None, true),
      DisplayProperty(true, "", Text, "", "", true, "3", None, "", false, 1, "TitleAlternate", "closure", "", "", "", None, true),
      DisplayProperty(true, "", DateTime, "", "", true, "1", None, "", false, 1, "EndDate", "descriptive", "", "", "", None, true),
      DisplayProperty(true, "", Text, "", "", true, "1", None, "", false, 1, "Description", "descriptive", "", "", "", None, true)
    )
  }
  def expectedCriteria: MetadataCriteria = {
    val titleAlternate = MetadataCriteria("TitleAlternate", validation.Text, false, false, false, Nil, dependencies = Some(Map()))
    val titleClosed =
      MetadataCriteria("TitleClosed", validation.Boolean, true, false, false, List("yes", "no"), dependencies = Some(Map("True" -> List(titleAlternate), "False" -> Nil)))
    val foiExemptionCode =
      MetadataCriteria("FoiExemptionCode", validation.Text, true, false, true, List("mock code1", "mock code2"), dependencies = Some(Map("mock code1" -> Nil, "mock code2" -> Nil)))

    val closureDependencies = Map("Closed" -> List(foiExemptionCode, titleClosed), "Open" -> List(titleClosed))
    MetadataCriteria(
      "ClosureType",
      validation.Text,
      true,
      false,
      false,
      List("Closed", "Open"),
      dependencies = Some(closureDependencies)
    )
  }
}
