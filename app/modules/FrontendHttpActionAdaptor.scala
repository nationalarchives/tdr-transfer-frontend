package modules

import org.pac4j.core.context.HttpConstants
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import play.mvc.{Result, Results}

class FrontendHttpActionAdaptor extends PlayHttpActionAdapter {

  override def adapt(code: Int, context: PlayWebContext): Result = {
    if (code == HttpConstants.UNAUTHORIZED) {
      Results.redirect("/")
    } else if (code == HttpConstants.FORBIDDEN) {
      Results.redirect("/")
    } else {
      super.adapt(code, context)
    }
  }
}

