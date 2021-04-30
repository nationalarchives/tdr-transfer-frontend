package configuration

import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.engine.savedrequest.SavedRequestHandler
import org.pac4j.core.exception.http.{FoundAction, HttpAction, RedirectionActionHelper}
import org.pac4j.core.util.Pac4jConstants

class CustomSavedRequestHandler extends SavedRequestHandler {
  override def save(context: WebContext): Unit = {
    println("Saving webContext")

    val requestedUrl = getRequestedUrl(context)
    // Need to specify the type of SessionStore so that we can pass the context into the set method
    context.getSessionStore.asInstanceOf[SessionStore[WebContext]]
      .set(context, Pac4jConstants.REQUESTED_URL, new FoundAction(requestedUrl))
  }

  private def getRequestedUrl(context: WebContext): String = context.getFullRequestURL

  override def restore(context: WebContext, defaultUrl: String): HttpAction = {
    val optRequestedUrl = context.getSessionStore.asInstanceOf[SessionStore[WebContext]]
      .get(context, Pac4jConstants.REQUESTED_URL)

    val requestedAction = if (optRequestedUrl.isPresent) {
      context.getSessionStore.asInstanceOf[SessionStore[WebContext]]
        .set(context, Pac4jConstants.REQUESTED_URL, "")
      Some(optRequestedUrl.get.asInstanceOf[FoundAction])
    } else {
      None
    }

    val redirectAction = requestedAction.getOrElse(new FoundAction(defaultUrl))
    RedirectionActionHelper.buildRedirectUrlAction(context, redirectAction.getLocation)
  }
}
