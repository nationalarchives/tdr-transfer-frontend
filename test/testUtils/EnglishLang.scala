package testUtils

import play.api.i18n.{Lang, Langs}

class EnglishLang extends Langs {
  def availables: Seq[Lang] = Seq(Lang("en-gb"))

}
