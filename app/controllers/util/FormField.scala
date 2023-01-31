package controllers.util

import controllers.util.FormField._
import org.apache.commons.lang3.NotImplementedException

import java.time.{LocalDateTime, Year}
import scala.util.control.Exception.allCatch

abstract class FormField {
  val fieldId: String
  val fieldName: String
  val fieldDescription: String
  val multiValue: Boolean
  val isRequired: Boolean
  val hideInputs: Boolean = false
  val fieldErrors: List[String]
  val dependencies: Map[String, List[FormField]] = Map.empty
  def selectedOptionNames(): List[String]
}

case class InputNameAndValue(name: String, value: String, placeHolder: String = "")

case class RadioButtonGroupField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
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
}

case class TextField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
    multiValue: Boolean,
    nameAndValue: InputNameAndValue,
    inputMode: String,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    addSuffixText: Boolean = true,
    inputType: String = "number",
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
}

case class TextAreaField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
    multiValue: Boolean,
    nameAndValue: InputNameAndValue,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    rows: String = "5",
    wrap: String = "hard",
    characterLimit: Int = 8000,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
}

case class DropdownField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
    multiValue: Boolean,
    options: Seq[InputNameAndValue],
    selectedOption: Option[InputNameAndValue],
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = selectedOption.map(_.name).toList
}

case class MultiSelectField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
    fieldGuidance: String,
    multiValue: Boolean = true,
    options: Seq[InputNameAndValue],
    selectedOption: Option[List[InputNameAndValue]],
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = selectedOption.getOrElse(Nil).map(_.name)
}

case class DateField(
    fieldId: String,
    fieldName: String,
    fieldDescription: String,
    multiValue: Boolean,
    day: InputNameAndValue,
    month: InputNameAndValue,
    year: InputNameAndValue,
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    isFutureDateAllowed: Boolean = true,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = Nil
}

object FormField {

  val dropdownOptionNotSelectedError = "There was no value selected for the %s."
  val invalidDropdownOptionSelectedError = "Option '%s' was not an option provided to the user."
  val emptyValueError = "There was no %s entered for the %s."
  val numberError = "%s entered must be a whole number."
  val negativeNumberError = "%s must not be a negative number."
  val invalidYearError = "The year should be 4 digits in length."
  val invalidDateError = "%s is an invalid %s number."
  val invalidDayError = "%s does not have %d days."
  val invalidDayForLeapYearError = "%s %d does not have %d days in it."
  val futureDateError = "%s date cannot be a future date."
  val radioOptionNotSelectedError = "There was no value selected for %s."
  val invalidRadioOptionSelectedError = "Option '%s' was not an option provided to the user."
  val tooLongInputError = "%s must be %s characters or less"

  def inputModeToFieldType(inputMode: String): String = {
    if (inputMode.equals("numeric")) "number" else "text"
  }
}

object RadioButtonGroupField {

  def validate(option: String, dependencies: Map[String, String], radioButtonGroupField: RadioButtonGroupField): List[String] = {
    val optionErrors = option match {
      case ""                                                               => Some(radioOptionNotSelectedError.format(radioButtonGroupField.fieldName))
      case value if !radioButtonGroupField.options.exists(_.value == value) => Some(invalidRadioOptionSelectedError.format(value))
      case _                                                                => None
    }
    def dependenciesError: Option[String] = radioButtonGroupField.dependencies
      .get(option)
      .flatMap(_.flatMap {
        case textField: TextField => TextField.validate(dependencies(textField.fieldId), textField)
        case formField: FormField => throw new NotImplementedException(s"Implement for ${formField.fieldId}")
      }.headOption)

    optionErrors.orElse(dependenciesError).map(List(_)).getOrElse(Nil)
  }

  def update(radioButtonGroupField: RadioButtonGroupField, value: Boolean): RadioButtonGroupField =
    radioButtonGroupField.copy(selectedOption = if (value) "yes" else "no")

  def update(radioButtonGroupField: RadioButtonGroupField, selectedOption: String, dependencies: Map[String, String]): RadioButtonGroupField = {

    if (dependencies.nonEmpty) {
      val updatedDependencies = radioButtonGroupField
        .dependencies(selectedOption)
        .map { case textField: TextField => TextField.update(textField, dependencies(textField.fieldId)) }
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

  def validate(selectedOption: Option[String], dropdownField: DropdownField): Option[String] =
    selectedOption match {
      case None                                                           => Some(dropdownOptionNotSelectedError.format(dropdownField.fieldName))
      case Some(value) if !dropdownField.options.exists(_.value == value) => Some(invalidDropdownOptionSelectedError.format(value))
      case _                                                              => None
    }

  def update(dropdownField: DropdownField, selectedValue: Option[String]): DropdownField = {
    val selectedOption: Option[InputNameAndValue] = dropdownField.options.find(v => selectedValue.contains(v.value))
    dropdownField.copy(selectedOption = selectedOption)
  }
}

object TextField {

  def validate(text: String, textField: TextField): Option[String] =
    if (text == "") {
      val fieldType: String = inputModeToFieldType(textField.inputMode)
      Some(emptyValueError.format(fieldType, textField.fieldName))
    } else if (textField.inputMode.equals("numeric")) {
      val inputName = textField.nameAndValue.name
      text match {
        case t if allCatch.opt(t.toInt).isEmpty => Some(numberError.format(inputName))
        case t if t.toInt < 0                   => Some(negativeNumberError.format(inputName))
        case _                                  => None
      }
    } else {
      None
    }

  def update(textField: TextField, value: String): TextField = textField.copy(nameAndValue = textField.nameAndValue.copy(value = value))
}

object TextAreaField {
  def update(textAreaField: TextAreaField, value: String): TextAreaField = textAreaField.copy(nameAndValue = textAreaField.nameAndValue.copy(value = value))

  def validate(text: String, textAreaField: TextAreaField): Option[String] = {

    text match {
      case t if t == "" && textAreaField.isRequired     => Some(emptyValueError.format("text", textAreaField.fieldName))
      case t if t.length > textAreaField.characterLimit => Some(tooLongInputError.format(textAreaField.fieldName, textAreaField.characterLimit))
      case _                                            => None
    }
  }
}

object DateField {

  val invalidDayValidation: Int => Boolean = (day: Int) => day < 1 || day > 31
  val invalidMonthValidation: Int => Boolean = (month: Int) => month < 1 || month > 12
  val invalidYearValidation: Int => Boolean = (year: Int) => year.toString.length != 4
  val isALeapYear: Int => Boolean = (year: Int) => Year.of(year).isLeap

  lazy val monthsWithLessThan31Days: Map[Int, String] = Map(
    2 -> "February",
    4 -> "April",
    6 -> "June",
    9 -> "September",
    11 -> "November"
  )

  def validate(day: String, month: String, year: String, dateField: DateField): Option[String] = {

    var error = validateValue(day, "Day", invalidDayValidation)
    error = if (error.isEmpty) validateValue(month, "Month", invalidMonthValidation) else error
    error = if (error.isEmpty) validateValue(year, "Year", invalidYearValidation) else error
    error = if (error.isEmpty) checkDayForTheMonthAndYear(day.toInt, month.toInt, year.toInt) else error
    if (error.isEmpty) checkIfFutureDateIsAllowed(day.toInt, month.toInt, year.toInt, dateField) else error
  }

  def update(dateField: DateField, localDateTime: LocalDateTime): DateField =
    dateField.copy(
      day = dateField.day.copy(value = localDateTime.getDayOfMonth.toString),
      month = dateField.month.copy(value = localDateTime.getMonthValue.toString),
      year = dateField.year.copy(value = localDateTime.getYear.toString)
    )

  def update(dateField: DateField, day: String, month: String, year: String): DateField =
    dateField.copy(day = dateField.day.copy(value = day), month = dateField.month.copy(value = month), year = dateField.year.copy(value = year))

  private def validateValue(day: String, unitType: String, isInvalidDate: Int => Boolean): Option[String] =
    day match {
      case ""                                 => Some(emptyValueError.format("number", unitType))
      case d if allCatch.opt(d.toInt).isEmpty => Some(numberError.format(unitType))
      case d if d.toInt < 0                   => Some(negativeNumberError.format(unitType))
      case d if isInvalidDate(d.toInt) =>
        if (unitType.equals("Year")) {
          Some(invalidYearError)
        } else {
          Some(invalidDateError.format(d, unitType))
        }
      case _ => None
    }

  private def checkDayForTheMonthAndYear(dayNumber: Int, monthNumber: Int, yearNumber: Int): Option[String] = {

    val monthHasLessThan31Days = monthsWithLessThan31Days.contains(monthNumber)

    if (dayNumber > 30 && monthHasLessThan31Days || dayNumber == 30 && monthNumber == 2) {
      Some(invalidDayError.format(monthsWithLessThan31Days(monthNumber), dayNumber))
    } else if (dayNumber == 29 && monthNumber == 2 && !isALeapYear(yearNumber)) {
      Some(invalidDayForLeapYearError.format(monthsWithLessThan31Days(monthNumber), yearNumber, dayNumber))
    } else {
      None
    }
  }

  private def checkIfFutureDateIsAllowed(day: Int, month: Int, year: Int, dateField: DateField): Option[String] =
    if (!dateField.isFutureDateAllowed && LocalDateTime.now().isBefore(LocalDateTime.of(year, month, day, 0, 0))) {
      Some(futureDateError.format(dateField.fieldName))
    } else {
      None
    }
}
