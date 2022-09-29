package controllers.util


import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.{LocalDateTime, Month}

class FormFieldSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  private val monthsWithLessThan31Days = Map(
    2 -> "February",
    4 -> "April",
    6 -> "June",
    9 -> "September",
    11 -> "November"
  )

  "RadioButtonGroupField" should {
    val radioButtonGroupField = RadioButtonGroupField("id", "name", "desc",
      Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")), "no", isRequired = true)

    "update should set selectedOption as 'yes' for the field" in {

      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, value = true)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "yes")
    }

    "update should set selectedOption as 'no' for the field" in {

      val updatedField = RadioButtonGroupField.update(radioButtonGroupField, value = false)
      updatedField shouldBe radioButtonGroupField.copy(selectedOption = "no")
    }
  }

  "TextField" should {
    val textField = TextField("id", "name", "desc", InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)

    "update should set value for the field" in {

      TextField.update(textField, "12") shouldBe textField.copy(nameAndValue = InputNameAndValue("years", "12", "0"))
    }

    "validate should not return any error when the given value is valid" in {

      List("1", "01", "0010", "20", "500", "1548883").foreach {
        validValue => TextField.validate(validValue, textField) shouldBe None
      }
    }

    "validate should return an error when the given value is empty" in {

      TextField.validate("", textField) shouldBe Some("There was no number entered for the years.")
    }

    "validate should return an error when the given value is not a numeric value" in {

      List("1b12", "b1", "notNumeric", "e").foreach {
        invalidValue => TextField.validate(invalidValue, textField) shouldBe Some("years entered must be a whole number.")
      }
    }

    "validate should return an error when the given value is a negative value" in {

      List("-1", "-12", "-03").foreach {
        negativeNumber => TextField.validate(negativeNumber, textField) shouldBe Some("years must not be a negative number.")
      }
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

      DateField.validate("12", "2", "1990", dateField) shouldBe None
      DateField.validate("30", "4", "1990", dateField) shouldBe None
      DateField.validate("29", "2", "2000", dateField) shouldBe None
      DateField.validate("1", "10", "2025", dateField) shouldBe None
    }

    "validate should return an error when the given day, month or year is empty" in {
      DateField.validate("", "2", "1990", dateField) shouldBe Some("There was no number entered for the Day.")
      DateField.validate("1", "", "1990", dateField) shouldBe Some("There was no number entered for the Month.")
      DateField.validate("1", "2", "", dateField) shouldBe Some("There was no number entered for the Year.")
    }

    "validate should return an error when the given day, month or year is not a numeric value" in {

      List("1b12", "b1", "notNumeric", "e").foreach {
        invalidValue =>
          DateField.validate(invalidValue, "2", "1990", dateField) shouldBe Some("Day entered must be a whole number.")
          DateField.validate("1", invalidValue, "1990", dateField) shouldBe Some("Month entered must be a whole number.")
          DateField.validate("1", "2", invalidValue, dateField) shouldBe Some("Year entered must be a whole number.")
      }
    }

    "validate should return an error when the given day, month or year is a negative value" in {

      List(("-1", "-1990"), ("-12", "-9999"), ("-03", "-2563")).foreach {
        case (negativeDayOrNumber, negativeYear) =>
          DateField.validate(negativeDayOrNumber, "2", "1990", dateField) shouldBe Some("Day must not be a negative number.")
          DateField.validate("1", negativeDayOrNumber, "1990", dateField) shouldBe Some("Month must not be a negative number.")
          DateField.validate("1", "2", negativeYear, dateField) shouldBe Some("Year must not be a negative number.")
      }
    }

    "validate should not return an error when the given year is 4 digits long" in {

      List("1000", "2022", "9999").foreach {
        fourDigitYear => DateField.validate("1", "2", fourDigitYear, dateField) shouldBe None
      }
    }

    "validate should return an error when the given year is a less than or greater than 4 digit" in {

      List("1", "40", "199", "19999", "300000").foreach {
        nonFourDigitYear => DateField.validate("1", "2", nonFourDigitYear, dateField) shouldBe Some("The year should be 4 digits in length.")
      }
    }

    "validate should return an error when the given day/month number is more/less than what is possible" in {

      DateField.validate("0", "2", "1990", dateField) shouldBe Some("0 is an invalid Day number.")
      DateField.validate("-0", "2", "1990", dateField) shouldBe Some("-0 is an invalid Day number.")
      DateField.validate("12", "0", "1990", dateField) shouldBe Some("0 is an invalid Month number.")
      DateField.validate("12", "-0", "1990", dateField) shouldBe Some("-0 is an invalid Month number.")
      List(("32", "13"), ("54", "31"), ("100", "64")).foreach {
        case (invalidDay, invalidMonth) =>
          DateField.validate(invalidDay, "1", "1990", dateField) shouldBe Some(s"$invalidDay is an invalid Day number.")
          DateField.validate("12", invalidMonth, "1990", dateField) shouldBe Some(s"$invalidMonth is an invalid Month number.")
      }
    }

    "validate should not return an error if 31 days was input for the day and the month entered has 31 days" in {

      List("01", "03", "05", "07", "08", "10", "12").foreach {
        monthWith31Days => DateField.validate("31", monthWith31Days, "1990", dateField) shouldBe None
      }
    }

    "validate should return an error if 31 days was input for the day and the month entered does not have 31 days" in {

      List("02", "04", "06", "09", "11").foreach {
        monthWithLessThan31Days =>
          DateField.validate("31", monthWithLessThan31Days, "1990", dateField) shouldBe
            Some(s"${monthsWithLessThan31Days(monthWithLessThan31Days.toInt)} does not have 31 days.")
      }
    }

    "validate should not return an error if 30 days was input for the day and the month entered has 30 days" in {

      List("01", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12").foreach {
        monthWithAtLeast30Days => DateField.validate("30", monthWithAtLeast30Days, "1990", dateField) shouldBe None
      }
    }

    "validate should return an error if 30 days was input for the day and the month entered was February" in {

      DateField.validate("30", "02", "1990", dateField) shouldBe Some(s"February does not have 30 days.")
    }

    "validate should return no errors if February does have 29 days that year (leap year)" in {

      List("2024", "2028", "2032", "2036", "2040", "2044", "2048", "2052").foreach {
        leapYear => DateField.validate("29", "02", leapYear, dateField) shouldBe None
      }
    }

    "validate should return an error if February does not have 29 days that year (not leap year)" in {
      // the first 4 years are special because they are skipped leap years

      List("1700", "1800", "1900", "2100", "2023", "2027", "2031", "2035").foreach {
        nonLeapYear => DateField.validate("29", "02", nonLeapYear, dateField) shouldBe Some(s"February $nonLeapYear does not have 29 days in it.")
      }
    }

    "validate should return only the error for the Day if the day has an error, even if Month and/or Year have errors" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("", "-1", "notYear", dateField) shouldBe Some("There was no number entered for the Day.")
      DateField.validate("33", "13", "-2022", dateField) shouldBe Some("33 is an invalid Day number.")
      DateField.validate("-1", "", "19904", dateField) shouldBe Some("Day must not be a negative number.")
      DateField.validate("0", "month", "42", dateField) shouldBe Some("0 is an invalid Day number.")
    }

    "validate should return only the error for the Month if the Day has no errors but Month has an error, even if Year has an error" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("1", "-1", "notYear", dateField) shouldBe Some("Month must not be a negative number.")
      DateField.validate("12", "13", "-2022", dateField) shouldBe Some("13 is an invalid Month number.")
      DateField.validate("25", "", "19904", dateField) shouldBe Some("There was no number entered for the Month.")
      DateField.validate("8", "month", "42", dateField) shouldBe Some("Month entered must be a whole number.")
    }

    "validate should return only the error for the Year if the Day and Month have no errors" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("1", "6", "notYear", dateField) shouldBe Some("Year entered must be a whole number.")
      DateField.validate("12", "1", "-2022", dateField) shouldBe Some("Year must not be a negative number.")
      DateField.validate("25", "8", "19904", dateField) shouldBe Some("The year should be 4 digits in length.")
      DateField.validate("8", "2", "42", dateField) shouldBe Some("The year should be 4 digits in length.")
    }

    "monthsWithLessThan31Days should only contain months with less than 31 days" in {
      // This is here to ensure that monthsWithLessThan31Days does not accidentally change

      DateField.monthsWithLessThan31Days should equal(monthsWithLessThan31Days)
    }

    "validate should return an error when future date is not allowed but the given date is in future" in {

      val date = List(
        getDate(LocalDateTime.now().plusDays(1)),
        getDate(LocalDateTime.now().plusMonths(1)),
        getDate(LocalDateTime.now().plusYears(1))
      )
      date.foreach {
        case (day, month, year) =>
          DateField.validate(day, month, year, dateField.copy(isFutureDateAllowed = false)) shouldBe Some(s"${dateField.fieldName} date cannot be a future date.")
      }
    }

    "validate should not return an error when future date is not allowed but the given date is present or past" in {

      val date = List(
        getDate(LocalDateTime.now()),
        getDate(LocalDateTime.now().plusMinutes(1)),
        getDate(LocalDateTime.now().minusDays(1)),
        getDate(LocalDateTime.now().minusMonths(1)),
        getDate(LocalDateTime.now().minusYears(1))
      )
      date.foreach {
        case (day, month, year) =>
          DateField.validate(day, month, year, dateField.copy(isFutureDateAllowed = false)) shouldBe None
      }
    }

    "validate should not return an error when future date is allowed and the given date is present, past or future" in {

      val date = List(
        getDate(LocalDateTime.now()),
        getDate(LocalDateTime.now().plusMinutes(1)),
        getDate(LocalDateTime.now().minusDays(1)),
        getDate(LocalDateTime.now().minusMonths(1)),
        getDate(LocalDateTime.now().minusYears(1)),
        getDate(LocalDateTime.now().plusDays(1)),
        getDate(LocalDateTime.now().plusMonths(1)),
        getDate(LocalDateTime.now().plusYears(1))
      )
      date.foreach {
        case (day, month, year) =>
          DateField.validate(day, month, year, dateField.copy(isFutureDateAllowed = true)) shouldBe None
      }
    }

    def getDate(dateTime: LocalDateTime): (String, String, String) =
      (dateTime.getDayOfMonth.toString, dateTime.getMonthValue.toString, dateTime.getYear.toString)
  }
}
