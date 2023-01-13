package controllers.util

import com.github.tototoshi.csv.CSVWriter
import org.apache.commons.io.output.ByteArrayOutputStream

object CsvUtils {

  def writeCsv(rows: List[List[String]]): String = {
    val bas = new ByteArrayOutputStream()
    val writer = CSVWriter.open(bas)
    writer.writeAll(rows)
    bas.toString("UTF-8")
  }
}
