package controllers.util

import org.dhatim.fastexcel.{Workbook, Worksheet}
import org.apache.commons.io.output.ByteArrayOutputStream
import graphql.codegen.types.DataType
import java.time.LocalDate

object ExcelUtils {

  def writeExcel(consignmentRef: String, rows: List[List[Any]], dataTypes: List[DataType]): Array[Byte] = {
    val xlBas = new ByteArrayOutputStream()
    val wb = new Workbook(xlBas, "TNA - Transfer Digital Records", "1.0")
    val ws: Worksheet = wb.newWorksheet(s"Metadata for ${consignmentRef}")

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

    ws.range(0, 0, 0, rows.head.length).style().bold().set()

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
