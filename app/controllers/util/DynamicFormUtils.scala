package controllers.util

import controllers.util.CustomMetadataUtils.FieldValues
import play.api.mvc.{AnyContent, Request}

class DynamicFormUtils(request: Request[AnyContent], defaultFieldValues: Set[(FieldValues, String)]) {
  private val formAnswers: Map[String, Seq[String]] = request.body.asFormUrlEncoded match {
    case Some(answers: Map[String, Seq[String]]) => answers
    case _ => throw new Exception("Error: There were no values submitted.") // This should never happen
  }

  lazy val formAnswersWithValidInputNames: Map[String, Seq[String]] = formAnswers.filter {
    case (inputName, _) if inputName.startsWith("input") => true
    case (inputName, _) if inputName == "csrfToken" => false
    case (inputName, _) => throw new IllegalArgumentException(s"${inputName.split("-").head} is not a supported field type.")
  }

  lazy val monthsWithLessThan31Days = Map(
    2 -> "February",
    4 -> "April",
    6 -> "June",
    9 -> "September",
    11 -> "November"
  )

  def formAnswersContainAnError(validatedFormAnswers: Map[String, (Option[Any], List[String])]): Boolean =
    validatedFormAnswers.exists { case (_, (_, errors)) => errors.nonEmpty }

  def convertSubmittedValuesToDefaultFieldValues(validatedSubmittedValues: Map[String, (Option[Any], List[String])]): Set[(FieldValues, String)] = {
    val fullyValidatedFieldsGroupedByMetadataName: Map[String, Map[String, (Option[Any], List[String])]] =
      validatedSubmittedValues.groupBy {
        case (inputName, (_, _)) =>
          val inputNameSplitUp: Array[String] = inputName.split("-")
          val metadataName: String = inputNameSplitUp(1)
          metadataName
      }

    defaultFieldValues.map {
      case (defaultFieldValue, dataType) =>
        val metadataName = defaultFieldValue.fieldId
        val valuesBelongingToMetadataName = fullyValidatedFieldsGroupedByMetadataName(metadataName)
        val labelAndEnteredValue = valuesBelongingToMetadataName.map {
          case (inputName, (enteredValue, _)) =>
            val inputInfo: Array[String] = inputName.split("-")
            val enteredValueAsString: String = enteredValue.getOrElse("").toString
            val label: String = if (inputInfo.length == 3) inputInfo(2) else enteredValueAsString.capitalize
            (label, enteredValueAsString)
        }.toSeq

        FieldValues(
          fieldId = metadataName,
          defaultFieldValue.fieldOptions,
          selectedFieldOption = Some(labelAndEnteredValue),
          defaultFieldValue.multiValueSelect,
          defaultFieldValue.fieldLabel,
          defaultFieldValue.fieldHint,
          defaultFieldValue.fieldRequired,
          fieldError = valuesBelongingToMetadataName.find { case (_, (_, errors)) => errors.nonEmpty } match {
            case Some((_, (_, errors))) => errors
            case None => defaultFieldValue.fieldError // the default in an empty list
          }
        ) ->
          dataType
    }
  }

  def validateFormAnswers(formAnswers: Map[String, Seq[String]]): Map[String, (Option[Any], List[String])] = {
    val validatedFields: Map[String, (Option[Any], List[String])] = formAnswers.map {
      case (inputName, valueSelected) => validateFields(inputName, valueSelected.head)
    }
    val dateFieldsWhereAllUnitsOfTimeAreCorrect: Map[String, Map[String, (Option[Any], List[String])]] = getFieldsWhereDMYAreAllCorrect(validatedFields)

    val numberOfDaysInMonthChecks: Map[String, (Option[Any], List[String])] =
      dateFieldsWhereAllUnitsOfTimeAreCorrect.map {
        case (_, dateFieldWhereAllUnitsOfTimeAreCorrect) =>
          val fieldsThatHaveHadDaysValidated: (String, (Option[Any], List[String])) = validateDaysInMonth(dateFieldWhereAllUnitsOfTimeAreCorrect)
          fieldsThatHaveHadDaysValidated
      }
    val fullyValidatedFields: Map[String, (Option[Any], List[String])] = validatedFields ++ numberOfDaysInMonthChecks
    fullyValidatedFields
  }

  private def validateFields(inputName: String, valueSelected: String): (String, (Option[Any], List[String])) = {
    val inputInfo = inputName.split("-")
    val fieldType = inputInfo.head
    val metadataName = inputInfo(1)
    checkThatMetadataNameIsExists(inputName, fieldType, metadataName, inputInfo, valueSelected)
  }

  private def checkThatMetadataNameIsExists(inputName: String, fieldType: String, metadataName: String,
                                            inputInfo: Array[String], valueSelected: String): (String, (Option[Any], List[String])) = {
    val fieldValueBelongingToMetadataName = defaultFieldValues.find {
      case (fieldValues, _) => fieldValues.fieldId == metadataName
    }

    fieldValueBelongingToMetadataName match {
      case Some((fieldValue, _)) =>
        validateFieldType(inputName, fieldType, metadataName, inputInfo, valueSelected, fieldValue)
      case None => throw new IllegalArgumentException(s"Metadata name $metadataName, extracted from field input name, does not exist.")
    }
  }

  private def validateFieldType(inputName: String, fieldType: String, metadataName: String,
                                inputInfo: Array[String], valueSelected: String, fieldValue: FieldValues): (String, (Option[Any], List[String])) = {
    fieldType match {
      case "inputdropdown" | "inputradio" => validateSingleInputSelection(inputName, metadataName, valueSelected, fieldValue)
      case "inputdate" | "inputnumeric" =>
        val unitType = inputInfo(2)
        validateNumericInputEntered(valueSelected, inputName, unitType)
      case unsupportedValue => throw new IllegalArgumentException(s"$unsupportedValue is not a supported type.")
    }
  }

  private def validateSingleInputSelection(inputName: String, metadataName: String,
                                           valueSelected: String, fieldValue: FieldValues): (String, (Option[String], List[String])) = {
    if (valueSelected.nonEmpty) {
      checkThatSelectionIsAmongOptions(inputName, valueSelected, fieldValue)
    } else {
      inputName -> (None, List(s"There was no value selected for the $metadataName."))
    }
  }

  private def checkThatSelectionIsAmongOptions(inputName: String, valueSelected: String, fieldValue: FieldValues): (String, (Option[String], List[String])) = {
    // Check that the answer the user gives is among the options that they were presented with

    val optionSelectedIsValid: Boolean = fieldValue.fieldOptions.exists {
      case (_, option) => option.toLowerCase == valueSelected.toLowerCase
    }

    if (optionSelectedIsValid) {
      inputName -> (Some(valueSelected), Nil)
    } else {
      throw new IllegalArgumentException(s"Option '$valueSelected' was not an option provided to the user")
    }
  }

  private def validateNumericInputEntered(valueEntered: String, inputName: String, unitType: String): (String, (Option[Any], List[String])) = {
    if (valueEntered.nonEmpty) {
      checkValueIsAnInt(valueEntered, inputName, unitType)
    } else {
      inputName -> (None, List(s"There was no number entered for the ${unitType.capitalize}."))
    }
  }

  private def checkValueIsAnInt(valueEntered: String, inputName: String, unitType: String): (String, (Option[Any], List[String])) =
    try {
      checkIntIsPositive(valueEntered.toInt, inputName, unitType)
    } catch {
      case _: Exception => inputName -> (
        Some(valueEntered),
        List(s"${unitType.capitalize} entered must be a whole number.")
      )
    }

  private def checkIntIsPositive(valueEntered: Int, inputName: String, unitType: String): (String, (Option[Int], List[String])) =
    if (valueEntered >= 0) {
      checkForCorrectAmountOfDigits(valueEntered, inputName, unitType)
    } else {
      inputName -> (Some(valueEntered), List(s"${unitType.capitalize} must not be a negative number."))
    }

  private def checkForCorrectAmountOfDigits(valueEntered: Int, inputName: String, unitType: String): (String, (Option[Int], List[String])) =
    unitType match {
      case "day" | "month" =>
        checkThatTimeUnitIsWithinRange(valueEntered, inputName, unitType)
      case "year" =>
        if (valueEntered.toString.length == 4) {
          inputName -> (Some(valueEntered), Nil)
        } else {
          inputName -> (Some(valueEntered), List(s"The year should be 4 digits in length."))
        }
      case _ => inputName -> (Some(valueEntered), Nil)
    }

  private def checkThatTimeUnitIsWithinRange(valueEntered: Int, inputName: String, unitType: String): (String, (Option[Int], List[String])) =
    if (
      valueEntered > 0 && (
        unitType == "day" && valueEntered < 32 ||
          unitType == "month" && valueEntered < 13
        )
    ) {
      inputName -> (Some(valueEntered), Nil)
    } else {
      inputName -> (Some(valueEntered), List(s"$valueEntered is an invalid $unitType number"))
    }

  private def getFieldsWhereDMYAreAllCorrect(validatedFields: Map[String, (Option[Any], List[String])]
                                            ): Map[String, Map[String, (Option[Any], List[String])]] = {
    val correctlyFormattedUnitsOfTime: Map[String, (Option[Any], List[String])] = validatedFields.filter {
      case (inputName, (_, errors)) => inputName.contains("inputdate") && errors.isEmpty
    }

    val correctlyFormattedUnitsOfTimeGroupedByField: Map[String, Map[String, (Option[Any], List[String])]] =
    // days, months and years that are grouped based on their inputName
      correctlyFormattedUnitsOfTime.groupBy {
        case (inputName, (_, _)) =>
          val inputNameSplitUp: Array[String] = inputName.split("-")
          val fieldTypeAndMetadataName: Array[String] = inputNameSplitUp.take(2)
          fieldTypeAndMetadataName.mkString("-")
      }

    val dateFieldsWhereAllUnitsOfTimeAreCorrect: Map[String, Map[String, (Option[Any], List[String])]] =
      correctlyFormattedUnitsOfTimeGroupedByField.filter {
        // If any units of time are missing (i.e. < 3) exclude them so that user can fix them first, else continue
        case (_, correctlyFormattedUnitsOfTime) => correctlyFormattedUnitsOfTime.size == 3
      }

    dateFieldsWhereAllUnitsOfTimeAreCorrect
  }

  private def validateDaysInMonth(dayMonthYearValues: Map[String, (Option[Any], List[String])]): (String, (Option[Any], List[String])) = {
    val dayMonthYearValuesGroupedByUnit: Map[String, Date] = dayMonthYearValues.map {
      case (inputName, (value, errors)) =>
        val unitOfTime: String = inputName.split("-")(2)
        (
          unitOfTime,
          Date(
            value.get.asInstanceOf[Int],
            inputName,
            (inputName, (value, errors))
          )
        )
    }

    val dayInformation: Date = dayMonthYearValuesGroupedByUnit("day")
    val dayNumber = dayInformation.number
    val monthNumber = dayMonthYearValuesGroupedByUnit("month").number
    val yearNumber = dayMonthYearValuesGroupedByUnit("year").number

    val monthHasLessThan31Days = monthsWithLessThan31Days.contains(monthNumber)

    if (dayNumber > 30 && monthHasLessThan31Days || dayNumber == 30 && monthNumber == 2) {
      dayInformation.inputName -> (
        Some(dayNumber),
        List(s"${monthsWithLessThan31Days(monthNumber)} does not have $dayNumber days.")
      )
    } else if (dayNumber == 29 && monthNumber == 2) {
      checkThatYearIsALeapYear(dayNumber, dayInformation, monthNumber, monthsWithLessThan31Days, yearNumber)
    } else {
      dayInformation.originalInfo
    }
  }
  private def checkThatYearIsALeapYear(dayNumber: Int, dayInformation: Date, monthNumber: Int, monthsWithLessThan31Days: Map[Int, String],  yearNumber: Int) = {
    val isALeapYear = (yearNumber % 4 == 0 && yearNumber % 100 != 0) || (yearNumber % 100 + yearNumber % 400) == 0
    if (isALeapYear) {
      dayInformation.originalInfo
    }
    else {
      dayInformation.inputName -> (
        Some(dayNumber),
        List(s"${monthsWithLessThan31Days(monthNumber)} $yearNumber does not have $dayNumber days in it.")
      )
    }
  }
}

case class Date(number: Int,
                inputName: String,
                originalInfo: (String, (Option[Any], List[String])))
