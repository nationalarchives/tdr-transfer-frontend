package controllers.util

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import org.apache.commons.io.output.ByteArrayOutputStream

import java.util.UUID

object ExcelUtils {

  def writeExcel(consignmentID: UUID, rows: List[List[String]]): Array[Byte] = {
    val xlBas = new ByteArrayOutputStream()
    val wb = new Workbook(xlBas, "MetadataDownload", "1.0")
    val ws: Worksheet = wb.newWorksheet(s"Metadata for ${consignmentID.toString}")

    for ((row, rowNo) <- rows.zipWithIndex) {
      for ((col, colNo) <- row.zipWithIndex) {
        ws.value(rowNo, colNo, col)
      }
    }
    ws.range(0,0,0,100).style().bold().set()

    wb.finish()

    xlBas.toByteArray

  }

}
