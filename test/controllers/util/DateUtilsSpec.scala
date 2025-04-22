package controllers.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class DateUtilsSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  "DateUtils" should {
    "format ZonedDateTime to a string with the default format" in {
      val dateTime = ZonedDateTime.of(2023, 6, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val expected = "19/06/2023 16:30:00"

      val result = DateUtils.format(dateTime)

      result should equal(expected)
    }

    "format ZonedDateTime to a string with a custom format" in {
      val dateTime = ZonedDateTime.of(2023, 6, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val customFormat = "yyyy-MM-dd HH:mm:ss"
      val expected = "2023-06-19 16:30:00"

      val result = DateUtils.format(dateTime, customFormat)

      result should be(expected)
    }

  }

  "ExcelUtils covertToLocalDateOrString" should {
    "correctly parse yyyy-MM-dd Date" in {
      val date = DateUtils.covertToLocalDateOrString("2023-10-01")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-10-01"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse yyyy/MM/dd Date" in {
      val date = DateUtils.covertToLocalDateOrString("2023/10/01")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-10-01"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse 2021-02-03T10:33:30.414 Date" in {
      val date = DateUtils.covertToLocalDateOrString("2021-02-03T10:33:30.414")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2021-02-03"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse 2023-06-19 16:30:00 Date" in {
      val date = DateUtils.covertToLocalDateOrString("2023-06-19 16:30:00")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-06-19"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse unknown date format dd-mm-yyyy to String" in {
      val date = DateUtils.covertToLocalDateOrString("12-10-2021")
      date match {
        case unknownDate: String => unknownDate shouldBe "12-10-2021"
        case _                   => fail("Date parsing failed")
      }
    }
  }
}
