package viewsapi

import play.api.mvc.Result

object Caching {

  class CacheControl(status: Result) {

    def uncache(): Result = {
      /*"no-store" & "must-revalidate" directives added in order to prevent the browser from caching the version of
       the page loaded initially and instead and force it to request the updated version of the page.
       */
      status.withHeaders("Cache-Control" -> "no-store, must-revalidate")
    }
  }

  implicit def preventCaching(status: Result): CacheControl = new CacheControl(status)
}
