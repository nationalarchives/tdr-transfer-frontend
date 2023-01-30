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

  "convertSubmittedValuesToFormFields" should "not throw an exception if the metadata name (in the input name) " +
    "contains/ends with a 'unitType'" in {

      val rawFormWithCsrfToken = ListMap(
        "inputdate-fieldidcontainsdaymonthyearoryears-day" -> List("29"),
        "inputdate-fieldidcontainsdaymonthyearoryears-month" -> List("4"),
        "inputdate-fieldidcontainsdaymonthyearoryears-year" -> List("2020"),
        "inputnumeric-fieldidcontainsdaymonthoryear-years" -> List("4"),
        "inputmultiselect-fieldidendswithday" -> List("TestValue 3"),
        "inputradio-fieldidendswithmonth" -> List("yes"),
        "inputtext-fieldidendswithyear" -> List("Some Text"),
        "inputtextarea-textarea" -> List("Lots of text"),
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
        MultiSelectField("fieldidendswithday", "", "", "", multiValue = true, Seq(InputNameAndValue("TestValue 3", "TestValue 3")), None, isRequired = true),
        RadioButtonGroupField(
          "fieldidendswithmonth",
          "",
          "",
          "",
          multiValue = false,
          Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
          "yes",
          isRequired = true
        ),
        TextField("fieldidendswithyear", "", "", multiValue = false, InputNameAndValue("text", ""), "text", isRequired = true)
      )

      val dynamicFormUtils = new DynamicFormUtils(mockRequest, metadataUsedForFormAsFields)
      val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)

      validatedForm.foreach(_.fieldErrors shouldBe Nil)
    }

  "convertSubmittedValuesToFormFields" should "return a 'no-value selected'-selection-related error for selection fields if they are missing" in {
    val mockFormValues = MockFormValues()
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-FoiExemptionAsserted-day" -> mockFormValues.day,
        "inputdate-FoiExemptionAsserted-month" -> mockFormValues.month,
        "inputdate-FoiExemptionAsserted-year" -> mockFormValues.year,
        "inputdate-ClosureStartDate-day" -> mockFormValues.day2,
        "inputdate-ClosureStartDate-month" -> mockFormValues.month2,
        "inputdate-ClosureStartDate-year" -> mockFormValues.year2,
        "inputnumeric-ClosurePeriod" -> mockFormValues.numericTextBoxValue,
        "inputtextarea-TextArea" -> mockFormValues.textAreaValue,
        "inputradio-Radio" -> mockFormValues.radioValue,
        "inputtext-Radio-TestProperty2-yes" -> List("title"),
        "csrfToken" -> mockFormValues.csrfToken
      )

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormToMakeRequestWith)

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.foreach {
      case multiSelectField: MultiSelectField =>
        multiSelectField.fieldErrors should equal(List(s"There was no value selected for the ${multiSelectField.fieldName}."))
      case _ =>
    }
  }

  "convertSubmittedValuesToFormFields" should "not return an error if the user selected multiple values" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(multiSelectField = List("dropdownValue2", "dropdownValue"))
    )

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.foreach {
      case multiSelectField: MultiSelectField =>
        multiSelectField.fieldErrors should be(Nil)
        multiSelectField.selectedOption should be(Some(List(InputNameAndValue("dropdownValue", "dropdownValue"), InputNameAndValue("dropdownValue2", "dropdownValue2"))))
      case _ =>
    }
  }

  "convertSubmittedValuesToFormFields" should "throws an exception if value selected is not one of the official options" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(multiSelectField = List("hello"))
    )

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.foreach {
      case multiSelectField: MultiSelectField =>
        multiSelectField.fieldErrors should equal(List(s"Option 'hello' was not an option provided to the user."))
      case _ =>
    }
  }

  "convertSubmittedValuesToFormFields" should "return the selection-related values and no errors for selection fields if they are present" in {
    val mockFormValues = MockFormValues(radioValue = List("no"))
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      mockFormValues
    )

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.exists(_.fieldErrors.nonEmpty) should be(false)
    verifyUpdatedFormFields(mockFormValues, validatedForm)
  }

  "convertSubmittedValuesToFormFields" should "return a 'no-number'-related error for the numeric fields that are missing values" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(day = List(""), numericTextBoxValue = List(""))
    )

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    val dateField = validatedForm.find(_.isInstanceOf[DateField]).get
    dateField.fieldErrors should equal(List(s"There was no number entered for the Day."))
  }

  "convertSubmittedValuesToFormFields" should "not validate the field if it is hidden" in {
    val rawFormToMakeRequestWith =
      ListMap(
        "inputradio-Radio" -> List("exclude") // Set value as "exclude" for the hidden field
      )

    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest
        .apply(POST, s"/consignment/12345/additional-metadata/add")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val allMetadataAsFields: List[FormField] =
      new DisplayPropertiesUtils(displayProperties.filter(_.propertyName == "Radio"), customMetadata).convertPropertiesToFormFields().toList

    val dynamicFormUtils = new DynamicFormUtils(mockRequest, allMetadataAsFields)

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    val dateField = validatedForm.find(_.isInstanceOf[RadioButtonGroupField]).get
    dateField.fieldErrors.isEmpty should be(true)
  }

  "convertSubmittedValuesToFormFields" should "not return an error if the value is present for the dependency" in {
    val mockFormValues = MockFormValues(radioValue = List("yes"))
    val dependencyFormValue = "title"
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-FoiExemptionAsserted-day" -> mockFormValues.day,
        "inputdate-FoiExemptionAsserted-month" -> mockFormValues.month,
        "inputdate-FoiExemptionAsserted-year" -> mockFormValues.year,
        "inputdate-ClosureStartDate-day" -> mockFormValues.day2,
        "inputdate-ClosureStartDate-month" -> mockFormValues.month2,
        "inputdate-ClosureStartDate-year" -> mockFormValues.year2,
        "inputnumeric-ClosurePeriod" -> mockFormValues.numericTextBoxValue,
        "inputradio-Radio" -> mockFormValues.radioValue,
        "inputtext-Radio-TestProperty2-yes" -> List(dependencyFormValue),
        "inputmultiselect-Dropdown" -> mockFormValues.multiSelectField,
        "inputtextarea-TextArea" -> mockFormValues.textAreaValue,
        "csrfToken" -> mockFormValues.csrfToken
      )

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormToMakeRequestWith)

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.exists(_.fieldErrors.isEmpty) should be(true)
    verifyUpdatedFormFields(mockFormValues, validatedForm)

    val radioButtonGroupField = validatedForm.find(_.fieldId == "Radio").map(_.asInstanceOf[RadioButtonGroupField]).get
    val textDependency = radioButtonGroupField.dependencies("yes").head.asInstanceOf[TextField]
    textDependency.nameAndValue.value should be(dependencyFormValue)
  }

  "convertSubmittedValuesToFormFields" should "return an error if the value is missing for the dependency" in {
    val mockFormValues = MockFormValues(radioValue = List("yes"))

    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-FoiExemptionAsserted-day" -> mockFormValues.day,
        "inputdate-FoiExemptionAsserted-month" -> mockFormValues.month,
        "inputdate-FoiExemptionAsserted-year" -> mockFormValues.year,
        "inputdate-ClosureStartDate-day" -> mockFormValues.day2,
        "inputdate-ClosureStartDate-month" -> mockFormValues.month2,
        "inputdate-ClosureStartDate-year" -> mockFormValues.year2,
        "inputnumeric-ClosurePeriod" -> mockFormValues.numericTextBoxValue,
        "inputtext-Radio-TestProperty2-yes" -> Nil,
        "inputmultiselect-Dropdown" -> mockFormValues.multiSelectField,
        "inputtextarea-TextArea" -> mockFormValues.textAreaValue,
        "inputradio-Radio" -> mockFormValues.radioValue,
        "csrfToken" -> mockFormValues.csrfToken
      )

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormToMakeRequestWith)

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    validatedForm.exists(_.fieldErrors.nonEmpty) should be(true)
    val field = validatedForm.find(_.isInstanceOf[RadioButtonGroupField]).get
    field.fieldErrors should equal(List("There was no text entered for the TestProperty2."))
  }

  "convertSubmittedValuesToFormFields" should "return an error when future date is not allowed" in {
    val dateTime = LocalDateTime.now().plusDays(1)
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-FoiExemptionAsserted-day" -> List(dateTime.getDayOfMonth.toString),
        "inputdate-FoiExemptionAsserted-month" -> List(dateTime.getMonthValue.toString),
        "inputdate-FoiExemptionAsserted-year" -> List(dateTime.getYear.toString)
      )

    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest
        .apply(POST, s"/consignment/12345/additional-metadata/add")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val allMetadataAsFields: List[FormField] = new DisplayPropertiesUtils(displayProperties, customMetadata).convertPropertiesToFormFields(displayProperties).toList
    val metaDataField = allMetadataAsFields.find(_.fieldId == "FoiExemptionAsserted").head match {
      case e: DateField => e.copy(isFutureDateAllowed = false)
    }

    val dynamicFormUtils = new DynamicFormUtils(mockRequest, List(metaDataField))

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
    val dateField = validatedForm.find(_.isInstanceOf[DateField]).get
    dateField.fieldErrors should equal(List(s"Date Display date cannot be a future date."))
  }

  "convertSubmittedValuesToFormFields" should "return a 'whole number'-related error for the numeric fields that are missing values" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(month = List("b4"), year = List("2r"), numericTextBoxValue = List("1.5"), day2 = List("000"), month2 = List("e"), year2 = List("-"))
    )

    val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)

    val dateField = validatedForm.filter(_.isInstanceOf[DateField])
    dateField.head.fieldErrors should equal(List(s"Month entered must be a whole number."))
    dateField(1).fieldErrors should equal(List(s"000 is an invalid Day number."))

    val textField = validatedForm.find(_.isInstanceOf[TextField]).get
    textField.fieldErrors should equal(List(s"years entered must be a whole number."))
  }

  "convertSubmittedValuesToFormFields" should "return all values, with any value submitted with whitespace " +
    "at the beginning/end of it, trimmed and no errors" in {
      val mockFormValues = MockFormValues(
        day = List(" 3"),
        month = List("4 "),
        year = List(" 2021 "),
        numericTextBoxValue = List("       5"),
        day2 = List("   7   "),
        radioValue = List("no")
      )
      val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
        mockFormValues
      )

      val validatedForm = dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames)
      validatedForm.exists(_.fieldErrors.nonEmpty) should be(false)
      verifyUpdatedFormFields(mockFormValues, validatedForm)
    }

  private def verifyUpdatedFormFields(mockFormValues: MockFormValues, validatedForm: List[FormField]): Unit = {

    val date = validatedForm.find(_.fieldId == "FoiExemptionAsserted").map(_.asInstanceOf[DateField]).get
    date.day.value should equal(mockFormValues.day.head.trim)
    date.month.value should equal(mockFormValues.month.head.trim)
    date.year.value should equal(mockFormValues.year.head.trim)

    val date2 = validatedForm.find(_.fieldId == "ClosureStartDate").map(_.asInstanceOf[DateField]).get
    date2.day.value should equal(mockFormValues.day2.head.trim)
    date2.month.value should equal(mockFormValues.month2.head.trim)
    date2.year.value should equal(mockFormValues.year2.head.trim)

    val text = validatedForm.find(_.fieldId == "ClosurePeriod").map(_.asInstanceOf[TextField]).get
    text.nameAndValue.value should equal(mockFormValues.numericTextBoxValue.head.trim)

    val multiSelectField = validatedForm.find(_.fieldId == "Dropdown").map(_.asInstanceOf[MultiSelectField]).get
    multiSelectField.selectedOption.map(_.map(_.value)).get should equal(mockFormValues.multiSelectField)

    val radioButtonGroupField = validatedForm.find(_.fieldId == "Radio").map(_.asInstanceOf[RadioButtonGroupField]).get
    radioButtonGroupField.selectedOption should equal(mockFormValues.radioValue.head)
  }

  private def generateFormAndSendRequest(mockFormValues: MockFormValues): (ListMap[String, List[String]], DynamicFormUtils) = {
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-FoiExemptionAsserted-day" -> mockFormValues.day,
        "inputdate-FoiExemptionAsserted-month" -> mockFormValues.month,
        "inputdate-FoiExemptionAsserted-year" -> mockFormValues.year,
        "inputdate-ClosureStartDate-day" -> mockFormValues.day2,
        "inputdate-ClosureStartDate-month" -> mockFormValues.month2,
        "inputdate-ClosureStartDate-year" -> mockFormValues.year2,
        "inputnumeric-ClosurePeriod" -> mockFormValues.numericTextBoxValue,
        "inputmultiselect-Dropdown" -> mockFormValues.multiSelectField,
        "inputtextarea-TextArea" -> mockFormValues.textAreaValue,
        "inputradio-Radio" -> mockFormValues.radioValue,
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
