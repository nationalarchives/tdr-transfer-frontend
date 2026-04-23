package controllers.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit

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

    "return correct day suffix" in {
      DateUtils.getDaySuffix(1) should be("st")
      DateUtils.getDaySuffix(2) should be("nd")
      DateUtils.getDaySuffix(3) should be("rd")
      DateUtils.getDaySuffix(4) should be("th")
      DateUtils.getDaySuffix(11) should be("th")
      DateUtils.getDaySuffix(21) should be("st")
      DateUtils.getDaySuffix(22) should be("nd")
      DateUtils.getDaySuffix(23) should be("rd")
      DateUtils.getDaySuffix(31) should be("st")
    }

    "format date with day suffix, lowercase am/pm and relative days" in {
      val dateTime = ZonedDateTime.of(2026, 4, 23, 10, 32, 0, 0, ZoneId.of("UTC"))
      val result = DateUtils.formatWithDaySuffixAndRelative(dateTime)

      result should include("23rd April 2026, 11:32am")
      result should include("(Today)")
    }

    "format date with Yesterday for a date one day ago" in {
      val dateTime = ZonedDateTime.now().minus(1, ChronoUnit.DAYS)
      val result = DateUtils.formatWithDaySuffixAndRelative(dateTime)

      result should include("(Yesterday)")
    }

    "format date with days ago for older dates" in {
      val dateTime = ZonedDateTime.now().minus(5, ChronoUnit.DAYS)
      val result = DateUtils.formatWithDaySuffixAndRelative(dateTime)

      result should include("(5 days ago)")
    }

    "convert GMT to BST during British Summer Time" in {
      // 19 Jun 2023 15:30 UTC = 16:30 BST
      val dateTime = ZonedDateTime.of(2023, 6, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val result = DateUtils.formatWithDaySuffixAndRelative(dateTime)

      result should include("4:30pm")
      result should not include "04:30pm"
    }

    "keep GMT during winter" in {
      // 19 Jan 2026 15:30 UTC = 15:30 GMT
      val dateTime = ZonedDateTime.of(2026, 1, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val result = DateUtils.formatWithDaySuffixAndRelative(dateTime)

      result should include("3:30pm")
      result should not include "03:30pm"
    }

  }

  "ExcelUtils covertToLocalDateOrString" should {
    "correctly parse yyyy-MM-dd Date" in {
      val date = DateUtils.convertToLocalDateOrString("2023-10-01")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-10-01"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse yyyy/MM/dd Date" in {
      val date = DateUtils.convertToLocalDateOrString("2023/10/01")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-10-01"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse 2021-02-03T10:33:30.414 Date" in {
      val date = DateUtils.convertToLocalDateOrString("2021-02-03T10:33:30.414")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2021-02-03"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse 2023-06-19 16:30:00 Date" in {
      val date = DateUtils.convertToLocalDateOrString("2023-06-19 16:30:00")
      date match {
        case localDate: LocalDate => localDate.toString shouldBe "2023-06-19"
        case _                    => fail("Date parsing failed")
      }
    }

    "correctly parse unknown date format dd-mm-yyyy to String" in {
      val date = DateUtils.convertToLocalDateOrString("12-10-2021")
      date match {
        case unknownDate: String => unknownDate shouldBe "12-10-2021"
        case _                   => fail("Date parsing failed")
      }
    }
  }
}
