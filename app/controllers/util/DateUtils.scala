package controllers.util

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object DateUtils {

  def format(dateTime: ZonedDateTime, format: String = "dd/MM/yyyy HH:mm:ss"): String = {
    val formatter = DateTimeFormatter.ofPattern(format)
    dateTime.withZoneSameInstant(ZoneId.of("Europe/London")).format(formatter)
  }
}
