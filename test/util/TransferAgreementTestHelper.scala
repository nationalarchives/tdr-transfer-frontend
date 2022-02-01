package util

import org.scalatest.Matchers._
import org.scalatest.MustMatchers.convertToAnyMustWrapper
import play.api.i18n.Langs

class TransferAgreementTestHelper {

  val langs: Langs = new EnglishLang

  val nonComplianceOptions = Map(
    "publicRecord" -> "I confirm that the records are Public Records.",
    "crownCopyright" -> "I confirm that the records are all Crown Copyright.",
    "english" -> "I confirm that the records are all in English."
  )

  val complianceOptions = Map(
    "droAppraisalSelection" -> "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection",
    "droSensitivity" -> "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review.",
    "openRecords" -> "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records."
  )

  val checkHtmlOfNonComplianceFormOptions = new CheckHtmlOfFormOptions(nonComplianceOptions)
  val checkHtmlOfComplianceFormOptions = new CheckHtmlOfFormOptions(complianceOptions)

  val notCompliance = "notCompliance"
  val compliance = "compliance"

}
