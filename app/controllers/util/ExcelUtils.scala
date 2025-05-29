package controllers.util

import controllers.util.DateUtils.convertToLocalDateOrString
import controllers.util.GuidanceUtils.{ALL_COLUMNS, GuidanceColumnWriter, approximatedRowHeight}
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import org.apache.commons.io.output.ByteArrayOutputStream
import org.dhatim.fastexcel.{BorderSide, BorderStyle, Workbook, Worksheet}
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils.DownloadFileDisplayProperty
import uk.gov.nationalarchives.tdr.validation.utils.GuidanceUtils.GuidanceItem

import java.time.LocalDate

object ExcelUtils {

  val NonEditableColour: String = "CCCCCC"

  def createExcelFile(
      consignmentRef: String,
      fileMetadata: getConsignmentFilesMetadata.GetConsignment,
      downloadFileDisplayProperties: List[DownloadFileDisplayProperty],
      keyToTdrFileHeader: String => String,
      keyToTdrDataLoadHeader: String => String,
      keyToPropertyType: String => String,
      sortColumn: String,
      guidanceItems: Seq[GuidanceItem] = Seq.empty
  ): Array[Byte] = {
    val colProperties: List[ColumnProperty] =
      downloadFileDisplayProperties.map(displayProperty => ColumnProperty(keyToTdrFileHeader(displayProperty.key), displayProperty.editable, Some(NonEditableColour)))
    val dataTypes: List[String] = downloadFileDisplayProperties.map(dp => keyToPropertyType(dp.key))
    val sortedMetaData = fileMetadata.files.sortBy(_.fileMetadata.find(_.name == sortColumn).map(_.value.toUpperCase))
    val fileMetadataRows: List[List[Any]] = createExcelRowData(sortedMetaData, downloadFileDisplayProperties, keyToPropertyType, keyToTdrDataLoadHeader)
    ExcelUtils.writeExcel(
      s"Metadata for $consignmentRef",
      colProperties,
      colProperties.map(x => x.header) :: fileMetadataRows,
      dataTypes,
      guidanceItems,
      keyToTdrFileHeader,
      keyToPropertyType
    )
  }

  def writeExcel(
      worksheetName: String,
      columnProperties: List[ColumnProperty] = List.empty,
      rows: List[List[Any]],
      dataTypes: List[String] = Nil,
      guidanceItems: Seq[GuidanceItem] = Seq.empty,
      keyToTdrFileHeader: String => String = identity,
      keyToPropertyType: String => String = identity
  ): Array[Byte] = {
    val xlBas = new ByteArrayOutputStream()
    val wb = new Workbook(xlBas, "TNA - Transfer Digital Records", "1.0")
    val ws: Worksheet = wb.newWorksheet(worksheetName)

    rows.head.zipWithIndex.foreach { case (header, col) =>
      ws.value(0, col, header.toString)
    }

    for ((row, rowNo) <- rows.tail.zipWithIndex) {
      for ((col, colNo) <- row.zipWithIndex) {
        setCellValue(ws, col, rowNo + 1, colNo)
      }
    }

    columnProperties.zipWithIndex.foreach { case (colProperty, colNo) =>
      if (!colProperty.editable && colProperty.fillColour.nonEmpty) {
        ws.range(0, colNo, rows.length - 1, colNo).style().fillColor(colProperty.fillColour.get).set()
      }
    }

    ws.range(0, 0, 0, rows.head.length - 1).style().bold().set()

    for ((dataType, colNo) <- dataTypes.zipWithIndex) {
      dataType match {
        case "date" => ws.range(1, colNo, rows.tail.length, colNo).style.format("yyyy-MM-dd").set()
        case _      =>
      }
    }

    if (guidanceItems.nonEmpty) {
      buildGuidanceWorksheet(wb, guidanceItems, keyToTdrFileHeader, keyToPropertyType)
    }

    wb.finish()

    xlBas.toByteArray

  }

  private def buildGuidanceWorksheet(
      wb: Workbook,
      guidanceItems: Seq[GuidanceItem],
      keyToTdrFileHeader: String => String,
      keyToPropertyType: String => String
  ): Unit = {
    val guidanceWorksheet: Worksheet = wb.newWorksheet("Quick guide")
    guidanceWorksheet.freezePane(0, 1)
    ALL_COLUMNS.zipWithIndex.foreach { case (colType, columnNumber) =>
      formatHeaderAndSetColumnWidth(guidanceWorksheet, colType, columnNumber)
      guidanceItems.zipWithIndex.foreach { case (gi, idx) =>
        guidanceWorksheet.rowHeight(idx + 1, approximatedRowHeight(gi, keyToTdrFileHeader, keyToPropertyType))
        writeColumnCell(keyToTdrFileHeader, keyToPropertyType, guidanceWorksheet, colType, columnNumber, gi, idx + 1)
      }
    }
  }

  private def writeColumnCell(
      keyToTdrFileHeader: String => String,
      keyToPropertyType: String => String,
      guidanceWorksheet: Worksheet,
      colType: GuidanceColumnWriter,
      columnNumber: Int,
      gi: GuidanceItem,
      rowNumber: Int
  ): Unit = {
    setCellValue(
      worksheet = guidanceWorksheet,
      value = colType.value(keyToTdrFileHeader, keyToPropertyType)(gi),
      rowNo = rowNumber,
      colNo = columnNumber
    )
    val styleSetter = guidanceWorksheet.range(rowNumber, columnNumber, rowNumber, columnNumber).style()
    colType.systemPropertyFormatting(gi)(styleSetter)
    colType.columnLevelFormatting(styleSetter)
    colType.formatDataType(keyToPropertyType, gi)(styleSetter)
  }

  private def formatHeaderAndSetColumnWidth(guidanceWorksheet: Worksheet, colType: GuidanceColumnWriter, columnNumber: Int): Unit = {
    guidanceWorksheet.value(0, columnNumber, colType.header)
    guidanceWorksheet.range(0, columnNumber, 0, columnNumber).style().bold().borderStyle(BorderSide.BOTTOM, BorderStyle.THIN).set()
    colType.maxColumnWidth.foreach(width => guidanceWorksheet.width(columnNumber, width))
  }

  private def setCellValue(worksheet: Worksheet, value: Any, rowNo: Int, colNo: Int): Unit = {
    value match {
      case value: LocalDate => worksheet.value(rowNo, colNo, value)
      case value: Integer   => worksheet.value(rowNo, colNo, value)
      case value            => worksheet.value(rowNo, colNo, value.toString)
    }
  }

  private def createExcelRowData(
      sortedMetaData: List[Files],
      downloadProperties: List[DownloadFileDisplayProperty],
      keyToPropertyType: String => String,
      keyToTdrDataLoadHeader: String => String
  ): List[List[Any]] = {
    sortedMetaData.map { file =>
      {
        val groupedMetadata: Map[String, String] = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
        downloadProperties.map(x => x.key).map { colOrderSchemaPropertyName =>
          groupedMetadata
            .get(keyToTdrDataLoadHeader(colOrderSchemaPropertyName))
            .map { fileMetadataValue =>
              convertValue(keyToPropertyType(colOrderSchemaPropertyName), fileMetadataValue)
            }
            .getOrElse("")
        }
      }
    }
  }

  def convertValue(propertyType: String, fileMetadataValue: String, convertBoolean: Boolean = true): Any = {
    propertyType match {
      case "date"                      => convertToLocalDateOrString(fileMetadataValue)
      case "boolean" if convertBoolean => if (fileMetadataValue == "true") "Yes" else "No"
      case "integer"                   => Integer.valueOf(fileMetadataValue)
      case _                           => fileMetadataValue
    }
  }

  case class ColumnProperty(header: String, editable: Boolean, fillColour: Option[String])
}
