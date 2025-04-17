package controllers.util

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId, ZonedDateTime}

object DateUtils {

  def format(dateTime: ZonedDateTime, format: String = "dd/MM/yyyy HH:mm:ss"): String = {
    val formatter = DateTimeFormatter.ofPattern(format)
    dateTime.withZoneSameInstant(ZoneId.of("Europe/London")).format(formatter)
  }

  def covertToLocalDateOrString(date: String): Any = {
    val regex = """\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?""".r
    val slashRegex = """\d{4}/\d{2}/\d{2}""".r
    val hyphenRegex = """\d{4}-\d{2}-\d{2}""".r
    val parseFormatterTimeStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
    date match {
      case regex(_*) =>
        LocalDateTime.parse(date, parseFormatterTimeStamp).toLocalDate
      case slashRegex(_*) =>
        LocalDate.parse(date.replaceAll("/", "-"))
      case hyphenRegex(_*) =>
        LocalDate.parse(date)
      case _ => date
    }
  }

}
