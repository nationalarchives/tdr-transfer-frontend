package controllers.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExcelBasedOrderingSpec extends AnyWordSpec with Matchers {

  "ExcelBasedOrdering" should {
    "sort string as per the excel based sorting" in {
      val testStrings = List(
        "test/test_1_1.txt",
        "test/test1$.txt",
        "test/test__1.txt",
        "test/test20.txt",
        "test/test_1hello.txt",
        "test/test10.txt",
        "test/testfile.txt",
        "test/test_2_32.txt",
        "test/test2.txt",
        "test/test1.txt",
        "test/test 1.txt",
        "test/test_2.txt",
        "test/test__file.txt",
        "test/test_file.txt"
      )
      val expectedOrder = List(
        "test/test 1.txt",
        "test/test__1.txt",
        "test/test__file.txt",
        "test/test_1_1.txt",
        "test/test_1hello.txt",
        "test/test_2.txt",
        "test/test_2_32.txt",
        "test/test_file.txt",
        "test/test1$.txt",
        "test/test1.txt",
        "test/test10.txt",
        "test/test2.txt",
        "test/test20.txt",
        "test/testfile.txt"
      )

      testStrings.sorted(ExcelBasedOrdering) shouldBe expectedOrder
    }
  }
}
