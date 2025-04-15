package controllers.util

import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import org.apache.commons.io.output.ByteArrayOutputStream
import org.dhatim.fastexcel.{Workbook, Worksheet}
import uk.gov.nationalarchives.tdr.validation.utils.ConfigUtils

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

object ExcelUtils {

  def createExcelFile(
      consignmentRef: String,
      fileMetadata: getConsignmentFilesMetadata.GetConsignment,
      downloadProperties: List[String],
      tdrFileHeader: String => String,
      propertyType: String => String,
      sortColumn: String
  ): Array[Byte] = {
    val header = downloadProperties.map(colOrderSchemaPropertyName => tdrFileHeader(colOrderSchemaPropertyName))
    val dataTypes: List[String] = downloadProperties.map(propertyType)
    val sortedMetaData = fileMetadata.files.sortBy(_.fileMetadata.find(_.name == sortColumn).map(_.value.toUpperCase))
    val fileMetadataRows: List[List[Any]] = createExcelRowData(sortedMetaData, downloadProperties)

    ExcelUtils.writeExcel(s"Metadata for $consignmentRef", header :: fileMetadataRows, dataTypes)
  }

  def writeExcel(worksheetName: String, rows: List[List[Any]], dataTypes: List[String] = Nil): Array[Byte] = {
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

  private def createExcelRowData(sortedMetaData: List[Files], downloadProperties: List[String]): List[List[Any]] = {
    val metadataConfiguration = ConfigUtils.loadConfiguration
    val tdrDataLoadHeader = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")

    sortedMetaData.map { file =>
      {
        val groupedMetadata: Map[String, String] = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
        downloadProperties.map { colOrderSchemaPropertyName =>
          groupedMetadata
            .get(tdrDataLoadHeader(colOrderSchemaPropertyName))
            .map { fileMetadataValue =>
              metadataConfiguration.getPropertyType(colOrderSchemaPropertyName) match {
                case "date"    => extractDate(fileMetadataValue)
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

  private def extractDate(date: String): Any = {
    val regex = """\d{4}([-]|[.])\d{2}\1\d{2}(T\d{2}:\d{2}:\d{2}([.]\d{1,3})?)?""".r
    val slashRegex = """\d{4}/\d{2}/\d{2}""".r
    val parseFormatterTimeStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
    date match {
      case regex(_*) =>
        LocalDateTime.parse(date, parseFormatterTimeStamp).toLocalDate
      case slashRegex(_*) =>
        LocalDateTime.parse(date.replaceAll("/", "-") + "T00:00:00", parseFormatterTimeStamp).toLocalDate
      case _ => date
    }
  }

}
