package modules

import org.pac4j.core.context.HttpConstants
import org.pac4j.core.exception.http.HttpAction
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import play.mvc.{Result, Results}

class FrontendHttpActionAdaptor extends PlayHttpActionAdapter {

  override def adapt(action: HttpAction, context: PlayWebContext): Result = {
    action match {
      case _ => val code = action.getCode
        if (code == HttpConstants.UNAUTHORIZED) {
          Results.redirect("/")
        } else if (code == HttpConstants.FORBIDDEN) {
          Results.redirect("/")
        } else {
          super.adapt(action, context)
        }
    }
  }
}
