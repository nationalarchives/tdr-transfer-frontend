package controllers.util

import graphql.codegen.GetCustomMetadata
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.POST

import java.time.LocalDateTime
import scala.collection.immutable.ListMap

class DynamicFormUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {

  "formAnswersWithValidInputNames" should "returns all values passed into the request except the CSRF token" in {
    val rawFormWithCsrfToken = ListMap(
      "inputdate-testproperty3-day" -> List("3"),
      "inputdate-testproperty3-month" -> List("4"),
      "inputdate-testproperty3-year" -> List("2020"),
      "inputnumeric-testproperty6-years" -> List("4"),
      "inputdropdown-testproperty8" -> List("TestValue 3"),
      "inputradio-testproperty7" -> List("Yes"),
      "inputtext-testproperty10" -> List("Some Text"),
      "csrfToken" -> List("12345")
    )
    val dynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithCsrfToken)

    rawFormWithCsrfToken.foreach { case (inputName, value) =>
      if (inputName != "csrfToken") {
        dynamicFormUtils.formAnswersWithValidInputNames(inputName) should equal(value)
      } else {
        dynamicFormUtils.formAnswersWithValidInputNames.contains("csrfToken") should equal(false)
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

  "validateAndConvertSubmittedValuesToFormFields" should "throw an exception if the metadata name in the input name is not valid" in {
    val unsupportedMetadataName = "wrongmetadataname"

    val rawFormWithoutCsrfToken =
      ListMap(s"inputdate-$unsupportedMetadataName-day" -> List("3"))

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

    val thrownException: IllegalArgumentException =
      the[IllegalArgumentException] thrownBy dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)

    thrownException.getMessage should equal(s"Metadata name TestProperty1 does not exist in submitted form values")
  }

  "validateAndConvertSubmittedValuesToFormFields" should "not throw an exception if the metadata name (in the input name) " +
    "contains/ends with a 'unitType'" in {

      val rawFormWithCsrfToken = ListMap(
        "inputdate-fieldidcontainsdaymonthyearoryears-day" -> List("29"),
        "inputdate-fieldidcontainsdaymonthyearoryears-month" -> List("4"),
        "inputdate-fieldidcontainsdaymonthyearoryears-year" -> List("2020"),
        "inputnumeric-fieldidcontainsdaymonthoryear-years" -> List("4"),
        "inputdropdown-fieldidendswithday" -> List("TestValue 3"),
        "inputradio-fieldidendswithmonth" -> List("yes"),
        "inputtext-fieldidendswithyear" -> List("Some Text"),
        "csrfToken" -> List("12345")
      )

      val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest
          .apply(POST, s"/consignment/12345/additional-metadata/add")
          .withBody(AnyContentAsFormUrlEncoded(rawFormWithCsrfToken))

      val metadataUsedForFormAsFields = List(
        DateField(
          "fieldidcontainsdaymonthyearoryears",
          "fieldId contains day month and year",
          "We were previously using '.contains('day')' which meant that this type of form (above) would have showed the user the wrong error",
          multiValue = false,
          InputNameAndValue("Day", "", "DD"),
          InputNameAndValue("Month", "", "MM"),
          InputNameAndValue("Year", "", "YYYY"),
          isRequired = true
        ),
        TextField("fieldidcontainsdaymonthoryear", "", "", multiValue = false, InputNameAndValue("years", "0", "0"), "numeric", isRequired = true),
        DropdownField("fieldidendswithday", "", "", multiValue = true, Seq(InputNameAndValue("TestValue 3", "TestValue 3")), None, isRequired = true),
        RadioButtonGroupField("fieldidendswithmonth", "", "", multiValue = false, Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")), "yes", isRequired = true),
        TextField("fieldidendswithyear", "", "", multiValue = false, InputNameAndValue("text", ""), "text", isRequired = true)
      )

      val dynamicFormUtils = new DynamicFormUtils(mockRequest, metadataUsedForFormAsFields)
      val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)

      validatedForm.foreach(_.fieldErrors shouldBe Nil)
    }

  "validateAndConvertSubmittedValuesToFormFields" should "return a 'no-value selected'-selection-related error for selection fields if they are missing" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(dropdownValue = List(""))
    )

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.foreach {
      case dropdownField: DropdownField =>
        dropdownField.fieldErrors should equal(List(s"There was no value selected for the ${dropdownField.fieldName}."))
      case _ =>
    }
  }

  "validateAndConvertSubmittedValuesToFormFields" should "throws an exception if value selected is not one of the official options" in {
    // This test is to try and catch bad actors
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(dropdownValue = List("hello"))
    )

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.foreach {
      case dropdownField: DropdownField =>
        dropdownField.fieldErrors should equal(List(s"Option 'hello' was not an option provided to the user."))
      case _ =>
    }
  }

  "validateAndConvertSubmittedValuesToFormFields" should "return the selection-related values and no errors for selection fields if they are present" in {
    val mockFormValues = MockFormValues()
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      mockFormValues
    )

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.exists(_.fieldErrors.nonEmpty) should be(false)
    verifyUpdatedFormFields(mockFormValues, validatedForm)
  }

  "validateAndConvertSubmittedValuesToFormFields" should "return a 'no-number'-related error for the numeric fields that are missing values" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(day = List(""), numericTextBoxValue = List(""))
    )

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    val dateField = validatedForm.find(_.isInstanceOf[DateField]).get
    dateField.fieldErrors should equal(List(s"There was no number entered for the Day."))
  }

  "validateAndConvertSubmittedValuesToFormFields" should "return an error when future date is not allowed" in {
    val dateTime = LocalDateTime.now().plusDays(1)
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-TestProperty3-day" -> List(dateTime.getDayOfMonth.toString),
        "inputdate-TestProperty3-month" -> List(dateTime.getMonthValue.toString),
        "inputdate-TestProperty3-year" -> List(dateTime.getYear.toString)
      )

    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest
        .apply(POST, s"/consignment/12345/additional-metadata/add")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val mockProperties: List[GetCustomMetadata.customMetadata.CustomMetadata] = new CustomMetadataUtilsSpec().allProperties.find(_.name == "TestProperty3").toList
    val allMetadataAsFields: List[FormField] = new CustomMetadataUtils(mockProperties).convertPropertiesToFormFields(mockProperties.toSet)
    val metaDataField = allMetadataAsFields.head match {
      case e: DateField => e.copy(isFutureDateAllowed = false)
    }

    val dynamicFormUtils = new DynamicFormUtils(mockRequest, List(metaDataField))

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    val dateField = validatedForm.find(_.isInstanceOf[DateField]).get
    dateField.fieldErrors should equal(List(s"Test Property 3 date cannot be a future date."))
  }

  "validateAndConvertSubmittedValuesToFormFields" should "return a 'whole number'-related error for the numeric fields that are missing values" in {
    // Shouldn't be possible on client-side due to "type:numeric" attribute added to HTML, but in case that is removed
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(month = List("b4"), year = List("2r"), numericTextBoxValue = List("1.5"), day2 = List("000"), month2 = List("e"), year2 = List("-"))
    )

    val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)

    val dateField = validatedForm.filter(_.isInstanceOf[DateField])
    dateField.head.fieldErrors should equal(List(s"Month entered must be a whole number."))
    dateField(1).fieldErrors should equal(List(s"000 is an invalid Day number."))

    val textField = validatedForm.find(_.isInstanceOf[TextField]).get
    textField.fieldErrors should equal(List(s"years entered must be a whole number."))
  }

  "validateAndConvertSubmittedValuesToFormFields" should "return all values, with any value submitted with whitespace " +
    "at the beginning/end of it, trimmed and no errors" in {
      val mockFormValues = MockFormValues(
        day = List(" 3"),
        month = List("4 "),
        year = List(" 2021 "),
        numericTextBoxValue = List("       5"),
        day2 = List("   7   ")
      )
      val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
        mockFormValues
      )

      val validatedForm = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
      validatedForm.exists(_.fieldErrors.nonEmpty) should be(false)
      verifyUpdatedFormFields(mockFormValues, validatedForm)
    }

  private def verifyUpdatedFormFields(mockFormValues: MockFormValues, validatedForm: List[FormField]): Unit = {

    val date = validatedForm.find(_.fieldId == "TestProperty3").map(_.asInstanceOf[DateField]).get
    date.day.value should equal(mockFormValues.day.head.trim)
    date.month.value should equal(mockFormValues.month.head.trim)
    date.year.value should equal(mockFormValues.year.head.trim)

    val date2 = validatedForm.find(_.fieldId == "TestProperty5").map(_.asInstanceOf[DateField]).get
    date2.day.value should equal(mockFormValues.day2.head.trim)
    date2.month.value should equal(mockFormValues.month2.head.trim)
    date2.year.value should equal(mockFormValues.year2.head.trim)

    val text = validatedForm.find(_.fieldId == "TestProperty1").map(_.asInstanceOf[TextField]).get
    text.nameAndValue.value should equal(mockFormValues.numericTextBoxValue.head.trim)

    val dropdownField = validatedForm.find(_.fieldId == "TestProperty4").map(_.asInstanceOf[DropdownField]).get
    dropdownField.selectedOption.map(_.value).get should equal(mockFormValues.dropdownValue.head)

    val radioButtonGroupField = validatedForm.find(_.fieldId == "TestProperty2").map(_.asInstanceOf[RadioButtonGroupField]).get
    radioButtonGroupField.selectedOption should equal(mockFormValues.radioValue.head)
  }

  private def generateFormAndSendRequest(mockFormValues: MockFormValues): (ListMap[String, List[String]], DynamicFormUtils) = {
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-TestProperty3-day" -> mockFormValues.day,
        "inputdate-TestProperty3-month" -> mockFormValues.month,
        "inputdate-TestProperty3-year" -> mockFormValues.year,
        "inputdate-TestProperty5-day" -> mockFormValues.day2,
        "inputdate-TestProperty5-month" -> mockFormValues.month2,
        "inputdate-TestProperty5-year" -> mockFormValues.year2,
        "inputnumeric-TestProperty1-years" -> mockFormValues.numericTextBoxValue,
        "inputdropdown-TestProperty4" -> mockFormValues.dropdownValue,
        "inputradio-TestProperty2" -> mockFormValues.radioValue,
        "csrfToken" -> mockFormValues.csrfToken
      )

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormToMakeRequestWith)

    (rawFormToMakeRequestWith.-("csrfToken"), dynamicFormUtils)
  }

  private def instantiateDynamicFormsUtils(rawFormToMakeRequestWith: Map[String, Seq[String]], passInFieldsForAllMetadata: Boolean = true): DynamicFormUtils = {
    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest
        .apply(POST, s"/consignment/12345/additional-metadata/add")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val mockProperties: List[GetCustomMetadata.customMetadata.CustomMetadata] = new CustomMetadataUtilsSpec().allProperties.take(5)
    val customMetadataUtils: CustomMetadataUtils = new CustomMetadataUtils(mockProperties)

    val allMetadataAsFields: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(mockProperties.toSet)

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
    numericTextBoxValue: List[String] = List("5"),
    dropdownValue: List[String] = List("TestValue 3"),
    radioValue: List[String] = List("yes"),
    day2: List[String] = List("7"),
    month2: List[String] = List("9"),
    year2: List[String] = List("2022"),
    textValue: List[String] = List("Some Text"),
    csrfToken: List[String] = List("12345")
)
