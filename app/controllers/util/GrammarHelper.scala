package controllers.util

object GrammarHelper {
  def generateCorrectIndefiniteArticle(word: String): String = {
    val vowels = Set("a", "e", "i", "o", "u", "x") // x because it's usually pronounced "ex"
    if (vowels.contains(word.head.toLower.toString)) s"an $word" else s"a $word"
  }
}
