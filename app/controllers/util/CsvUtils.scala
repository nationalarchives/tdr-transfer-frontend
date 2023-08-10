package controllers.util

import com.github.tototoshi.csv.{CSVReader, CSVWriter}
import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.File

object CsvUtils {

  def writeCsv(rows: List[List[String]]): String = {
    val bas = new ByteArrayOutputStream()
    val writer = CSVWriter.open(bas)
    writer.writeAll(rows)
    bas.toString("UTF-8")
  }

  def readCsv(file: File): List[Map[String, String]] = {
    val reader = CSVReader.open(file)
    reader.allWithHeaders()
  }
}
