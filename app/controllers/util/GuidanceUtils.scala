package controllers.util

import controllers.util.ExcelUtils.convertValue
import org.dhatim.fastexcel.StyleSetter
import uk.gov.nationalarchives.tdr.validation.utils.GuidanceUtils.GuidanceItem

object GuidanceUtils {
  private val TYPICAL_CHARACTER_HEIGHT = 15.00
  private val DEFAULT_FONT_COLOR = "000000"
  private val SYSTEM_ROW_FONT_COLOR = "A1A1A1"
  
  def approximatedRowHeight(
    guidanceItem: GuidanceItem, 
    keyToTdrHeader: String => String, 
    keyToPropertyType: String => String
  ): Double = {
    val maxRequiredHeight = ALL_COLUMNS.map { colType =>
      colType.maxColumnWidth.map { columnWidth =>
        val cellValue = colType.value(keyToTdrHeader, keyToPropertyType)(guidanceItem).toString
        Math.ceil(cellValue.length.toDouble / columnWidth) * TYPICAL_CHARACTER_HEIGHT
      }.getOrElse(1.0)
    }.max
    maxRequiredHeight
  }

  sealed trait GuidanceColumnWriter {
    val header: String
    val maxColumnWidth: Option[Double] = None
    def columnLevelFormatting: StyleSetter => Unit = _ => ()
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => Any
    def systemPropertyFormatting(guidanceItem: GuidanceItem): StyleSetter => Unit = ss => 
        if (guidanceItem.format == "Do not modify") 
          ss.fontColor(SYSTEM_ROW_FONT_COLOR).set() 
        else ss.fontColor(DEFAULT_FONT_COLOR).set()
    def formatDataType(keyToPropertyType: String => String, guidanceItem: GuidanceItem): StyleSetter => Unit = _ => ()
  }

  val ALL_COLUMNS: Seq[GuidanceColumnWriter] = Seq(ColumnTitle, Details, Format, TDRRequirement, Example)

  case object ColumnTitle extends GuidanceColumnWriter {
    val header = "COLUMN TITLE"
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => String = gi => keyToTdrHeader(gi.property)
    override def columnLevelFormatting: StyleSetter => Unit = _.bold.set()
  }

  case object Details extends GuidanceColumnWriter {
    val header = "DETAILS"
    override val maxColumnWidth: Option[Double] = Some(60.67)
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => String = _.details
    override def columnLevelFormatting: StyleSetter => Unit = _.wrapText(true).set()
  }

  case object Format extends GuidanceColumnWriter {
    val header = "FORMAT"
    override val maxColumnWidth: Option[Double] = Some(37.00)
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => String = _.format
    override def columnLevelFormatting: StyleSetter => Unit = _.wrapText(true).set()
  }

  case object TDRRequirement extends GuidanceColumnWriter {
    val header = "TDR REQUIREMENT"
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => String = _.tdrRequirement
  }

  case object Example extends GuidanceColumnWriter {
    val header = "EXAMPLE"
    def value(keyToTdrHeader: String => String, keyToPropertyType: String => String): GuidanceItem => Any =
      gi => convertValue(keyToPropertyType(gi.property), gi.example, convertBoolean = false)
    override def formatDataType(keyToPropertyType: String => String, guidanceItem: GuidanceItem): StyleSetter => Unit = { ss =>
      if (keyToPropertyType(guidanceItem.property) == "date") ss.format("yyyy-MM-dd").horizontalAlignment("left").set()
      else if (keyToPropertyType(guidanceItem.property) == "integer") ss.format("0").horizontalAlignment("left").set()
      else ()
    }
  }
}
