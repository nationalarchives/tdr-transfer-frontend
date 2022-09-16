package controllers.util

import java.text.Normalizer
import scala.util.matching.Regex

object NaturalSorting {
  implicit object ArrayOrdering extends Ordering[Array[String]] { // 4
    val INT: Regex = "([0-9]+)".r
    def compare(a: Array[String], b: Array[String]) : Int = {
      val l = Math.min(a.length, b.length)
      (0 until l).segmentLength(i => a(i) equals b(i)) match {
        case i if i == l => Math.signum(a.length - b.length).toInt
        case i => (a(i), b(i)) match {
          case (INT(c), INT(d)) => Math.signum(c.toInt - d.toInt).toInt
          case (c, d) => c compareTo d
        }
      }
    }
  }

  def natural(str: String): Array[String] = {
    val replacements = Map('\u00df' -> "ss", '\u017f' -> "s", '\u0292' -> "s").withDefault(s => s.toString)
    Normalizer.normalize(Normalizer.normalize(
      str.trim.toLowerCase,
      Normalizer.Form.NFKC),
      Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}", "")
      .replaceAll("^(the|a|an) ", "")
      .flatMap(replacements.apply)
      .split(s"\\s+|(?=[0-9])(?<=[^0-9])|(?=[^0-9])(?<=[0-9])")
  }
}
