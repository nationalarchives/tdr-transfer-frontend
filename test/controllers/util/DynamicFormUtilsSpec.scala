package controllers.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import testUtils.FormTestData

import java.time.LocalDateTime
import scala.collection.immutable.ListMap

class DynamicFormUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {

  private val testData = new FormTestData()
  private val customMetadata = new FormTestData().setupCustomMetadata()
  private val displayProperties = new FormTestData().setupDisplayProperties()

  "formAnswersWithValidInputNames" should "returns all values passed into the request except restricted values" in {
    val rawFormWithCsrfToken = ListMap(
      "inputdate-testproperty3-day" -> List("3"),
      "inputdate-testproperty3-month" -> List("4"),
      "inputdate-testproperty3-year" -> List("2020"),
      "inputnumeric-testproperty6-years" -> List("4"),
      "inputmultiselect-testproperty8" -> List("TestValue 3"),
      "inputradio-testproperty7" -> List("Yes"),
      "inputtext-testproperty10" -> List("Some Text"),
      "inputtextarea-testproperty11" -> List("Lots of text"),
      "csrfToken" -> List("12345"),
      "tna-multi-select-search" -> List("12345"),
      "details" -> List("12345")
    )
    val dynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithCsrfToken)

    val expectedRestrictedValues = Set("csrfToken", "tna-multi-select-search", "details")

    rawFormWithCsrfToken.foreach { case (inputName, value) =>
      if (expectedRestrictedValues.contains(inputName)) {
        dynamicFormUtils.formAnswersWithValidInputNames.contains(inputName) should equal(false)
      } else {
        dynamicFormUtils.formAnswersWithValidInputNames(inputName) should equal(value)
      }
    }
  }

  "formAnswersWithValidInputNames" should "return values passed into the request so long as the input name has a prefix of 'input'" +
    "regardless of whether an implementation of the field exists" in {
      val unsupportedFieldType = "inputwrong"

      val rawFormWithoutCsrfToken = ListMap(
        s"$unsupportedFieldType-testproperty3-day" -> List("3"),
        "inputdate-testproperty3-month" -> List("5")
      )

      val dynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

      rawFormWithoutCsrfToken.foreach { case (inputName, value) =>
        dynamicFormUtils.formAnswersWithValidInputNames(inputName) should equal(value)
      }
    }

  "formAnswersWithValidInputNames" should "throw an exception if the input name does not have a prefix of 'input'" in {
    val fieldTypeWithIncorrectPrefix = "wrongprefixinputdate"

    val rawFormWithoutCsrfToken = ListMap(
      s"$fieldTypeWithIncorrectPrefix-testproperty3-day" -> List("3"),
      "inputdate-datemetadataname-month" -> List("5")
    )

    val dynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

    val thrownException: IllegalArgumentException =
      the[IllegalArgumentException] thrownBy dynamicFormUtils.formAnswersWithValidInputNames

    thrownException.getMessage should equal(s"$fieldTypeWithIncorrectPrefix is not a supported field type.")
  }

  private def instantiateDynamicFormsUtils(rawFormToMakeRequestWith: Map[String, Seq[String]], passInFieldsForAllMetadata: Boolean = true): DynamicFormUtils = {
    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest
        .apply(POST, s"/consignment/12345/additional-metadata/add")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val allMetadataAsFields: List[FormField] = new DisplayPropertiesUtils(displayProperties, customMetadata)
      .convertPropertiesToFormFields(displayProperties.filterNot(p => testData.dependencies().contains(p.propertyName)))
      .toList

    if (passInFieldsForAllMetadata) {
      new DynamicFormUtils(mockRequest, allMetadataAsFields)
    } else {
      val metadataUsedForFormAsFields: List[FormField] = allMetadataAsFields.filter { formField =>
        val rawFormWithoutCsrfToken: Map[String, Seq[String]] = rawFormToMakeRequestWith - "csrfToken"
        val metadataNames: List[String] = rawFormWithoutCsrfToken.keys.map { inputName => inputName.split("-")(1) }.toList
        metadataNames.contains(formField.fieldId)
      }
      new DynamicFormUtils(mockRequest, metadataUsedForFormAsFields)
    }
  }
}

case class MockFormValues(
    day: List[String] = List("3"),
    month: List[String] = List("4"),
    year: List[String] = List("2021"),
    day2: List[String] = List("7"),
    month2: List[String] = List("9"),
    year2: List[String] = List("2022"),
    numericTextBoxValue: List[String] = List("5"),
    multiSelectField: List[String] = List("dropdownValue"),
    radioValue: List[String] = List("yes"),
    textValue: List[String] = List("Some Text"),
    textAreaValue: List[String] = List("A large amount of text"),
    csrfToken: List[String] = List("12345")
)
