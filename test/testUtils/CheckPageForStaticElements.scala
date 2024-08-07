package testUtils

import org.scalatest.matchers.must.Matchers._

class CheckPageForStaticElements() {
  def checkContentOfPagesThatUseMainScala(
      page: String,
      signedIn: Boolean = true,
      userType: String,
      consignmentExists: Boolean = true,
      transferStillInProgress: Boolean = true,
      pageRequiresAwsServices: Boolean = false
  ): Unit = {
    page must include("""<html lang="en" class="govuk-template">""")
    page must include("""
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <meta name="robots" content="noindex">
    |    <link rel="stylesheet" media="screen" href="/assets/stylesheets/main.css">
    |    <link rel="shortcut icon" type="image/ico" href="/assets/images/favicon.ico">
    |    <script  src="/assets/javascripts/all.js" type="text/javascript"></script>
    |    <script  src="/assets/javascripts/main.js" type="text/javascript"></script>""".stripMargin)
    page must include("""<a href="#main-content" class="govuk-skip-link" data-module="govuk-skip-link">Skip to main content</a>""")
    page must include("""href="/contact">""")
    page must include("""href="/cookies">""")
    page must include("""href="/accessibility-statement">""")
    page must include("All content is available under the")
    page must include(
      "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
    )
    page must include(", except where otherwise stated")
    page must include("© Crown copyright")
    if (signedIn) {
      checkContentOfSignedInPagesThatUseMainScala(page, userType, consignmentExists, transferStillInProgress, pageRequiresAwsServices)
    } else {
      page must include("This is a new service – your feedback will help us to improve it. Please")
      page must include("""href="/contact"> get in touch (opens in new tab).</a>""")
      page must not include "/faq"
      page must not include "Sign out"
    }
  }

  // scalastyle:off method.length
  private def checkContentOfSignedInPagesThatUseMainScala(
      page: String,
      userType: String,
      consignmentExists: Boolean,
      transferStillInProgress: Boolean,
      pageRequiresAwsServices: Boolean
  ) = {
    page must include(
      """<a class="govuk-header__link" href="/sign-out">
        |                                        Sign out
        |                                    </a>""".stripMargin
    )
    page must include("""    <dialog class="timeout-dialog">
                         |            <div>
                         |                <h2 class="govuk-heading-m">You have been inactive for more than 55 minutes.</h2>
                         |                <p class="govuk-body">If you do not respond within 5 minutes, you will be logged out to keep your information secure.</p>
                         |            </div>
                         |            <div class="govuk-button-group">
                         |                <button id="extend-timeout" class="govuk-button" role="button">
                         |                    Keep me signed in
                         |                </button>
                         |                <a class="govuk-link" href="/sign-out">Sign me out</a>
                         |            </div>
                         |        </dialog>""".stripMargin)
    if (userType == "judgment" || userType == "tna") {
      page must not include ("View transfers")
    }
    if (userType == "judgment") {
      checkHeaderOfSignedInPagesForFeedbackLink(page, survey = "5YDPSA")
      page must include("""href="/judgment/faq">""")
      page must include("""href="/judgment/help">""")
      if (consignmentExists) {
        page must include("TEST-TDR-2021-GB")

        if (transferStillInProgress) {
          page must include("Transfer reference")
          page must include("Problems with your transfer?")
          page must include("Email us and include the transfer reference:")
          page must include("""<a href="mailto:tdr@nationalarchives.gov.uk" class="govuk-link">tdr@nationalarchives.gov.uk</a>""")
        }
      }
    } else if (userType == "standard") {
      checkHeaderOfSignedInPagesForFeedbackLink(page)
      page must include("View transfers")
      page must include("""href="/faq">""")
      page must include("""href="/help">""")
      if (consignmentExists) {
        page must include("TEST-TDR-2021-GB")

        if (transferStillInProgress) {
          page must include("Consignment reference")
        }
      }
    }
    if (pageRequiresAwsServices) {
      page must include(
        """<input type="hidden" class="api-url" value="https://mock-api-url.com/graphql">
          |<input type="hidden" class="stage" value="mockStage">
          |<input type="hidden" class="region" value="mockRegion">
          |<input type="hidden" class="upload-url" value="https://mock-upload-url.com">""".stripMargin
      )
    }
  }

  private def checkHeaderOfSignedInPagesForFeedbackLink(page: String, survey: String = "tdr-feedback") = {
    page must include(
      s"""<span class="govuk-phase-banner__text">
        |    This is a new service - your <a href="https://www.smartsurvey.co.uk/s/$survey/" class="govuk-link" rel="noreferrer noopener" target="_blank" title="What did you think of this service? (opens in new tab)">feedback</a> will help us to improve it.
        |</span>""".stripMargin
    )
  }
  // scalastyle:on method.length
}
