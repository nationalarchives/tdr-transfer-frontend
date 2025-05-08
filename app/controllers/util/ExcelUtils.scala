package controllers.util

import controllers.util.DateUtils.covertToLocalDateOrString
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import org.apache.commons.io.output.ByteArrayOutputStream
import org.dhatim.fastexcel.{Workbook, Worksheet}
import uk.gov.nationalarchives.tdr.validation.utils.ConfigUtils.DownloadFileDisplayProperty

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
      sortColumn: String
  ): Array[Byte] = {
    val colProperties: List[ColumnProperty] =
      downloadFileDisplayProperties.map(displayProperty => ColumnProperty(keyToTdrFileHeader(displayProperty.key), displayProperty.editable, Some(NonEditableColour)))
    val dataTypes: List[String] = downloadFileDisplayProperties.map(dp => keyToPropertyType(dp.key))
    val sortedMetaData = fileMetadata.files.sortBy(_.fileMetadata.find(_.name == sortColumn).map(_.value.toUpperCase))
    val fileMetadataRows: List[List[Any]] = createExcelRowData(sortedMetaData, downloadFileDisplayProperties, keyToPropertyType, keyToTdrDataLoadHeader)

    ExcelUtils.writeExcel(s"Metadata for $consignmentRef", colProperties, colProperties.map(x => x.header) :: fileMetadataRows, dataTypes)
  }

  def writeExcel(worksheetName: String, columnProperties: List[ColumnProperty] = List.empty, rows: List[List[Any]], dataTypes: List[String] = Nil): Array[Byte] = {
    val xlBas = new ByteArrayOutputStream()
    val wb = new Workbook(xlBas, "TNA - Transfer Digital Records", "1.0")
    val ws: Worksheet = wb.newWorksheet(worksheetName)

    rows.head.zipWithIndex.foreach { case (header, col) =>
      ws.value(0, col, header.toString)
    }

    for ((row, rowNo) <- rows.tail.zipWithIndex) {
      for ((col, colNo) <- row.zipWithIndex) {
        col match {
          case value: LocalDate => ws.value(rowNo + 1, colNo, value)
          case value: Integer   => ws.value(rowNo + 1, colNo, value)
          case value            => ws.value(rowNo + 1, colNo, value.toString)
        }
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

    wb.finish()

    xlBas.toByteArray

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
              keyToPropertyType(colOrderSchemaPropertyName) match {
                case "date"    => covertToLocalDateOrString(fileMetadataValue)
                case "boolean" => if (fileMetadataValue == "true") "Yes" else "No"
                case "integer" => Integer.valueOf(fileMetadataValue)
                case _         => fileMetadataValue
              }
            }
            .getOrElse("")
        }
      }
    }
  }

  case class ColumnProperty(header: String, editable: Boolean, fillColour: Option[String])

}
