package controllers.util

import cats.implicits.catsSyntaxOptionId
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
    val radioButtonGroupField =
      RadioButtonGroupField(
        "id",
        "name",
        "alternativeName",
        "desc",
        "details",
        multiValue = false,
        Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
        "no",
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

    "validate should not return any error when the given value is valid" in {
      List("yes", "no").foreach { validValue =>
        RadioButtonGroupField.validate(validValue, Map.empty, radioButtonGroupField) shouldBe List()
      }
    }

    "validate should return an error when the given value is invalid" in {
      List("agreed", "disagree").foreach { validValue =>
        RadioButtonGroupField.validate(validValue, Map.empty, radioButtonGroupField) shouldBe List(s"Option '$validValue' was not an option provided to the user.")
      }
    }
  }

  "TextField" should {
    val textField = TextField("id", "name", "alternativeName", "desc", multiValue = false, InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)
    val closureTextField = TextField("ClosurePeriod", "name", "alternativeName", "desc", multiValue = false, InputNameAndValue("years", "0", "0"), "numeric", isRequired = true)

    "update should set value for the field" in {

      TextField.update(textField, "12") shouldBe textField.copy(nameAndValue = InputNameAndValue("years", "12", "0"))
    }

    "validate should not return any error when the given value is valid" in {

      List("1", "01", "0010", "20", "500", "1548883").foreach { validValue =>
        TextField.validate(validValue, textField) shouldBe None
      }
    }

    "validate should return an error when the given value is empty" in {

      TextField.validate("", textField) shouldBe Some("Add an alternativename for this record")
    }

    "validate should return an error when the given value is empty for closurePeriod" in {

      TextField.validate("", closureTextField) shouldBe Some("Enter the number of years the record is closed from the closure start date")
    }

    "validate should return an error when the given value is not a numeric value" in {

      List("1b12", "b1", "notNumeric", "e").foreach { invalidValue =>
        TextField.validate(invalidValue, textField) shouldBe Some("The name must be a whole number, like 3, 15, 21")
      }
    }

    "validate should return an error when the given value is a negative value" in {

      List("-1", "-12", "-03").foreach { negativeNumber =>
        TextField.validate(negativeNumber, textField) shouldBe Some("The name cannot be a negative number")
      }
    }
  }

  "TextAreaField" should {

    "update should set value for the field" in {
      val updatedField = TextAreaField("id", "name", "alternativeName", "desc", multiValue = false, InputNameAndValue("name", "old inputted value"), isRequired = false)
      TextAreaField.update(updatedField, "new inputted value") shouldBe updatedField.copy(nameAndValue = InputNameAndValue("name", "new inputted value", ""))
    }

    "validate should return an error if the given value is empty and the field is required" in {
      val requiredField = TextAreaField("id", "FieldName", "alternativeName", "desc", multiValue = false, InputNameAndValue("name", ""), isRequired = true)
      TextAreaField.validate("", requiredField) shouldBe Some("Add an alternativename for this record")
    }

    "validate should not return an error if the given value is empty and the field is not required" in {
      val nonRequiredField = TextAreaField("id", "FieldName", "alternativeName", "desc", multiValue = false, InputNameAndValue("name", ""), isRequired = false)
      TextAreaField.validate("", nonRequiredField) shouldBe None
    }

    "validate should return an error if the given value is large than the specific character limit" in {
      val tooLargeValueField =
        TextAreaField("id", "FieldName", "alternativeName", "desc", multiValue = false, InputNameAndValue("name", ""), isRequired = false, characterLimit = 5)
      TextAreaField.validate("more than character limit", tooLargeValueField) shouldBe Some("FieldName must be 5 characters or less")
    }
  }

  "DropdownField" should {
    val dropdownField =
      DropdownField("id", "name", "alternativeName", "desc", multiValue = true, Seq(InputNameAndValue("Open", "Open"), InputNameAndValue("34", "34")), None, isRequired = true)

    "update should set value for the field" in {

      DropdownField.update(dropdownField, "34".some) shouldBe dropdownField.copy(selectedOption = Some(InputNameAndValue("34", "34")))
    }

    "validate should not return an error when the given value is valid option" in {

      DropdownField.validate("34".some, dropdownField) shouldBe None
    }

    "validate should return an error when the given value is empty" in {

      DropdownField.validate(None, dropdownField) shouldBe Some("Search for and select at least one name")
    }

    "validate should return an error when the given value is not a valid option" in {

      DropdownField.validate("ABC".some, dropdownField) shouldBe Some("Option 'ABC' was not an option provided to the user.")
    }
  }

  "MultiSelectField" should {
    val multiSelectField =
      MultiSelectField(
        "id",
        "name",
        "alternativeName",
        "desc",
        "guidance",
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

    "validate should not return an error when the given value is valid option" in {

      MultiSelectField.validate(Seq("34"), multiSelectField) shouldBe None
    }

    "validate should return an error when the given value is empty" in {

      MultiSelectField.validate(Nil, multiSelectField) shouldBe Some("Search for and select at least one name")
    }

    "validate should return an error when the given value is not a valid option" in {

      MultiSelectField.validate(Seq("ABC"), multiSelectField) shouldBe Some("Option 'ABC' was not an option provided to the user.")
    }
  }

  "DateField" should {
    val dateField = DateField(
      "id",
      "name",
      "alternativename",
      "desc",
      multiValue = false,
      InputNameAndValue("Day", "1", "DD"),
      InputNameAndValue("Month", "12", "MM"),
      InputNameAndValue("Year", "1990", "YYYY"),
      isRequired = true
    )

    "update should set day, month and year for the field" in {

      val updatedField = DateField.update(dateField, "12", "1", "1999")
      updatedField shouldBe dateField.copy(
        day = InputNameAndValue("Day", "12", "DD"),
        month = InputNameAndValue("Month", "1", "MM"),
        year = InputNameAndValue("Year", "1999", "YYYY")
      )
    }

    "update should set day, month and year when the date is LocalDateTome" in {

      val dateTime = LocalDateTime.of(2015, Month.JULY, 29, 19, 30, 40)
      val updatedField = DateField.update(dateField, dateTime)
      updatedField shouldBe dateField.copy(
        day = InputNameAndValue("Day", "29", "DD"),
        month = InputNameAndValue("Month", "7", "MM"),
        year = InputNameAndValue("Year", "2015", "YYYY")
      )
    }

    "validate should not return any error when the given date is valid" in {
      DateField.validate("12", "2", "1990", dateField) shouldBe None
      DateField.validate("30", "4", "1990", dateField) shouldBe None
      DateField.validate("29", "2", "2000", dateField) shouldBe None
      DateField.validate("1", "10", "2022", dateField) shouldBe None
    }

    "validate should return an error when the given day, month and year is empty" in {
      DateField.validate("", "", "", dateField) shouldBe Some("Enter the alternativename for this record")
    }

    "validate should return an error when the given day, month or year is empty" in {
      DateField.validate("", "2", "1990", dateField) shouldBe Some("The alternativename must contain a day")
      DateField.validate("1", "", "1990", dateField) shouldBe Some("The alternativename must contain a month")
      DateField.validate("1", "2", "", dateField) shouldBe Some("The alternativename must contain a year")
    }

    "validate should return an error when the given day, month or year is not a numeric value" in {

      List("1b12", "b1", "notNumeric", "e").foreach { invalidValue =>
        DateField.validate(invalidValue, "2", "1990", dateField) shouldBe Some("The day of the alternativename must be a whole number, like 3, 15, 21")
        DateField.validate("1", invalidValue, "1990", dateField) shouldBe Some("The month of the alternativename must be a whole number, like 3, 9, 12")
        DateField.validate("1", "2", invalidValue, dateField) shouldBe Some("The year of the alternativename must be a whole number, like 1994, 2000, 2023")
      }
    }

    "validate should return an error when the given day, month or year is a negative value" in {

      List(("-1", "-1990"), ("-12", "-9999"), ("-03", "-2563")).foreach { case (negativeDayOrNumber, negativeYear) =>
        DateField.validate(negativeDayOrNumber, "2", "1990", dateField) shouldBe Some("The day cannot be a negative number")
        DateField.validate("1", negativeDayOrNumber, "1990", dateField) shouldBe Some("The month cannot be a negative number")
        DateField.validate("1", "2", negativeYear, dateField) shouldBe Some("The year cannot be a negative number")
      }
    }

    "validate should not return an error when the given year is 4 digits long" in {

      List("1000", "2022").foreach { fourDigitYear =>
        DateField.validate("1", "2", fourDigitYear, dateField) shouldBe None
      }
    }

    "validate should return an error when the given year is a less than or greater than 4 digit" in {

      List("1", "40", "199", "19999", "300000").foreach { nonFourDigitYear =>
        DateField.validate("1", "2", nonFourDigitYear, dateField) shouldBe Some("The year of the alternativename must contain 4 digits")
      }
    }

    "validate should return an error when the given day/month number is more/less than what is possible" in {

      DateField.validate("0", "2", "1990", dateField) shouldBe Some("The day of the alternativename must be between 1 and 31")
      DateField.validate("-0", "2", "1990", dateField) shouldBe Some("The day of the alternativename must be between 1 and 31")
      DateField.validate("12", "0", "1990", dateField) shouldBe Some("The month of the alternativename must be between 1 and 12")
      DateField.validate("12", "-0", "1990", dateField) shouldBe Some("The month of the alternativename must be between 1 and 12")
      List(("32", "13"), ("54", "31"), ("100", "64")).foreach { case (invalidDay, invalidMonth) =>
        DateField.validate(invalidDay, "1", "1990", dateField) shouldBe Some(s"The day of the alternativename must be between 1 and 31")
        DateField.validate("12", invalidMonth, "1990", dateField) shouldBe Some(s"The month of the alternativename must be between 1 and 12")
      }
    }

    "validate should not return an error if 31 days was input for the day and the month entered has 31 days" in {

      List("01", "03", "05", "07", "08", "10", "12").foreach { monthWith31Days =>
        DateField.validate("31", monthWith31Days, "1990", dateField) shouldBe None
      }
    }

    "validate should return an error if 31 days was input for the day and the month entered does not have 31 days" in {

      List("02", "04", "06", "09", "11").foreach { monthWithLessThan31Days =>
        DateField.validate("31", monthWithLessThan31Days, "1990", dateField) shouldBe
          Some(s"${monthsWithLessThan31Days(monthWithLessThan31Days.toInt)} does not have 31 days in it. Enter the day for the alternativename between 1 and 30")
      }
    }

    "validate should not return an error if 30 days was input for the day and the month entered has 30 days" in {

      List("01", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12").foreach { monthWithAtLeast30Days =>
        DateField.validate("30", monthWithAtLeast30Days, "1990", dateField) shouldBe None
      }
    }

    "validate should return an error if 30 days was input for the day and the month entered was February" in {

      DateField.validate("30", "02", "1990", dateField) shouldBe Some(s"February does not have 30 days in it. Enter the day for the alternativename between 1 and 30")
    }

    "validate should return no errors if February does have 29 days that year (leap year)" in {

      List("2000", "2008", "2012", "2016", "2020").foreach { leapYear =>
        DateField.validate("29", "02", leapYear, dateField) shouldBe None
      }
    }

    "validate should return an error if February does not have 29 days that year (not leap year)" in {
      // the first 4 years are special because they are skipped leap years

      List("1700", "1800", "1900", "2100", "2023", "2027", "2031", "2035").foreach { nonLeapYear =>
        DateField.validate("29", "02", nonLeapYear, dateField) shouldBe Some(s"February does not have 29 days in it. Enter the day for the alternativename between 1 and 28")
      }
    }

    "validate should return only the error for the Day if the day has an error, even if Month and/or Year have errors" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("", "-1", "notYear", dateField) shouldBe Some("The alternativename must contain a day")
      DateField.validate("33", "13", "-2022", dateField) shouldBe Some("The day of the alternativename must be between 1 and 31")
      DateField.validate("-1", "", "19904", dateField) shouldBe Some("The day cannot be a negative number")
      DateField.validate("0", "month", "42", dateField) shouldBe Some("The day of the alternativename must be between 1 and 31")
    }

    "validate should return only the error for the Month if the Day has no errors but Month has an error, even if Year has an error" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("1", "-1", "notYear", dateField) shouldBe Some("The month cannot be a negative number")
      DateField.validate("12", "13", "-2022", dateField) shouldBe Some("The month of the alternativename must be between 1 and 12")
      DateField.validate("25", "", "19904", dateField) shouldBe Some("The alternativename must contain a month")
      DateField.validate("8", "month", "42", dateField) shouldBe Some("The month of the alternativename must be a whole number, like 3, 9, 12")
    }

    "validate should return only the error for the Year if the Day and Month have no errors" in {
      // We must only show the user one error at a time so if there are 2 or more, we must show the foremost error

      DateField.validate("1", "6", "notYear", dateField) shouldBe Some("The year of the alternativename must be a whole number, like 1994, 2000, 2023")
      DateField.validate("12", "1", "-2022", dateField) shouldBe Some("The year cannot be a negative number")
      DateField.validate("25", "8", "19904", dateField) shouldBe Some("The year of the alternativename must contain 4 digits")
      DateField.validate("8", "2", "42", dateField) shouldBe Some("The year of the alternativename must contain 4 digits")
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
      date.foreach { case (day, month, year) =>
        DateField.validate(day, month, year, dateField.copy(isFutureDateAllowed = false)) shouldBe Some(s"The date of the ${dateField.fieldAlternativeName} must be in the past")
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
      date.foreach { case (day, month, year) =>
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
      date.foreach { case (day, month, year) =>
        DateField.validate(day, month, year, dateField.copy(isFutureDateAllowed = true)) shouldBe None
      }
    }

    def getDate(dateTime: LocalDateTime): (String, String, String) =
      (dateTime.getDayOfMonth.toString, dateTime.getMonthValue.toString, dateTime.getYear.toString)
  }
}
