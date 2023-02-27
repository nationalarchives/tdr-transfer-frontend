package controllers.util

import controllers.util.FormField._
import org.apache.commons.lang3.NotImplementedException
import services.Details

import java.time.{LocalDateTime, Year}
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
    addSuffixText: Boolean = true,
    inputType: String = "number",
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
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
    characterLimit: Int = 8000,
    details: Option[Details],
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = List(nameAndValue.name)
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
}

case class MultiSelectField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
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
}

object FormField {
  val dropdownOptionNotSelectedError = "Search for and select at least one %s"
  val invalidDropdownOptionSelectedError = "Option '%s' was not an option provided to the user."
  val emptyValueError = "The %s must contain a %s"
  val wholeNumberError = "The %s must be a whole number, like %s"
  val wholeNumberError2 = "The %s of the %s must be a whole number, like %s"
  val negativeNumberError = "The %s cannot be a negative number"
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
        case textField: TextField         => TextField.validate(dependencies(textField.fieldId), textField)
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
      val fieldName = textField.getAlternativeName.toLowerCase
      if (textField.fieldId == "ClosurePeriod") Some(closurePeriodNotEnteredError.format(textField)) else Some(dependencyNotEntered.format(fieldName))
    } else if (textField.inputMode.equals("numeric")) {
      text match {
        case t if allCatch.opt(t.toInt).isEmpty => Some(wholeNumberError.format(textField.fieldName.toLowerCase, wholeNumberExample))
        case t if t.toInt < 0                   => Some(negativeNumberError.format(textField.fieldName.toLowerCase))
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
    val fieldName = textAreaField.getAlternativeName
    text match {
      case t if t == "" && textAreaField.isRequired     => Some(dependencyNotEntered.format(fieldName.toLowerCase))
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
    val fieldName = if (dateField.getAlternativeName == "Advisory Council Approval") dateField.fieldAlternativeName else dateField.getAlternativeName.toLowerCase
    if (day.isEmpty && month.isEmpty && year.isEmpty) {
      Some(dateNotEnteredError.format(fieldName))
    } else {
      val dayError = validateValue(day, "Day", invalidDayValidation, wholeNumberExample, "31", fieldName)
      val monthError = if (dayError.isEmpty) validateValue(month, "Month", invalidMonthValidation, "3, 9, 12", "12", fieldName) else dayError
      val yearError = if (monthError.isEmpty) validateValue(year, "Year", invalidYearValidation, "1994, 2000, 2023", "", fieldName) else monthError
      val dayForMonthAndYearError = if (yearError.isEmpty) checkDayForTheMonthAndYear(day.toInt, month.toInt, year.toInt, fieldName) else yearError
      if (dayForMonthAndYearError.isEmpty) checkIfFutureDateIsAllowed(day.toInt, month.toInt, year.toInt, dateField, fieldName) else dayForMonthAndYearError
    }
  }

  def update(dateField: DateField, localDateTime: LocalDateTime): DateField =
    dateField.copy(
      day = dateField.day.copy(value = localDateTime.getDayOfMonth.toString),
      month = dateField.month.copy(value = localDateTime.getMonthValue.toString),
      year = dateField.year.copy(value = localDateTime.getYear.toString)
    )

  def update(dateField: DateField, day: String, month: String, year: String): DateField =
    dateField.copy(day = dateField.day.copy(value = day), month = dateField.month.copy(value = month), year = dateField.year.copy(value = year))

  private def validateValue(
      day: String,
      unitType: String,
      isInvalidDate: Int => Boolean,
      wholeNumberError: String,
      incorrectDateError: String,
      fieldName: String
  ): Option[String] = {
    day match {
      case ""                                 => Some(emptyValueError.format(fieldName, unitType.toLowerCase))
      case d if allCatch.opt(d.toInt).isEmpty => Some(wholeNumberError2.format(unitType.toLowerCase, fieldName, wholeNumberError))
      case d if d.toInt < 0                   => Some(negativeNumberError.format(unitType.toLowerCase))
      case d if isInvalidDate(d.toInt) =>
        if (unitType.equals("Year")) {
          Some(invalidYearError.format(fieldName))
        } else {
          Some(invalidDateError.format(unitType.toLowerCase, fieldName, incorrectDateError))
        }
      case _ => None
    }
  }

  private def checkDayForTheMonthAndYear(dayNumber: Int, monthNumber: Int, yearNumber: Int, fieldName: String): Option[String] = {
    val monthHasLessThan31Days = monthsWithLessThan31Days.contains(monthNumber)

    if (dayNumber > 30 && monthHasLessThan31Days || dayNumber == 30 && monthNumber == 2) {
      Some(invalidDayError.format(monthsWithLessThan31Days(monthNumber), dayNumber, fieldName, 30))
    } else if (dayNumber == 29 && monthNumber == 2 && !isALeapYear(yearNumber)) {
      Some(invalidDayError.format(monthsWithLessThan31Days(monthNumber), dayNumber, fieldName, 28))
    } else {
      None
    }
  }

  private def checkIfFutureDateIsAllowed(day: Int, month: Int, year: Int, dateField: DateField, fieldName: String): Option[String] =
    if (!dateField.isFutureDateAllowed && LocalDateTime.now().isBefore(LocalDateTime.of(year, month, day, 0, 0))) {
      Some(futureDateError.format(fieldName))
    } else {
      None
    }
}
