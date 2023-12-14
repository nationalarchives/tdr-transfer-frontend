package controllers.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.{ZoneId, ZonedDateTime}

class DateUtilsSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  "DateUtils" should {
    "format ZonedDateTime to a string with the default format of yyyy-MM-dd HH:mm:ss" in {
      val dateTime = ZonedDateTime.of(2023, 6, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val expected = "19/06/2023 16:30:00"

      val result = DateUtils.format(dateTime)

      result should equal(expected)
    }

    "format ZonedDateTime to a string with a custom format of yyyy-MM-dd HH:mm" in {
      val dateTime = ZonedDateTime.of(2023, 6, 19, 15, 30, 0, 0, ZoneId.of("UTC"))
      val customFormat = "yyyy-MM-dd HH:mm"
      val expected = "2023-06-19 16:30"

      val result = DateUtils.format(dateTime, customFormat)

      result should be(expected)
    }
  }
}
