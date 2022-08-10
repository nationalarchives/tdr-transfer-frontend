package controllers.util

import controllers.util.CustomMetadataUtils.FieldValues
import graphql.codegen.GetCustomMetadata
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.POST

import scala.collection.immutable.{ListMap, ListSet}

class DynamicFormUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  "formAnswersWithValidInputNames" should "returns all values passed into the request except the CSRF token" in {
    val rawFormWithoutCsrfToken = ListMap(
      "inputdate-testproperty3-day" -> List("3"),
      "inputdate-testproperty3-month" -> List("4"),
      "inputdate-testproperty3-year" -> List("2020"),
      "inputnumeric-testproperty6-years" -> List("4"),
      "inputdropdown-testproperty8" -> List("TestValue 3"),
      "inputradio-testproperty7" -> List("Yes"),
      "csrfToken" -> List("12345")
    )
    val dynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
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

    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
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

  "validateFormAnswers" should "throw an exception if the metadata name in the input name is not valid" in {
    val unsupportedMetadataName = "wrongmetadataname"

    val rawFormWithoutCsrfToken =
      ListMap(s"inputdate-$unsupportedMetadataName-day" -> List("3"))

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

    val thrownException: IllegalArgumentException =
      the[IllegalArgumentException] thrownBy dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)

    thrownException.getMessage should equal(s"Metadata name $unsupportedMetadataName, extracted from field input name, does not exist.")
  }

  "validateFormAnswers" should "throw an exception if an unsupported fieldType (with a prefix of 'input') has been passed in" in {
    val unsupportedFieldType = "inputwrong"

    val rawFormWithoutCsrfToken =
      ListMap(s"$unsupportedFieldType-testproperty3-day" -> List("3"), "csrfToken" -> List("12345"))

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

    val thrownException: IllegalArgumentException =
      the[IllegalArgumentException] thrownBy dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)

    thrownException.getMessage should equal(s"$unsupportedFieldType is not a supported type.")
  }

  "validateFormAnswers" should "return a 'no-value selected'-selection-related error for selection fields if they are missing" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(dropdownValue = List(""), radioValue = List(""))
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        if (value == List("")) {
          val metadataName = inputName.split("-")(1)
          validatedForm(inputName)._1 should equal(None)
          validatedForm(inputName)._2 should equal(List(s"There was no value selected for the $metadataName."))
        } else {
          validatedForm(inputName)._2 should equal(List())
        }
    }
  }

  "validateFormAnswers" should "throws an exception if value selected is not one of the official options" in {
    // This test is to try and catch bad actors
    val invalidSelection = "incorrectValueSelection"
    List(s"inputdropdown-testproperty8" -> List(invalidSelection), s"inputradio-testproperty2" -> List(invalidSelection)).foreach {
      inputNameAndValue =>
        val rawFormWithoutCsrfToken =
          ListMap(inputNameAndValue)

        val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormWithoutCsrfToken)

        val thrownException: IllegalArgumentException =
          the[IllegalArgumentException] thrownBy dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)

        thrownException.getMessage should equal(s"Option '$invalidSelection' was not an option provided to the user")
    }
  }

  "validateFormAnswers" should "return the selection-related values and no errors for selection fields if they are present" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues()
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
  }

  "validateFormAnswers" should "return a 'no-number'-related error for the numeric fields that are missing values" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(day = List(""), numericTextBoxValue = List(""))
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        if (value == List("")) {
          val unitType = inputName.split("-")(2)
          validatedForm(inputName)._1 should equal(None)
          validatedForm(inputName)._2 should equal(List(s"There was no number entered for the ${unitType.capitalize}."))
        } else {
          validatedForm(inputName)._2 should equal(List())
        }
    }
  }

  "validateFormAnswers" should "return a 'whole number'-related error for the numeric fields that are missing values" in {
    //Shouldn't be possible on client-side due to "type:numeric" attribute added to HTML, but in case that is removed
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(month = List("b4"),
        year = List("2r"),
        numericTextBoxValue = List(" 40"),
        day2 = List("000"),
        month2 = List("e"),
        year2 = List("-")
      )
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        val inputInfo = inputName.split("-")
        if (inputInfo.length == 3 && !inputName.endsWith("-day")) {
          val unitType = inputInfo(2)
          validatedForm(inputName)._1 should equal(Some(value.head))
          validatedForm(inputName)._2 should equal(List(s"${unitType.capitalize} entered must be a whole number."))
        } else {
          validatedForm(inputName)._2 should equal(List())
        }
    }
  }

  "validateFormAnswers" should "return a 'negative number'-related error for the numeric fields that have negative numbers" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(day = List("-20"), numericTextBoxValue = List("-01"))
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        if (value.head.startsWith("-")) {
          val unitType = inputName.split("-")(2)
          validatedForm(inputName)._1 should equal(Some(value.head.toInt))
          validatedForm(inputName)._2 should equal(List(s"${unitType.capitalize} must not be a negative number."))
        } else {
          validatedForm(inputName)._2 should equal(List())
        }
    }
  }

  "validateFormAnswers" should "return the 'year' value and no errors for the year field if it is 4 digits long" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues()
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
  }

  "validateFormAnswers" should "return a '4 digits'-related error for the numeric 'year' field if the year is more or less than 4 digits" in {
    List(("1", "19"), ("200", "20050")).foreach {
      case (year1, year2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(year = List(year1), year2 = List(year2))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        rawFormWithoutCsrfToken.foreach {
          case (inputName, value) =>
            val inputInfo = inputName.split("-")
            if (inputInfo.last == "year") {
              validatedForm(inputName)._1 should equal(Some(value.head.toInt))
              validatedForm(inputName)._2 should equal(List(s"The year should be 4 digits in length."))
            }
            else {
              validatedForm(inputName)._2 should equal(List())
            }
        }
    }
  }

  "validateFormAnswers" should "return the day/month values and no errors if they are within the defined range" in {
    List(("12", "6", "31", "12"), ("6", "4", "18", "8")).foreach {
      case (day1, month1, day2, month2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List(day1), month = List(month1), day2 = List(day2), month2 = List(month2))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
    }
  }

  "validateFormAnswers" should "return an 'invalid'-number error for the day/month if the day number more than 32, " +
    "month is more than 12 or they are both less than 1" in {
    List(("0", "0", "32", "13"), ("50", "18", "1000", "72")).foreach {
      case (day1, month1, day2, month2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List(day1), month = List(month1), day2 = List(day2), month2 = List(month2))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        rawFormWithoutCsrfToken.foreach {
          case (inputName, value) =>
            val inputInfo = inputName.split("-")
            if (Set("day", "month").contains(inputInfo.last)) {
              validatedForm(inputName)._1 should equal(Some(value.head.toInt))
              validatedForm(inputName)._2 should equal(List(s"${value.head} is an invalid ${inputInfo.last} number"))
            }
            else {
              validatedForm(inputName)._2 should equal(List())
            }
        }
    }
  }

  "validateFormAnswers" should "return the day value and no errors if the month truly has 31 days" in {
    List(("01", "03"), ("05", "07"), ("08", "10"), ("12", "1")).foreach {
      case (month1, month2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List("31"), month = List(month1), year = List("2011"), day2 = List("31"), month2 = List(month2), year2 = List("2007"))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
    }
  }

  "validateFormAnswers" should "return a 'month does not have 31 days' error if the month does not have 31 days" in {
    List(("04", "06"), ("09", "11"), ("02", "2")).foreach {
      case (month1, month2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List("31"), month = List(month1), year = List("2011"), day2 = List("31"), month2 = List(month2), year2 = List("2007"))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        rawFormWithoutCsrfToken.foreach {
          case (inputName, value) =>
            val inputInfo = inputName.split("-")
            if (inputInfo.last == "day") {
              val month: Option[Any] = validatedForm(inputName.replace("-day", "-month"))._1
              validatedForm(inputName)._1 should equal(Some(value.head.toInt))
              validatedForm(inputName)._2 should equal(
                List(s"${dynamicFormUtils.monthsWithLessThan31Days(month.get.asInstanceOf[Int])} does not have 31 days.")
              )
            }
            else {
              validatedForm(inputName)._2 should equal(List())
            }
        }
    }
  }

  "validateFormAnswers" should "return the day value and no errors if the month has 30 days" in {
    List(("04", "06"), ("09", "11"), ("01", "03"), ("05", "07"), ("08", "10"), ("12", "1")).foreach {
      case (month1, month2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List("30"), month = List(month1), year = List("2011"), day2 = List("30"), month2 = List(month2), year2 = List("2007"))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
    }
  }

  "validateFormAnswers" should "return a 'month does not have 30 days' error if the month does not have 30 days" in {
    val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(day = List("30"), month = List("02"), year = List("2011"), day2 = List("30"), month2 = List("02"), year2 = List("2020"))
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        val inputInfo = inputName.split("-")
        if (inputInfo.last == "day") {
          val month: Option[Any] = validatedForm(inputName.replace("-day", "-month"))._1
          validatedForm(inputName)._1 should equal(Some(value.head.toInt))
          validatedForm(inputName)._2 should equal(
            List(s"${dynamicFormUtils.monthsWithLessThan31Days(month.get.asInstanceOf[Int])} does not have 30 days.")
          )
        }
        else {
          validatedForm(inputName)._2 should equal(List())
        }
    }
  }

  "validateFormAnswers" should "return the day value and no errors if February does have 29 days that year (leap year)" in {
    List(("2024", "2028"), ("2032", "2036"), ("2040", "2044"), ("2048", "2052")).foreach {
      case (year1, year2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List("29"), month = List("02"), year = List(year1), day2 = List("29"), month2 = List("02"), year2 = List(year2))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken, validatedForm)
    }
  }

  "validateFormAnswers" should "return a 'month does not have 29 days' error if February does not have 29 days that year" in {
    List(("1700", "1800"), ("1900", "2100"), ("2023", "2027"), ("2031", "2035"), ("2039", "2043"), ("2047", "2051")).foreach {
      case (year1, year2) =>
        val (rawFormWithoutCsrfToken, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
          MockFormValues(day = List("29"), month = List("02"), year = List(year1), day2 = List("29"), month2 = List("02"), year2 = List(year2))
        )

        val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)
        rawFormWithoutCsrfToken.foreach {
          case (inputName, value) =>
            val inputInfo = inputName.split("-")

            if (inputInfo.last == "day") {
              val year: Option[Any] = validatedForm(inputName.replace("-day", "-year"))._1
              validatedForm(inputName)._1 should equal(Some(value.head.toInt))
              validatedForm(inputName)._2 should equal(
                List(s"February ${year.get} does not have 29 days in it.")
              )
            }
            else {
              validatedForm(inputName)._2 should equal(List())
            }
        }
    }
  }

  "convertSubmittedValuesToDefaultFieldValues" should "convert form answers and their errors to FieldValues" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(month = List("b4"),
        year = List("2r"),
        numericTextBoxValue = List(" 40"),
        day2 = List("000"),
        month2 = List("02"),
        year2 = List("2000")
      )
    )

    val validatedForm = dynamicFormUtils.validateFormAnswers(dynamicFormUtils.formAnswersWithValidInputNames)

    val formAnswersConvertedToFieldValues: Set[(FieldValues, String)] =
      dynamicFormUtils.convertSubmittedValuesToDefaultFieldValues(validatedForm)

    formAnswersConvertedToFieldValues.foreach {
      case (fieldValues, dataType) =>
        val fieldsThatHaveFieldIdInInputName: Map[String, (Option[Any], List[String])] = validatedForm.filter {
          case (inputName, (_, _)) => inputName.contains(fieldValues.fieldId)
        }
        val inputTypeBelongingToDataType: List[String] = dataTypeToInputType(dataType)
        val (enteredValues, inputErrors) = fieldsThatHaveFieldIdInInputName.toList.map {
          case (_, (value, errors)) => (value.getOrElse("").toString, errors)
        }.unzip

        // Go through fields/sub-fields, find the one with the error in it
        val errorThatSubmissionGenerated: List[String] = inputErrors.find { inputError => inputError.nonEmpty } match {
          case Some(error) => error
          case None => Nil
        }

        fieldsThatHaveFieldIdInInputName should not be empty
        fieldValues.selectedFieldOption.getOrElse(Seq()).foreach {
          case (_, valueThatUserSubmitted) => enteredValues.contains(valueThatUserSubmitted) should be(true)
        }
        fieldsThatHaveFieldIdInInputName.keys.foreach {
          inputName =>
            val dataTypeMatchesInputType = inputTypeBelongingToDataType.find(inputName.startsWith) match {
              case Some(_) => true
              case None => false
            }
            dataTypeMatchesInputType should be(true)
        }
        fieldValues.fieldError should equal(errorThatSubmissionGenerated)
    }
  }

  "monthsWithLessThan31Days" should "only contain months with 31 days" in {
    val (_, dynamicFormUtils): (ListMap[String, List[String]], DynamicFormUtils) = generateFormAndSendRequest(
      MockFormValues(dropdownValue = List(""), radioValue = List(""))
    )

    val monthsWithLessThan31Days = Map(
      2 -> "February",
      4 -> "April",
      6 -> "June",
      9 -> "September",
      11 -> "November"
    )

    dynamicFormUtils.monthsWithLessThan31Days should equal(monthsWithLessThan31Days)

  }

  private val dataTypeToInputType = Map(
    "Boolean" -> List("inputradio"),
    "DateTime" -> List("inputdate"),
    "Integer" -> List("inputnumeric"),
    "Text" -> List("inputdropdown")
  )

  private def verifyThatNoFieldsHaveErrors(rawFormWithoutCsrfToken: ListMap[String, List[String]],
                                           validatedForm: Map[String, (Option[Any], List[String])]): Unit = {
    rawFormWithoutCsrfToken.foreach {
      case (inputName, value) =>
        try {
          validatedForm(inputName)._1 should equal(Some(value.head.toInt))
        } catch {
          case _: Exception => validatedForm(inputName)._1 should equal(Some(value.head))
        }

        validatedForm(inputName)._2 should equal(List())
    }
  }

  private def generateFormAndSendRequest(mockFormValues: MockFormValues): (ListMap[String, List[String]], DynamicFormUtils) = {
    val rawFormToMakeRequestWith =
      ListMap(
        "inputdate-testproperty3-day" -> mockFormValues.day,
        "inputdate-testproperty3-month" -> mockFormValues.month,
        "inputdate-testproperty3-year" -> mockFormValues.year,
        "inputdate-testproperty9-day" -> mockFormValues.day,
        "inputdate-testproperty9-month" -> mockFormValues.month,
        "inputdate-testproperty9-year" -> mockFormValues.year,
        "inputnumeric-testproperty6-years" -> mockFormValues.numericTextBoxValue,
        "inputdropdown-testproperty8" -> mockFormValues.dropdownValue,
        "inputradio-testproperty7" -> mockFormValues.radioValue,
        "csrfToken" -> mockFormValues.csrfToken
      )

    val dynamicFormUtils: DynamicFormUtils = instantiateDynamicFormsUtils(rawFormToMakeRequestWith, passInFieldsForAllMetadata = false)

    (rawFormToMakeRequestWith.-("csrfToken"), dynamicFormUtils)
  }

  private def instantiateDynamicFormsUtils(rawFormToMakeRequestWith: Map[String, Seq[String]], passInFieldsForAllMetadata: Boolean = true): DynamicFormUtils = {
    val mockRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
      FakeRequest.apply(POST, s"/consignment/12345/add-closure-metadata")
        .withBody(AnyContentAsFormUrlEncoded(rawFormToMakeRequestWith))

    val mockProperties: List[GetCustomMetadata.customMetadata.CustomMetadata] = new CustomMetadataUtilsSpec().allProperties
    val customMetadataUtils: CustomMetadataUtils = new CustomMetadataUtils(mockProperties)

    val allMetadataAsFields: ListSet[(FieldValues, String)] = customMetadataUtils.convertPropertiesToFields(mockProperties.to(ListSet))

    if (passInFieldsForAllMetadata) {
      new DynamicFormUtils(mockRequest, allMetadataAsFields)
    } else {
      val metadataUsedForFormAsFields: ListSet[(FieldValues, String)] = allMetadataAsFields.filter {
        case (inputName, _) =>
          val rawFormWithoutCsrfToken: Map[String, Seq[String]] = rawFormToMakeRequestWith - "csrfToken"
          val metadataNames: List[String] = rawFormWithoutCsrfToken.keys.map { inputName => inputName.split("-")(1) }.toList
          metadataNames.contains(inputName.fieldId)
      }
      new DynamicFormUtils(mockRequest, metadataUsedForFormAsFields)
    }
  }
}

case class MockFormValues(day: List[String] = List("3"),
                          month: List[String] = List("4"),
                          year: List[String] = List("2021"),
                          numericTextBoxValue: List[String] = List("5"),
                          dropdownValue: List[String] = List("TestValue 3"),
                          radioValue: List[String] = List("Yes"),
                          day2: List[String] = List("7"),
                          month2: List[String] = List("9"),
                          year2: List[String] = List("2022"),
                          csrfToken: List[String] = List("12345"))
