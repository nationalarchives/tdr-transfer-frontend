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
    page must include("""
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <meta name="robots" content="noindex">
    |    <link rel="stylesheet" media="screen" href="/assets/stylesheets/main.css">
    |    <link rel="shortcut icon" type="image/ico" href="/assets/images/favicon.ico">
    |    <script  src="/assets/javascripts/all.js" type="text/javascript"></script>
    |    <script  src="/assets/javascripts/main.js" type="text/javascript"></script>""".stripMargin)
    page must include("""<a href="#main-content" class="govuk-skip-link" data-module="govuk-skip-link">Skip to main content</a>""")
    page must include("This is a new service – your feedback will help us to improve it. Please")
    page must include("href=\"/contact\">get in touch (opens in new tab).</a>")
    page must include("""href="/contact">""")
    page must include("""href="/cookies">""")
    page must include("All content is available under the")
    page must include(
      "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
    )
    page must include(", except where otherwise stated")
    page must include("© Crown copyright")
    if (signedIn) {
      checkContentOfSignedInPagesThatUseMainScala(page, userType, consignmentExists, transferStillInProgress, pageRequiresAwsServices)
    } else {
      page must not include "/faq"
      page must not include "/help"
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
      """    <a href="/sign-out" class="govuk-header__link">
        |                                Sign out""".stripMargin
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
    if (userType == "judgment") {
      page must include("Judgment Username")
      page must include("""href="/judgment/faq">""")
      page must include("""href="/judgment/help">""")
      if (consignmentExists) {
        page must include("TEST-TDR-2021-GB")

        if (transferStillInProgress) {
          page must include("""<span class="govuk-caption-l">progressIndicator.step</span>""")
          page must include("Transfer reference")
        }
      }
    } else if (userType == "standard") {
      page must include("Standard Username")
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
  // scalastyle:on method.length
}
