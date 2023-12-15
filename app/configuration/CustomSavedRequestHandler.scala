package configuration

import org.pac4j.core.context.{CallContext, WebContext}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.engine.savedrequest.SavedRequestHandler
import org.pac4j.core.exception.http.{FoundAction, HttpAction}
import org.pac4j.core.util.{HttpActionHelper, Pac4jConstants}
import play.api.Logging

import scala.jdk.OptionConverters.RichOptional

class CustomSavedRequestHandler extends SavedRequestHandler with Logging {
  override def save(context: CallContext): Unit = {
    logger.info("Saving webContext")
    val webContext = context.webContext()

    val requestedUrl = getRequestedUrl(webContext)

    // Need to specify the type of SessionStore so that we can pass the context into the set method context.
    context.sessionStore().set(webContext, Pac4jConstants.REQUESTED_URL, new FoundAction(requestedUrl))
  }

  private def getRequestedUrl(context: WebContext): String = context.getFullRequestURL

  override def restore(context: CallContext, defaultUrl: String): HttpAction = {
    val webContext = context.webContext()
    val optRequestedUrl = context.sessionStore()
      .get(webContext, Pac4jConstants.REQUESTED_URL)

    val redirectAction = optRequestedUrl.toScala
      .map(_.asInstanceOf[FoundAction])
      .getOrElse(new FoundAction(defaultUrl))
    HttpActionHelper.buildRedirectUrlAction(webContext, redirectAction.getLocation)
  }
}
