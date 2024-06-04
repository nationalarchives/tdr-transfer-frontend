package controllers.util

import org.dhatim.fastexcel.{Workbook, Worksheet}
import org.apache.commons.io.output.ByteArrayOutputStream
import graphql.codegen.types.DataType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object ExcelUtils {

  def writeExcel(consignmentID: UUID, rows: List[List[String]], dataTypes: List[DataType]): Array[Byte] = {
    val xlBas = new ByteArrayOutputStream()
    val wb = new Workbook(xlBas, "MetadataDownload", "1.0")
    val ws: Worksheet = wb.newWorksheet(s"Metadata for ${consignmentID.toString}")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    rows.head.zipWithIndex.foreach { case (header, col) =>
      ws.value(0, col, header)
    }

    for ((row, rowNo) <- rows.tail.zipWithIndex) {
      for ((col, colNo) <- row.zipWithIndex) {
        col match {
          case a if !a.equals("") && dataTypes(colNo) == DataType.DateTime => ws.value(rowNo + 1, colNo, LocalDate.parse(col, formatter))
          case a if !a.equals("") && dataTypes(colNo) == DataType.Integer  => ws.value(rowNo + 1, colNo, Integer.valueOf(col))
          case _                                                           => ws.value(rowNo + 1, colNo, col)
        }
      }
    }

    ws.range(0, 0, 0, 100).style().bold().set()

    for ((dataType, colNo) <- dataTypes.zipWithIndex) {
      dataType match {
        case DataType.DateTime => ws.range(1, colNo, rows.tail.length, colNo).style.format("yyyy-MM-dd").set()
        case _                 =>
      }
    }

    wb.finish()

    xlBas.toByteArray

  }

}
