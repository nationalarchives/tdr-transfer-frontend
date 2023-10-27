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
}
