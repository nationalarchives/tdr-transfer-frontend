package controllers.util

import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import testUtils.FormTestData

class DisplayPropertiesUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val customMetadata = new FormTestData().setupCustomMetadata()
  private val displayProperties = new FormTestData().setupDisplayProperties()
  private val displayPropertiesUtils = new DisplayPropertiesUtils(displayProperties, customMetadata)

  "convertPropertiesToFormFields" should "return the list of properties requested" in {
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    fields.size shouldBe 1
  }
}
