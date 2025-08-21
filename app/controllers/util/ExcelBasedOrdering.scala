package controllers.util

object ExcelBasedOrdering extends Ordering[String] {
  // Define the priority order for characters
  private val priorityOrder: Map[Char, Int] = {
    val space = Set(' ')
    val specialChars = Set((32 to 47).map(_.toChar): _*) ++
      Set((58 to 64).map(_.toChar): _*) ++
      Set((91 to 96).map(_.toChar): _*) ++
      Set((123 to 126).map(_.toChar): _*)
    val alphanumeric = (('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z')).toSet

    List(
      (space, 0),
      (specialChars, 1),
      (alphanumeric, 2)
    ).flatMap { case (chars, priority) =>
      chars.map(c => c -> priority)
    }.toMap
  }

  def compare(a: String, b: String): Int = {
    val minLength = Math.min(a.length, b.length)

    for (i <- 0 until minLength) {
      val charA = a(i)
      val charB = b(i)

      val priorityA = priorityOrder.getOrElse(charA, Int.MaxValue)
      val priorityB = priorityOrder.getOrElse(charB, Int.MaxValue)

      if (priorityA != priorityB) {
        return priorityA - priorityB
      } else if (charA != charB) {
        return charA.compareTo(charB)
      }
    }

    // If all compared characters are equal, shorter string comes first
    a.length - b.length
  }
}
