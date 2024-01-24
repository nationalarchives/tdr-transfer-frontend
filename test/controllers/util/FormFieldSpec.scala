package controllers.util

import cats.implicits.catsSyntaxOptionId
import controllers.util.MetadataProperty.closurePeriod
import org.apache.commons.lang3.NotImplementedException
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.nationalarchives.tdr.validation.ErrorCode._

import java.time.{LocalDateTime, Month}

class FormFieldSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  "RadioButtonGroupField" should {
    val radioButtonGroupField =
      RadioButtonGroupField(
        "id",
        "name",
        "alternativeName",
        "desc",
        Nil,
        "details",
        multiValue = false,
        Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
        "no",
        isRequired = true
      )
    val textField = TextField("AlternateTitle", "name", "alternativeName", "desc", Nil, multiValue = false, InputNameAndValue("AlternativeTitle", ""), "text", isRequired = false)
    val textAreaField = TextAreaField(
      "AlternateDescription",
      "name",
      "alternativeDesc",
      "desc",
      Nil,
      multiValue = false,
      InputNameAndValue("AlternativeDesc", ""),
      isRequired = false,
      details = None
    )
    val dateField = DateField(
      "id",
      "name",
      "alternativename",
      "desc",
      Nil,
      multiValue = false,
      InputNameAndValue("Day", "1", "DD"),
      InputNameAndValue("Month", "12", "MM"),
      InputNameAndValue("Year", "1990", "YYYY"),
      isRequired = true
    )

    "update should set selectedOption as 'yes' for the field" in {
      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, value = true)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "yes")
    }

    "update should set selectedOption as 'no' for the field" in {
      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, value = false)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "no")
    }

    "updateError should set an error message as per the given error code" in {
      RadioButtonGroupField.updateError(radioButtonGroupField, NO_OPTION_SELECTED_ERROR).fieldErrors shouldBe List("Select if the name is sensitive to the public")
      RadioButtonGroupField
        .updateError(radioButtonGroupField.copy(selectedOption = "agreed"), UNDEFINED_VALUE_ERROR)
        .fieldErrors shouldBe List("Option 'agreed' was not an option provided to the user.")
      RadioButtonGroupField
        .updateError(radioButtonGroupField.copy(selectedOption = "yes", dependencies = Map("yes" -> List(textAreaField))), EMPTY_VALUE_ERROR)
        .fieldErrors shouldBe List("Add an alternativedesc for this record")
      RadioButtonGroupField
        .updateError(radioButtonGroupField.copy(selectedOption = "yes", dependencies = Map("yes" -> List(textAreaField))), MAX_CHARACTER_LIMIT_INPUT_ERROR)
        .fieldErrors shouldBe List("alternativeDesc must be 8000 characters or less")
      RadioButtonGroupField.updateError(radioButtonGroupField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }
  }

  "TextField" should {
    val textField = TextField("id", "name", "alternativeName", "desc", Nil, multiValue = false, InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)
    val optionalTextField = textField.copy(isRequired = false)
    val closureTextField =
      TextField("ClosurePeriod", "name", "alternativeName", "desc", Nil, multiValue = false, InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)

    "update should set value for the field" in {

      TextField.update(textField, "12") shouldBe textField.copy(nameAndValue = InputNameAndValue("years", "12", "0"))
    }

    "updateError should set an error message as per the given error code" in {
      TextField.updateError(textField, EMPTY_VALUE_ERROR).fieldErrors shouldBe List("Enter the name for this record")
      TextField.updateError(textField.copy(fieldId = closurePeriod), EMPTY_VALUE_ERROR).fieldErrors shouldBe List(
        "Enter the number of years the record is closed from the closure start date"
      )
      TextField.updateError(textField, NUMBER_ONLY_ERROR).fieldErrors shouldBe List("The name must be a whole number, like 3, 15, 21")
      TextField.updateError(textField, NEGATIVE_NUMBER_ERROR).fieldErrors shouldBe List("The name cannot be a negative number")
      TextField.updateError(textField, ZERO_NUMBER_ERROR).fieldErrors shouldBe List("The name cannot be 0")
      TextField.updateError(textField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }
  }

  "TextAreaField" should {

    val updatedField =
      TextAreaField("id", "name", "alternativeName", "desc", Nil, multiValue = false, InputNameAndValue("name", "old inputted value"), isRequired = false, details = None)

    "update should set value for the field" in {
      TextAreaField.update(updatedField, "new inputted value") shouldBe updatedField.copy(nameAndValue = InputNameAndValue("name", "new inputted value", ""))
    }

    "updateError should set an error message as per the given error code" in {
      TextAreaField.updateError(updatedField, MAX_CHARACTER_LIMIT_INPUT_ERROR).fieldErrors shouldBe List("name must be 8000 characters or less")
      TextAreaField.updateError(updatedField, EMPTY_VALUE_ERROR).fieldErrors shouldBe List("Add an alternativename for this record")
      TextAreaField.updateError(updatedField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }
  }

  "DropdownField" should {
    val dropdownField =
      DropdownField("id", "name", "alternativeName", "desc", Nil, multiValue = true, Seq(InputNameAndValue("Open", "Open"), InputNameAndValue("34", "34")), None, isRequired = true)

    val optionalDropdownField = dropdownField.copy(isRequired = false)
    "update should set value for the field" in {

      DropdownField.update(dropdownField, "34".some) shouldBe dropdownField.copy(selectedOption = Some(InputNameAndValue("34", "34")))
    }

    "updateError should set an error message as per the given error code" in {
      DropdownField.updateError(dropdownField, EMPTY_VALUE_ERROR).fieldErrors shouldBe List("Select at least one name")
      DropdownField
        .updateError(dropdownField.copy(selectedOption = InputNameAndValue("Test", "Test").some), UNDEFINED_VALUE_ERROR)
        .fieldErrors shouldBe List("Option 'Test' was not an option provided to the user.")
      DropdownField.updateError(dropdownField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }
  }

  "MultiSelectField" should {
    val multiSelectField =
      MultiSelectField(
        "id",
        "name",
        "alternativeName",
        "desc",
        Nil,
        multiValue = true,
        Seq(InputNameAndValue("Open", "Open"), InputNameAndValue("34", "34")),
        None,
        isRequired = true
      )

    "update should set value for the field" in {

      MultiSelectField.update(multiSelectField, Seq("34")) shouldBe multiSelectField.copy(selectedOption = Some(List(InputNameAndValue("34", "34"))))
      MultiSelectField.update(multiSelectField, Seq("Open", "34")) shouldBe multiSelectField.copy(selectedOption =
        Some(List(InputNameAndValue("Open", "Open"), InputNameAndValue("34", "34")))
      )
    }

    "updateError should set an error message as per the given error code" in {
      MultiSelectField.updateError(multiSelectField, EMPTY_VALUE_ERROR).fieldErrors shouldBe List("Select at least one name")
      MultiSelectField
        .updateError(multiSelectField.copy(selectedOption = List(InputNameAndValue("Test", "Test")).some), UNDEFINED_VALUE_ERROR)
        .fieldErrors shouldBe List("Option 'Test' was not an option provided to the user.")
      MultiSelectField.updateError(multiSelectField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }
  }

  "DateField" should {
    val mandatoryDateField = DateField(
      "id",
      "name",
      "alternativename",
      "desc",
      Nil,
      multiValue = false,
      InputNameAndValue("Day", "1", "DD"),
      InputNameAndValue("Month", "12", "MM"),
      InputNameAndValue("Year", "1990", "YYYY"),
      isRequired = true
    )

    val optionalDateField = mandatoryDateField.copy(isRequired = false)

    "update should set day, month and year for the field" in {

      val updatedField = DateField.update(mandatoryDateField, "12", "1", "1999")
      updatedField shouldBe mandatoryDateField.copy(
        day = InputNameAndValue("Day", "12", "DD"),
        month = InputNameAndValue("Month", "1", "MM"),
        year = InputNameAndValue("Year", "1999", "YYYY")
      )
    }

    "update should set day, month and year when the date is LocalDateTome" in {

      val dateTime = LocalDateTime.of(2015, Month.JULY, 29, 19, 30, 40)
      val updatedField = DateField.update(mandatoryDateField, dateTime)
      updatedField shouldBe mandatoryDateField.copy(
        day = InputNameAndValue("Day", "29", "DD"),
        month = InputNameAndValue("Month", "7", "MM"),
        year = InputNameAndValue("Year", "2015", "YYYY")
      )
    }

    "monthStringFromNumber should returned the expected string representations" in {
      List(
        (1, "January"),
        (2, "February"),
        (3, "March"),
        (4, "April"),
        (5, "May"),
        (6, "June"),
        (7, "July"),
        (8, "August"),
        (9, "September"),
        (10, "October"),
        (11, "November"),
        (12, "December")
      ).foreach { case (number, expectedString) =>
        DateField.monthStringFromNumber(number) shouldBe expectedString
      }
    }

    "updateError should set an error message as per the given error code" in {

      DateField.updateError(mandatoryDateField, EMPTY_VALUE_ERROR).fieldErrors shouldBe List("Enter the alternativename for this record")
      DateField.updateError(mandatoryDateField, EMPTY_VALUE_ERROR_FOR_DAY).fieldErrors shouldBe List("The alternativename must contain a day")
      DateField.updateError(mandatoryDateField, NUMBER_ERROR_FOR_DAY).fieldErrors shouldBe List("The day of the alternativename must be a whole number, like 3, 15, 21")
      DateField.updateError(mandatoryDateField, NEGATIVE_NUMBER_ERROR_FOR_DAY).fieldErrors shouldBe List("The day cannot be a negative number")
      DateField.updateError(mandatoryDateField, EMPTY_VALUE_ERROR_FOR_MONTH).fieldErrors shouldBe List("The alternativename must contain a month")
      DateField.updateError(mandatoryDateField, NUMBER_ERROR_FOR_MONTH).fieldErrors shouldBe List("The month of the alternativename must be a whole number, like 3, 9, 12")
      DateField.updateError(mandatoryDateField, NEGATIVE_NUMBER_ERROR_FOR_MONTH).fieldErrors shouldBe List("The month cannot be a negative number")
      DateField.updateError(mandatoryDateField, INVALID_NUMBER_ERROR_FOR_MONTH).fieldErrors shouldBe List("The month of the alternativename must be between 1 and 12")
      DateField.updateError(mandatoryDateField, EMPTY_VALUE_ERROR_FOR_YEAR).fieldErrors shouldBe List("The alternativename must contain a year")
      DateField.updateError(mandatoryDateField, NUMBER_ERROR_FOR_YEAR).fieldErrors shouldBe List("The year of the alternativename must be a whole number, like 1994, 2000, 2023")
      DateField.updateError(mandatoryDateField, NEGATIVE_NUMBER_ERROR_FOR_YEAR).fieldErrors shouldBe List("The year cannot be a negative number")
      DateField.updateError(mandatoryDateField, INVALID_NUMBER_ERROR_FOR_YEAR).fieldErrors shouldBe List("The year of the alternativename must contain 4 digits")
      DateField
        .updateError(mandatoryDateField.copy(day = InputNameAndValue("32", "32"), month = InputNameAndValue("2", "2")), INVALID_NUMBER_ERROR_FOR_DAY)
        .fieldErrors shouldBe List("February does not have 32 days in it. Enter the day for the alternativename between 1 and 28")
      DateField
        .updateError(mandatoryDateField.copy(day = InputNameAndValue("31", "31"), month = InputNameAndValue("6", "6")), INVALID_DAY_FOR_MONTH_ERROR)
        .fieldErrors shouldBe List("June does not have 31 days in it. Enter the day for the alternativename between 1 and 30")
      DateField.updateError(mandatoryDateField, FUTURE_DATE_ERROR).fieldErrors shouldBe List("The date of the alternativename must be in the past")
      DateField.updateError(mandatoryDateField, CLOSURE_STATUS_IS_MISSING).fieldErrors shouldBe List("CLOSURE_STATUS_IS_MISSING")
    }

    def getDate(dateTime: LocalDateTime): (String, String, String) =
      (dateTime.getDayOfMonth.toString, dateTime.getMonthValue.toString, dateTime.getYear.toString)
  }
}
