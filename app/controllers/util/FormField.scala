package controllers.util

import controllers.util.FormField.{invalidDateError, _}
import controllers.util.MetadataProperty.closurePeriod
import org.apache.commons.lang3.NotImplementedException
import services.Details
import uk.gov.nationalarchives.tdr.validation.ErrorCode._

import java.time.{LocalDateTime, Month, YearMonth}
import scala.util.control.Exception.allCatch

abstract class FormField {
  val fieldId: String
  val fieldName: String
  val fieldAlternativeName: String
  val fieldDescription: String
  val fieldInsetTexts: List[String]
  val multiValue: Boolean
  val isRequired: Boolean
  val hideInputs: Boolean = false
  val fieldErrors: List[String]
  val dependencies: Map[String, List[FormField]] = Map.empty
  def selectedOptionNames(): List[String]
  def selectedOptions(): String

  def getAlternativeName: String = {
    if (fieldAlternativeName.nonEmpty) fieldAlternativeName else fieldName
  }
}

case class InputNameAndValue(name: String, value: String, placeHolder: String = "")

case class RadioButtonGroupField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    additionalInfo: String,
    multiValue: Boolean,
    options: Seq[InputNameAndValue],
    selectedOption: String,
    isRequired: Boolean,
    override val hideInputs: Boolean = false,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] =
    List(selectedOption)

  override def selectedOptions(): String = selectedOption
}

case class TextField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean,
    nameAndValue: InputNameAndValue,
    inputMode: String,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    suffixText: Option[String] = None,
    inputType: String = "number",
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
  override def selectedOptions(): String = nameAndValue.value
}

case class TextAreaField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean,
    nameAndValue: InputNameAndValue,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    rows: String = "5",
    wrap: String = "soft",
    characterLimit: Int = maxCharacterLimit,
    details: Option[Details],
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
  override def selectedOptions(): String = nameAndValue.value
}

case class DropdownField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean,
    options: Seq[InputNameAndValue],
    selectedOption: Option[InputNameAndValue],
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = selectedOption.map(_.name).toList
  override def selectedOptions(): String = selectedOption.map(_.value).getOrElse("")
}

case class MultiSelectField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean = true,
    options: Seq[InputNameAndValue],
    selectedOption: Option[List[InputNameAndValue]],
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = selectedOption.getOrElse(Nil).map(_.name)
  override def selectedOptions(): String = selectedOption.map(_.map(_.value).mkString(",")).getOrElse("")
}

case class DateField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean,
    day: InputNameAndValue,
    month: InputNameAndValue,
    year: InputNameAndValue,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    isFutureDateAllowed: Boolean = false,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(fieldName)
  override def selectedOptions(): String = s"${year.value}:${month.value}:${day.value}T00:00:00"
}
object FormField {
  val dropdownOptionNotSelectedError = "Select at least one %s"
  val invalidDropdownOptionSelectedError = "Option '%s' was not an option provided to the user."
  val emptyValueError = "The %s must contain a %s"
  val wholeNumberError = "The %s must be a whole number, like %s"
  val wholeNumberError2 = "The %s of the %s must be a whole number, like %s"
  val negativeNumberError = "The %s cannot be a negative number"
  val zeroNumberError = "The %s cannot be 0"
  val invalidYearError = "The year of the %s must contain 4 digits"
  val futureDateError = "The date of the %s must be in the past"
  val invalidDateError = "The %s of the %s must be between 1 and %s"
  val invalidDayError = "%s does not have %d days in it. Enter the day for the %s between 1 and %d"
  val radioOptionNotSelectedError = "Select if the %s is sensitive to the public"
  val invalidRadioOptionSelectedError = "Option '%s' was not an option provided to the user."
  val tooLongInputError = "%s must be %s characters or less"
  val closurePeriodNotEnteredError = "Enter the number of years the record is closed from the closure start date"
  val dependencyNotEntered = "Add an %s for this record"
  val dateNotEnteredError = "Enter the %s for this record"
  val wholeNumberExample = "3, 15, 21"
  val maxCharacterLimit = 8000
}

object RadioButtonGroupField {

  def updateError(field: RadioButtonGroupField, errorCode: String): RadioButtonGroupField = {
    errorCode match {
      case NO_OPTION_SELECTED_ERROR => field.copy(fieldErrors = List(radioOptionNotSelectedError.format(field.fieldName)))
      case UNDEFINED_VALUE_ERROR    => field.copy(fieldErrors = List(invalidRadioOptionSelectedError.format(field.selectedOption)))
      case EMPTY_VALUE_ERROR        => field.copy(fieldErrors = List(dependencyNotEntered.format(field.dependencies(field.selectedOption).head.getAlternativeName.toLowerCase)))
      case MAX_CHARACTER_LIMIT_INPUT_ERROR =>
        field.copy(fieldErrors = List(tooLongInputError.format(field.dependencies(field.selectedOption).head.getAlternativeName, maxCharacterLimit)))
      case _ => field.copy(fieldErrors = List(errorCode))
    }
  }

  def validate(option: String, dependencies: Map[String, String], radioButtonGroupField: RadioButtonGroupField): List[String] = {
    val optionErrors = Option(option).filter(_.nonEmpty) match {
      case None if radioButtonGroupField.isRequired                               => Some(radioOptionNotSelectedError.format(radioButtonGroupField.fieldName))
      case Some(value) if !radioButtonGroupField.options.exists(_.value == value) => Some(invalidRadioOptionSelectedError.format(value))
      case _                                                                      => None
    }
    def dependenciesError: Option[String] = radioButtonGroupField.dependencies
      .get(option)
      .flatMap(_.flatMap {
        case textField: TextField         => TextField.validate(dependencies(textField.fieldId), textField.copy(isRequired = true))
        case textAreaField: TextAreaField => TextAreaField.validate(dependencies(textAreaField.fieldId), textAreaField.copy(isRequired = true))
        case formField: FormField         => throw new NotImplementedException(s"Implement for ${formField.fieldId}")
      }.headOption)

    optionErrors.orElse(dependenciesError).map(List(_)).getOrElse(Nil)
  }

  def update(radioButtonGroupField: RadioButtonGroupField, value: Boolean): RadioButtonGroupField =
    radioButtonGroupField.copy(selectedOption = if (value) "yes" else "no")

  def update(radioButtonGroupField: RadioButtonGroupField, selectedOption: String, dependencies: Map[String, String]): RadioButtonGroupField = {

    if (dependencies.nonEmpty) {
      val updatedDependencies = radioButtonGroupField
        .dependencies(selectedOption)
        .map {
          case textField: TextField         => TextField.update(textField, dependencies(textField.fieldId))
          case textAreaField: TextAreaField => TextAreaField.update(textAreaField, dependencies(textAreaField.fieldId))
        }
      radioButtonGroupField.copy(
        selectedOption = selectedOption,
        dependencies = radioButtonGroupField.dependencies + (selectedOption -> updatedDependencies)
      )
    } else {
      radioButtonGroupField.copy(selectedOption = selectedOption)
    }
  }
}

object MultiSelectField {
  def updateError(multiSelectField: MultiSelectField, errorCode: String): MultiSelectField = {
    errorCode match {
      case EMPTY_VALUE_ERROR     => multiSelectField.copy(fieldErrors = List(dropdownOptionNotSelectedError.format(multiSelectField.fieldName)))
      case UNDEFINED_VALUE_ERROR => multiSelectField.copy(fieldErrors = List(invalidDropdownOptionSelectedError.format(multiSelectField.selectedOptions())))
      case _                     => multiSelectField.copy(fieldErrors = List(errorCode))
    }
  }

  def validate(selectedOptions: Seq[String], multiSelectField: MultiSelectField): Option[String] =
    selectedOptions match {
      case Nil                                                                      => Some(dropdownOptionNotSelectedError.format(multiSelectField.fieldName))
      case values if !values.forall(multiSelectField.options.map(_.value).contains) => Some(invalidDropdownOptionSelectedError.format(values.mkString(", ")))
      case _                                                                        => None
    }

  def update(multiSelectField: MultiSelectField, selectedValues: Seq[String]): MultiSelectField = {
    val selectedOptions: List[InputNameAndValue] = multiSelectField.options.filter(v => selectedValues.contains(v.value)).toList
    multiSelectField.copy(selectedOption = Some(selectedOptions))
  }
}

object DropdownField {

  def updateError(dropdownField: DropdownField, errorCode: String): DropdownField = {
    errorCode match {
      case EMPTY_VALUE_ERROR     => dropdownField.copy(fieldErrors = List(dropdownOptionNotSelectedError.format(dropdownField.fieldName)))
      case UNDEFINED_VALUE_ERROR => dropdownField.copy(fieldErrors = List(invalidDropdownOptionSelectedError.format(dropdownField.selectedOptions())))
      case _                     => dropdownField.copy(fieldErrors = List(errorCode))
    }
  }

  def validate(selectedOption: Option[String], dropdownField: DropdownField): Option[String] = {
    selectedOption match {
      case None if dropdownField.isRequired                               => Some(dropdownOptionNotSelectedError.format(dropdownField.fieldName))
      case Some(value) if !dropdownField.options.exists(_.value == value) => Some(invalidDropdownOptionSelectedError.format(value))
      case _                                                              => None
    }
  }

  def update(dropdownField: DropdownField, selectedValue: Option[String]): DropdownField = {
    val selectedOption: Option[InputNameAndValue] = dropdownField.options.find(v => selectedValue.contains(v.value))
    dropdownField.copy(selectedOption = selectedOption)
  }
}

object TextField {

  def updateError(textField: TextField, errorCode: String): TextField = {
    errorCode match {
      case EMPTY_VALUE_ERROR =>
        val error = if (textField.fieldId == closurePeriod) closurePeriodNotEnteredError else dateNotEnteredError.format(textField.fieldName)
        textField.copy(fieldErrors = List(error))
      case NUMBER_ONLY_ERROR     => textField.copy(fieldErrors = List(wholeNumberError.format(textField.fieldName.toLowerCase, wholeNumberExample)))
      case NEGATIVE_NUMBER_ERROR => textField.copy(fieldErrors = List(negativeNumberError.format(textField.fieldName.toLowerCase)))
      case ZERO_NUMBER_ERROR     => textField.copy(fieldErrors = List(zeroNumberError.format(textField.fieldName.toLowerCase)))
      case _                     => textField.copy(fieldErrors = List(errorCode))
    }
  }

  def validate(text: String, textField: TextField): Option[String] = {

    if (text.isEmpty) {
      textField.isRequired match {
        case true if textField.fieldId == closurePeriod => Some(closurePeriodNotEnteredError)
        case true                                       => Some(dependencyNotEntered.format(textField.getAlternativeName.toLowerCase))
        case _                                          => None
      }
    } else if (textField.inputMode.equals("numeric")) {
      text match {
        case t if allCatch.opt(t.toInt).isEmpty => Some(wholeNumberError.format(textField.fieldName.toLowerCase, wholeNumberExample))
        case t if t.toInt < 0                   => Some(negativeNumberError.format(textField.fieldName.toLowerCase))
        case _                                  => None
      }
    } else {
      None
    }
  }

  def update(textField: TextField, value: String): TextField = textField.copy(nameAndValue = textField.nameAndValue.copy(value = value))
}

object TextAreaField {
  def updateError(textAreaField: TextAreaField, errorCode: String): TextAreaField = {
    errorCode match {
      case EMPTY_VALUE_ERROR               => textAreaField.copy(fieldErrors = List(dependencyNotEntered.format(textAreaField.getAlternativeName.toLowerCase)))
      case MAX_CHARACTER_LIMIT_INPUT_ERROR => textAreaField.copy(fieldErrors = List(tooLongInputError.format(textAreaField.fieldName, textAreaField.characterLimit)))
      case _                               => textAreaField.copy(fieldErrors = List(errorCode))
    }
  }

  def update(textAreaField: TextAreaField, value: String): TextAreaField = textAreaField.copy(nameAndValue = textAreaField.nameAndValue.copy(value = value))
  def validate(text: String, textAreaField: TextAreaField): Option[String] = {
    val fieldName = textAreaField.getAlternativeName
    text match {
      case t if t == "" && textAreaField.isRequired     => Some(dependencyNotEntered.format(fieldName.toLowerCase))
      case t if t.length > textAreaField.characterLimit => Some(tooLongInputError.format(textAreaField.fieldName, textAreaField.characterLimit))
      case _                                            => None
    }
  }
}

object DateField {

  def updateError(dateField: DateField, errorCode: String): DateField = {
    val fieldName = if (dateField.getAlternativeName == "Advisory Council Approval") dateField.fieldAlternativeName else dateField.getAlternativeName.toLowerCase

    errorCode match {
      case EMPTY_VALUE_ERROR               => dateField.copy(fieldErrors = List(dateNotEnteredError.format(fieldName)))
      case EMPTY_VALUE_ERROR_FOR_DAY       => dateField.copy(fieldErrors = List(emptyValueError.format(fieldName, "day")))
      case NUMBER_ERROR_FOR_DAY            => dateField.copy(fieldErrors = List(wholeNumberError2.format("day", fieldName, wholeNumberExample)))
      case NEGATIVE_NUMBER_ERROR_FOR_DAY   => dateField.copy(fieldErrors = List(negativeNumberError.format("day")))
      case INVALID_NUMBER_ERROR_FOR_DAY    => dateField.copy(fieldErrors = List(invalidDateError.format("day", fieldName, "31")))
      case EMPTY_VALUE_ERROR_FOR_MONTH     => dateField.copy(fieldErrors = List(emptyValueError.format(fieldName, "month")))
      case NUMBER_ERROR_FOR_MONTH          => dateField.copy(fieldErrors = List(wholeNumberError2.format("month", fieldName, "3, 9, 12")))
      case NEGATIVE_NUMBER_ERROR_FOR_MONTH => dateField.copy(fieldErrors = List(negativeNumberError.format("month")))
      case INVALID_NUMBER_ERROR_FOR_MONTH  => dateField.copy(fieldErrors = List(invalidDateError.format("month", fieldName, "12")))
      case EMPTY_VALUE_ERROR_FOR_YEAR      => dateField.copy(fieldErrors = List(emptyValueError.format(fieldName, "year")))
      case NUMBER_ERROR_FOR_YEAR           => dateField.copy(fieldErrors = List(wholeNumberError2.format("year", fieldName, "1994, 2000, 2023")))
      case NEGATIVE_NUMBER_ERROR_FOR_YEAR  => dateField.copy(fieldErrors = List(negativeNumberError.format("year")))
      case INVALID_NUMBER_ERROR_FOR_YEAR   => dateField.copy(fieldErrors = List(invalidYearError.format(fieldName)))
      case INVALID_DAY_FOR_MONTH_ERROR =>
        dateField.copy(
          fieldErrors = checkDayForTheMonthAndYear(
            dayNumber = dateField.day.value.toInt,
            monthNumber = dateField.month.value.toInt,
            yearNumber = dateField.year.value.toInt,
            fieldName = fieldName
          ).toList
        )
      case FUTURE_DATE_ERROR => dateField.copy(fieldErrors = List(futureDateError.format(fieldName)))
      case _                 => dateField.copy(fieldErrors = List(errorCode))
    }
  }

  val invalidMonthValidation: Int => Boolean = (month: Int) => month < 1 || month > 12
  val invalidYearValidation: Int => Boolean = (year: Int) => year.toString.length != 4

  def validate(day: String, month: String, year: String, dateField: DateField): Option[String] = {
    val fieldName = if (dateField.getAlternativeName == "Advisory Council Approval") dateField.fieldAlternativeName else dateField.getAlternativeName.toLowerCase
    val emptyDate: Boolean = day.isEmpty && month.isEmpty && year.isEmpty

    emptyDate match {
      case false                        => validateDateValues(day, month, year, fieldName, dateField)
      case true if dateField.isRequired => Some(dateNotEnteredError.format(fieldName))
      case _                            => None
    }
  }

  private def validateDateValues(day: String, month: String, year: String, fieldName: String, dateField: DateField): Option[String] = {
    val input = for {
      year <- validatePositiveInteger(year, fieldName, "Year", "1994, 2000, 2023")
      month <- validatePositiveInteger(month, fieldName, "Month", "3, 9, 12")
      day <- validatePositiveInteger(day, fieldName, "Day", wholeNumberExample)
    } yield (day, month, year)

    input match {
      case Right((day, month, year)) =>
        lazy val yearValidation = if (invalidYearValidation(year)) Some(invalidYearError.format(fieldName)) else None
        lazy val monthValidation = if (invalidMonthValidation(month)) Some(invalidDateError.format("month", fieldName, "12")) else None
        lazy val dayValidation = checkDayForTheMonthAndYear(dayNumber = day, monthNumber = month, yearNumber = year, fieldName = fieldName)
        lazy val futureValidation = checkIfFutureDateIsAllowed(day = day, month = month, year = year, dateField = dateField, fieldName = fieldName)
        yearValidation orElse monthValidation orElse dayValidation orElse futureValidation
      case Left(inputValidationError) => Some(inputValidationError)
    }
  }

  def validatePositiveInteger(value: String, fieldName: String, unitType: String, wholeNumberExample: String): Either[String, Int] = {
    val nonEmpty: String => Either[String, String] = value => if (value.isEmpty) Left(emptyValueError.format(fieldName, unitType.toLowerCase)) else Right(value)
    val isInt: String => Either[String, Int] = value => value.toIntOption.toRight(wholeNumberError2.format(unitType.toLowerCase, fieldName, wholeNumberExample))
    val nonNegative: Int => Either[String, Int] = value => if (value < 0) Left(negativeNumberError.format(unitType.toLowerCase)) else Right(value)
    for {
      nonEmptyValue <- nonEmpty(value)
      intValue <- isInt(nonEmptyValue)
      positiveInt <- nonNegative(intValue)
    } yield positiveInt
  }

  def update(dateField: DateField, localDateTime: LocalDateTime): DateField =
    dateField.copy(
      day = dateField.day.copy(value = localDateTime.getDayOfMonth.toString),
      month = dateField.month.copy(value = localDateTime.getMonthValue.toString),
      year = dateField.year.copy(value = localDateTime.getYear.toString)
    )

  def update(dateField: DateField, day: String, month: String, year: String): DateField =
    dateField.copy(day = dateField.day.copy(value = day), month = dateField.month.copy(value = month), year = dateField.year.copy(value = year))

  private def checkDayForTheMonthAndYear(dayNumber: Int, monthNumber: Int, yearNumber: Int, fieldName: String): Option[String] = {
    val daysInMonth = YearMonth.of(yearNumber, monthNumber).lengthOfMonth()
    if (dayNumber < 1) Some(invalidDateError.format("day", fieldName, daysInMonth))
    else if (dayNumber > daysInMonth) Some(invalidDayError.format(monthStringFromNumber(monthNumber), dayNumber, fieldName, daysInMonth))
    else None
  }

  def monthStringFromNumber(monthNumber: Int): String = Month.of(monthNumber).toString.toLowerCase.capitalize

  private def checkIfFutureDateIsAllowed(day: Int, month: Int, year: Int, dateField: DateField, fieldName: String): Option[String] =
    if (!dateField.isFutureDateAllowed && LocalDateTime.now().isBefore(LocalDateTime.of(year, month, day, 0, 0))) {
      Some(futureDateError.format(fieldName))
    } else {
      None
    }
}
