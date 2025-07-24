package controllers.util

object NaturalStringOrdering extends Ordering[String] {
  def compare(a: String, b: String): Int = {
    val aChars = a.toCharArray
    val bChars = b.toCharArray
    var i = 0
    var j = 0

    while (i < aChars.length && j < bChars.length) {
      // Handle space priority
      if (aChars(i) == ' ' && bChars(j) != ' ') return -1
      if (bChars(j) == ' ' && aChars(i) != ' ') return 1

      // Handle underscore priority
      if (aChars(i) == '_' && bChars(j) != '_' && aChars(i) != ' ') return -1
      if (bChars(j) == '_' && aChars(i) != '_' && bChars(j) != ' ') return 1

      // Handle numeric sequences
      if (aChars(i).isDigit && bChars(j).isDigit) {
        // Skip leading zeros
        var ai = i
        var bi = j
        while (ai < aChars.length && aChars(ai) == '0') ai += 1
        while (bi < bChars.length && bChars(bi) == '0') bi += 1

        // Find the end of the numeric sequence
        var aNumEnd = ai
        var bNumEnd = bi
        while (aNumEnd < aChars.length && aChars(aNumEnd).isDigit) aNumEnd += 1
        while (bNumEnd < bChars.length && bChars(bNumEnd).isDigit) bNumEnd += 1

        // Compare by length first (longer numeric sequence is larger)
        val aNumLen = aNumEnd - ai
        val bNumLen = bNumEnd - bi
        if (aNumLen != bNumLen) return aNumLen - bNumLen

        // If same length, compare digit by digit
        while (ai < aNumEnd && bi < bNumEnd) {
          if (aChars(ai) != bChars(bi)) return aChars(ai) - bChars(bi)
          ai += 1
          bi += 1
        }

        // If numeric sequences are equal, compare leading zeros
        if (i != ai || j != bi) return (ai - i) - (bi - j)

        i = aNumEnd - 1
        j = bNumEnd - 1
      } else {
        // Normal character comparison
        if (aChars(i) != bChars(j)) return aChars(i) - bChars(j)
      }

      i += 1
      j += 1
    }

    // Handle case when one string is prefix of another
    a.length - b.length
  }
}
