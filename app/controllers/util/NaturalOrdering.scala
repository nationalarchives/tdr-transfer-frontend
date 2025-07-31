package controllers.util

object NaturalOrdering extends Ordering[String] {
  def compare(a: String, b: String): Int = {
    // Extract base names (without extension) for comparison
    val (aBase, _) = splitFilename(a)
    val (bBase, _) = splitFilename(b)

    compareBaseNames(aBase, bBase)
  }

  private def splitFilename(filename: String): (String, String) = {
    val lastDot = filename.lastIndexOf('.')
    if (lastDot >= 0) {
      (filename.substring(0, lastDot), filename.substring(lastDot))
    } else {
      (filename, "")
    }
  }

  private def compareBaseNames(a: String, b: String): Int = {
    val aChars = a.toCharArray
    val bChars = b.toCharArray
    var i = 0
    var j = 0

    while (i < aChars.length && j < bChars.length) {
      if (aChars(i) == ' ' && bChars(j) != ' ') return -1
      if (bChars(j) == ' ' && aChars(i) != ' ') return 1
      val aDoubleUnderscore = i + 1 < aChars.length && aChars(i) == '_' && aChars(i + 1) == '_'
      val bDoubleUnderscore = j + 1 < bChars.length && bChars(j) == '_' && bChars(j + 1) == '_'

      if (aDoubleUnderscore && !bDoubleUnderscore) return -1
      if (bDoubleUnderscore && !aDoubleUnderscore) return 1

      if (aChars(i) == '_' && bChars(j) != '_' && !bDoubleUnderscore) return -1
      if (bChars(j) == '_' && aChars(i) != '_' && !aDoubleUnderscore) return 1

      if (aDoubleUnderscore && bDoubleUnderscore) {
        i += 1
        j += 1
      }

      if (aChars(i).isDigit && bChars(j).isDigit) {
        var numA = 0
        var numB = 0
        while (i < aChars.length && aChars(i).isDigit) {
          numA = numA * 10 + (aChars(i) - '0')
          i += 1
        }
        while (j < bChars.length && bChars(j).isDigit) {
          numB = numB * 10 + (bChars(j) - '0')
          j += 1
        }

        if (numA != numB) return numA - numB
        i -= 1
        j -= 1
      } else {
        if (aChars(i) != bChars(j)) return aChars(i) - bChars(j)
      }

      i += 1
      j += 1
    }
    a.length - b.length
  }
}
