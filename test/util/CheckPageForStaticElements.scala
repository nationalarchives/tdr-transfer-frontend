package util

import org.scalatest.matchers.must.Matchers._
import play.api.test.Helpers.contentAsString

class CheckPageForStaticElements() {
  def checkContentOfPagesThatUseMainScala(page: String, signedIn: Boolean=true, userType: String=""): Unit = {
    page must include ("This is a new service – your feedback will help us to improve it. Please")
    page must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
    page must include ("/contact")
    page must include ("/cookies")
    page must include ("All content is available under the")
    page must include (
      "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
    )
    page must include (", except where otherwise stated")
    page must include ("© Crown copyright")

    if(signedIn){
      page must include ("name")
      page must include ("Sign out")

      if(userType == "judgment") {
        page must include (s"""" href="/faq">""")
        page must include (s"""" href="/help">""")
      } else if(userType == "standard") {
        page must include ("/faq")
        page must include ("/help")
      }
    }
  }
}
