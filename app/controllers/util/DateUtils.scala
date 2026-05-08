package controllers.util

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId, ZonedDateTime}

object DateUtils {

  val ukTimeZone: ZoneId = ZoneId.of("Europe/London")

  def format(dateTime: ZonedDateTime, format: String = "dd/MM/yyyy HH:mm:ss", zoneId: ZoneId = ukTimeZone): String = {
    val formatter = DateTimeFormatter.ofPattern(format)
    dateTime.withZoneSameInstant(zoneId).format(formatter)
  }

  def formatWithDaySuffixAndRelative(dateTime: ZonedDateTime, zoneId: ZoneId = ukTimeZone): String = {
    val localDateTime = dateTime.withZoneSameInstant(zoneId)
    val daySuffix = getDaySuffix(localDateTime.getDayOfMonth)
    val formatted = format(dateTime, s"d'$daySuffix' MMMM yyyy, h:mma", zoneId)
    // Lowercase only the trailing AM/PM so the month stays capitalised, e.g. "20th April 2026, 10:32am"
    val date =
      if (formatted.endsWith("AM") || formatted.endsWith("PM")) formatted.dropRight(2) + formatted.takeRight(2).toLowerCase
      else formatted
    val noOfDays = LocalDate.now().toEpochDay - localDateTime.toLocalDate.toEpochDay
    val days = noOfDays match {
      case 0   => "Today"
      case 1   => "Yesterday"
      case day => s"$day days ago"
    }
    s"$date ($days)"
  }

  def getDaySuffix(day: Int): String = day match {
    case 1 | 21 | 31 => "st"
    case 2 | 22      => "nd"
    case 3 | 23      => "rd"
    case _           => "th"
  }

  def convertToLocalDateOrString(date: String): Any = {
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
