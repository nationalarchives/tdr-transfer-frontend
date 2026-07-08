package controllers.util

import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import scala.io.Source
import scala.jdk.CollectionConverters._

class ExcelUtilsSpec extends AnyWordSpec with Matchers {

  private def sheetXml(excelBytes: Array[Byte]): String = {
    val tempFile = java.io.File.createTempFile("test-excel", ".xlsx")
    try {
      Files.write(tempFile.toPath, excelBytes)
      val zipFile = new ZipFile(tempFile)
      try {
        val entry = zipFile.getEntry("xl/worksheets/sheet1.xml")
        if (entry != null) new String(zipFile.getInputStream(entry).readAllBytes(), "UTF-8") else ""
      } finally zipFile.close()
    } finally tempFile.delete()
  }

  "writeExcel" should {
    "hide the specified columns" in {
      val rows = List(List("A", "B", "C"), List("1", "2", "3"))

      val excelBytes = ExcelUtils.writeExcel("Sheet1", rows, Set(0, 1))

      val xml = sheetXml(excelBytes)
      xml should include("hidden=\"true\"")
    }

    "not hide any columns when hiddenColumns is empty" in {
      val rows = List(List("A", "B", "C"), List("1", "2", "3"))

      val excelBytes = ExcelUtils.writeExcel("Sheet1", rows, Set.empty[Int])

      val xml = sheetXml(excelBytes)
      xml should not include "hidden=\"true\""
    }

    "hide only the specified columns and not others" in {
      val rows = List(List("A", "B", "C"), List("1", "2", "3"))

      val excelBytes = ExcelUtils.writeExcel("Sheet1", rows, Set(0))

      val xml = sheetXml(excelBytes)
      // Column 1 (min/max="1" in 1-based Excel XML) should be hidden
      xml should include("hidden=\"true\"")
      // The content of non-hidden columns should still be present
      val wb = new ReadableWorkbook(new ByteArrayInputStream(excelBytes))
      val headerRow = wb.getFirstSheet.read().asScala.head
      headerRow.getCellAsString(1).orElse("") shouldBe "B"
      headerRow.getCellAsString(2).orElse("") shouldBe "C"
      wb.close()
    }
  }

  "convertCsvToExcel" should {
    "convert a CSV source to an Excel file with the correct content and bold header row" in {
      val csvContent = "Name,Age,City\nAlice,30,London\nBob,25,Edinburgh"
      val source = Source.fromString(csvContent)

      val excelBytes = ExcelUtils.convertCsvToExcel(source)

      val wb = new ReadableWorkbook(new ByteArrayInputStream(excelBytes))
      val sheet = wb.getFirstSheet
      val rows = sheet.read().asScala.toList

      rows.length shouldBe 3

      rows.head.getCellAsString(0).orElse("") shouldBe "Name"
      rows.head.getCellAsString(1).orElse("") shouldBe "Age"
      rows.head.getCellAsString(2).orElse("") shouldBe "City"

      rows(1).getCellAsString(0).orElse("") shouldBe "Alice"
      rows(1).getCellAsString(1).orElse("") shouldBe "30"
      rows(1).getCellAsString(2).orElse("") shouldBe "London"

      rows(2).getCellAsString(0).orElse("") shouldBe "Bob"
      rows(2).getCellAsString(1).orElse("") shouldBe "25"
      rows(2).getCellAsString(2).orElse("") shouldBe "Edinburgh"

      wb.close()
    }

    "use the provided worksheet name" in {
      val csvContent = "Header1\nValue1"
      val source = Source.fromString(csvContent)

      val excelBytes = ExcelUtils.convertCsvToExcel(source, "Custom Sheet")

      val wb = new ReadableWorkbook(new ByteArrayInputStream(excelBytes))
      wb.getFirstSheet.getName shouldBe "Custom Sheet"
      wb.close()
    }

    "default the worksheet name to Sheet1" in {
      val csvContent = "Header1\nValue1"
      val source = Source.fromString(csvContent)

      val excelBytes = ExcelUtils.convertCsvToExcel(source)

      val wb = new ReadableWorkbook(new ByteArrayInputStream(excelBytes))
      wb.getFirstSheet.getName shouldBe "Sheet1"
      wb.close()
    }

    "handle CSV fields containing commas within quotes" in {
      val csvContent = "Name,Address\nAlice,\"123 High Street, London\""
      val source = Source.fromString(csvContent)

      val excelBytes = ExcelUtils.convertCsvToExcel(source)

      val wb = new ReadableWorkbook(new ByteArrayInputStream(excelBytes))
      val rows = wb.getFirstSheet.read().asScala.toList

      rows(1).getCellAsString(1).orElse("") shouldBe "123 High Street, London"

      wb.close()
    }
  }
}
