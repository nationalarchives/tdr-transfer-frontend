package configuration

import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.engine.savedrequest.SavedRequestHandler
import org.pac4j.core.exception.http.{FoundAction, HttpAction}
import org.pac4j.core.util.{HttpActionHelper, Pac4jConstants}
import play.api.Logging

import scala.jdk.OptionConverters.RichOptional

class CustomSavedRequestHandler extends SavedRequestHandler with Logging {
  override def save(context: WebContext, sessionStore: SessionStore): Unit = {
    logger.info("Saving webContext")

    val requestedUrl = getRequestedUrl(context)

    // Need to specify the type of SessionStore so that we can pass the context into the set method context.
    sessionStore
      .set(context, Pac4jConstants.REQUESTED_URL, new FoundAction(requestedUrl))
  }

  private def getRequestedUrl(context: WebContext): String = context.getFullRequestURL

  override def restore(context: WebContext, sessionStore: SessionStore, defaultUrl: String): HttpAction = {
    val optRequestedUrl = sessionStore
      .get(context, Pac4jConstants.REQUESTED_URL)

    val redirectAction = optRequestedUrl.toScala
      .map(_.asInstanceOf[FoundAction])
      .getOrElse(new FoundAction(defaultUrl))
    HttpActionHelper.buildRedirectUrlAction(context, redirectAction.getLocation)
  }
}
