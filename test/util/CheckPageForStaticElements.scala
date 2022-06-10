package util

import org.scalatest.matchers.must.Matchers._

class CheckPageForStaticElements() {
  //scalastyle:off method.length
  def checkContentOfPagesThatUseMainScala(page: String,
                                          signedIn: Boolean=true,
                                          userType: String="",
                                          consignmentExists: Boolean=true,
                                          pageRequiresAwsServices: Boolean=false): Unit = {
    page must include ("""
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <meta name="robots" content="noindex">
    |    <link rel="stylesheet" media="screen" href="/assets/stylesheets/main.css">
    |    <link rel="shortcut icon" type="image/ico" href="/assets/images/favicon.ico">
    |    <script  src="/assets/javascripts/all.js" type="text/javascript"></script>
    |    <script  src="/assets/javascripts/main.js" type="text/javascript"></script>""".stripMargin
    )
    page must include ("This is a new service – your feedback will help us to improve it. Please")
    page must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
    page must include ("""href="/contact">""")
    page must include ("""href="/cookies">""")
    page must include ("All content is available under the")
    page must include (
      "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
    )
    page must include (", except where otherwise stated")
    page must include ("© Crown copyright")

    if(signedIn){
      page must include (
      """<a href="/sign-out" class="govuk-header__link">
      |                            Sign out""".stripMargin
      )
      page must include ("""    <dialog class="timeout-dialog">
                           |        <div>
                           |            <h2 class="govuk-heading-m">You have been inactive for more than 30 minutes.</h2>
                           |            <p class="govuk-body">You will be logged out if you do not respond in 5 minutes. We do this to keep your information secure.</p>
                           |        </div>
                           |        <div class="govuk-button-group">
                           |            <button id="extend-timeout" class="govuk-button" role="button">
                           |                Keep me signed in
                           |            </button>
                           |            <a class="govuk-link" href="/sign-out">Sign me out</a>
                           |        </div>
                           |    </dialog>""".stripMargin)
      if(userType == "judgment") {
        page must include ("Judgment Username")
        page must include ("""href="/judgment/faq">""")
        page must include ("""href="/judgment/help">""")
        if(consignmentExists) {
          page must include ("""<span class="govuk-caption-l">progressIndicator.step</span>""")
          page must include ("Transfer reference")
          page must include ("TEST-TDR-2021-GB")
        }
      } else if(userType == "standard") {
        page must include ("Standard Username")
        page must include ("""href="/faq">""")
        page must include ("""href="/help">""")
        if(consignmentExists) {
          page must include ("""<span class="govuk-caption-l">progressIndicator.step</span>""")
          page must include ("Consignment reference")
          page must include ("TEST-TDR-2021-GB")
        }
      }
      if(pageRequiresAwsServices){
          page must include (
          """<input type="hidden" class="api-url" value="https://mock-api-url.com/graphql">
            |<input type="hidden" class="stage" value="mockStage">
            |<input type="hidden" class="region" value="mockRegion">
            |<input type="hidden" class="upload-url" value="https://mock-upload-url.com">""".stripMargin
        )
      }
    }
  }
  //scalastyle:on method.length
}
