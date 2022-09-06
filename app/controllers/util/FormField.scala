package controllers.util

import controllers.util.FormField._

import java.time.{LocalDateTime, Year}
import scala.util.control.Exception.allCatch


abstract class FormField {
  val fieldId: String
  val fieldName: String
  val fieldDescription: String
  val isRequired: Boolean
  val fieldErrors: List[String]
}

case class InputNameAndValue(name: String, value: String, placeHolder: String = "")

case class RadioButtonGroupField(fieldId: String, fieldName: String, fieldDescription: String, options: Seq[InputNameAndValue],
                                 selectedOption: String, isRequired: Boolean, fieldErrors: List[String] = Nil) extends FormField

case class TextField(fieldId: String, fieldName: String, fieldDescription: String, nameAndValue: InputNameAndValue, inputMode: String,
                     isRequired: Boolean, fieldErrors: List[String] = Nil) extends FormField

case class DropdownField(fieldId: String, fieldName: String, fieldDescription: String, options: Seq[InputNameAndValue],
                         selectedOption: Option[InputNameAndValue], isRequired: Boolean, fieldErrors: List[String] = Nil) extends FormField

case class DateField(fieldId: String, fieldName: String, fieldDescription: String, day: InputNameAndValue, month: InputNameAndValue, year: InputNameAndValue,
                     isRequired: Boolean, fieldErrors: List[String] = Nil) extends FormField

object FormField {

  val dropdownOptionNotSelectedError = "There was no value selected for the %s."
  val invalidDropdownOptionSelectedError = "Option '%s' was not an option provided to the user."
  val emptyValueError = "There was no number entered for the %s."
  val numberError = "%s entered must be a whole number."
  val negativeNumberError = "%s must not be a negative number."
  val invalidYearError = "The year should be 4 digits in length."
  val invalidDateError = "%s is an invalid %s number."
  val invalidDayError = "%s does not have %d days."
  val invalidDayForLeapYearError = "%s %d does not have %d days in it."
}

object RadioButtonGroupField {

  def update(radioButtonGroupField: RadioButtonGroupField, value: Boolean): RadioButtonGroupField =
    radioButtonGroupField.copy(selectedOption = if (value) "yes" else "no")
}

object DropdownField {

  def validate(option: String, dropdownField: DropdownField): Option[String] =
    option match {
      case "" => Some(dropdownOptionNotSelectedError.format(dropdownField.fieldName))
      case value if !dropdownField.options.exists(_.value == value) => Some(invalidDropdownOptionSelectedError.format(value))
      case _ => None
    }

  def update(dropdownField: DropdownField, value: String): DropdownField =
    dropdownField.copy(selectedOption = Some(InputNameAndValue(value, value)))
}

object TextField {

  def validate(text: String, textField: TextField): Option[String] =
    if (textField.inputMode.equals("numeric")) {
      val inputName = textField.nameAndValue.name
      text match {
        case "" => Some(emptyValueError.format(inputName))
        case t if allCatch.opt(t.toInt).isEmpty => Some(numberError.format(inputName))
        case t if t.toInt < 0 => Some(negativeNumberError.format(inputName))
        case _ => None
      }
    } else {
      None
    }

  def update(textField: TextField, value: String): TextField = {
    textField.copy(nameAndValue = textField.nameAndValue.copy(value = value))
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

  def validate(day: String, month: String, year: String): Option[String] = {

    var error = validateValue(day, "Day", invalidDayValidation)
    error = if (error.isEmpty) validateValue(month, "Month", invalidMonthValidation) else error
    error = if (error.isEmpty) validateValue(year, "Year", invalidYearValidation) else error

    if (error.isEmpty) {
      checkDayForTheMonthAndYear(day.toInt, month.toInt, year.toInt)
    } else {
      error
    }
  }

  def update(dateField: DateField, localDateTime: LocalDateTime): DateField =
    dateField.copy(
      day = dateField.day.copy(value = localDateTime.getDayOfMonth.toString),
      month = dateField.month.copy(value = localDateTime.getMonthValue.toString),
      year = dateField.year.copy(value = localDateTime.getYear.toString))

  def update(dateField: DateField, day: String, month: String, year: String): DateField =
    dateField.copy(
      day = dateField.day.copy(value = day),
      month = dateField.month.copy(value = month),
      year = dateField.year.copy(value = year))

  private def validateValue(day: String, unitType: String, isInvalidDate: Int => Boolean): Option[String] =
    day match {
      case "" => Some(emptyValueError.format(unitType))
      case d if allCatch.opt(d.toInt).isEmpty => Some(numberError.format(unitType))
      case d if d.toInt < 0 => Some(negativeNumberError.format(unitType))
      case d if isInvalidDate(d.toInt) => if (unitType.equals("Year")) {
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
}
