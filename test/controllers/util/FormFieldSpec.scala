package controllers.util


import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.{LocalDateTime, Month}

class FormFieldSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {


  "RadioButtonGroupField" should {
    val radioButtonGroupField = RadioButtonGroupField("id", "name", "desc",
      Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")), "no", isRequired = true)

    "update should set selectedOption as 'yes' for the field" in {

      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, true)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "yes")
    }

    "update should set selectedOption as 'no' for the field" in {

      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, false)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "no")
    }
  }

  "TextField" should {
    val textField = TextField("id", "name", "desc", InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)

    "update should set value for the field" in {

      TextField.update(textField, "12") shouldBe textField.copy(nameAndValue = InputNameAndValue("years", "12", "0"))
    }

    "validate should not return any error when the given value is valid" in {

      TextField.validate("1", textField) shouldBe None
    }

    "validate should return an error when the given value is empty" in {

      TextField.validate("", textField) shouldBe Some("There was no number entered for the years.")
    }

    "validate should return an error when the given value is a numeric value" in {

      TextField.validate("1b12", textField) shouldBe Some("years entered must be a whole number.")
    }

    "validate should return an error when the given value is a negative value" in {

      TextField.validate("-12", textField) shouldBe Some("years must not be a negative number.")
    }
  }

  "DropdownField" should {
    val dropdownField = DropdownField("id", "name", "desc",
      Seq(InputNameAndValue("Open", "Open"), InputNameAndValue("34", "34")), None, isRequired = true)

    "update should set value for the field" in {

      DropdownField.update(dropdownField, "34") shouldBe dropdownField.copy(selectedOption = Some(InputNameAndValue("34", "34")))
    }

    "validate should not return an error when the given value is valid option" in {

      DropdownField.validate("34", dropdownField) shouldBe None
    }

    "validate should return an error when the given value is empty" in {

      DropdownField.validate("", dropdownField) shouldBe Some("There was no value selected for the name.")
    }

    "validate should return an error when the given value is not a valid option" in {

      DropdownField.validate("ABC", dropdownField) shouldBe Some("Option 'ABC' was not an option provided to the user.")
    }
  }

  "DateField" should {
    val dateField = DateField("id", "name", "desc", InputNameAndValue("Day", "1", "DD"),
      InputNameAndValue("Month", "12", "MM"), InputNameAndValue("Year", "1990", "YYYY"), isRequired = true)

    "update should set day, month and year for the field" in {

      val updatedField = DateField.update(dateField, "12", "1", "1999")
      updatedField shouldBe dateField.copy(day = InputNameAndValue("Day", "12", "DD"),
        month = InputNameAndValue("Month", "1", "MM"),
        year = InputNameAndValue("Year", "1999", "YYYY"))
    }

    "update should set day, month and year when the date is LocalDateTome" in {

      val dateTime = LocalDateTime.of(2015, Month.JULY, 29, 19, 30, 40)
      val updatedField = DateField.update(dateField, dateTime)
      updatedField shouldBe dateField.copy(
        day = InputNameAndValue("Day", "29", "DD"),
        month = InputNameAndValue("Month", "7", "MM"),
        year = InputNameAndValue("Year", "2015", "YYYY"))
    }

    "validate should not return any error when the given date is valid" in {

      DateField.validate("12", "2", "1990") shouldBe None
      DateField.validate("30", "4", "1990") shouldBe None
      DateField.validate("29", "2", "2000") shouldBe None
    }

    "validate should return an error when the given day, month or year is empty" in {

      DateField.validate("", "2", "1990") shouldBe Some("There was no number entered for the Day.")
      DateField.validate("1", "", "1990") shouldBe Some("There was no number entered for the Month.")
      DateField.validate("1", "2", "") shouldBe Some("There was no number entered for the Year.")
    }

    "validate should return an error when the given day, month or year is a numeric value" in {

      DateField.validate("ab", "2", "1990") shouldBe Some("Day entered must be a whole number.")
      DateField.validate("1", "ad", "1990") shouldBe Some("Month entered must be a whole number.")
      DateField.validate("1", "2", "ad") shouldBe Some("Year entered must be a whole number.")
    }

    "validate should return an error when the given day, month or year is a negative value" in {

      DateField.validate("-1", "2", "1990") shouldBe Some("Day must not be a negative number.")
      DateField.validate("1", "-2", "1990") shouldBe Some("Month must not be a negative number.")
      DateField.validate("1", "2", "-1990") shouldBe Some("Year must not be a negative number.")
    }

    "validate should return an error when the given year is a less than or greater than 4 digit" in {
      List("199", "19999").foreach(year =>
        DateField.validate("1", "2", year) shouldBe Some("The year should be 4 digits in length.")
      )
    }

    "validate should return an error when the given day is not valid number for the month year (leap)" in {

      DateField.validate("32", "1", "1990") shouldBe Some("32 is an invalid Day number.")
      DateField.validate("31", "2", "1990") shouldBe Some("February does not have 31 days.")
      DateField.validate("31", "6", "1990") shouldBe Some("June does not have 31 days.")
      DateField.validate("0", "2", "1990") shouldBe Some("0 is an invalid Day number.")
      DateField.validate("29", "2", "1990") shouldBe Some("February 1990 does not have 29 days in it.")
      DateField.validate("12", "13", "1990") shouldBe Some("13 is an invalid Month number.")
      DateField.validate("12", "0", "1990") shouldBe Some("0 is an invalid Month number.")
    }
  }
}
