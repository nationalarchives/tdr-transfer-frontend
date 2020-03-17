package modules

import java.util

import com.google.inject.{AbstractModule, Provides}
import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.CommonProfile
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}


class SecurityModule extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])

    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/")
    callbackController.setMultiProfile(true)
    callbackController.setRenewSession(false)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/")
    logoutController.setLocalLogout(false)
    logoutController.setCentralLogout(true)
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides
  def provideOidcClient: OidcClient[OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId("tdr")
    val configuration = Configuration.load(Environment.simple())
    val authUrl = configuration.get[String]("auth.url")
    val callback = configuration.get[String]("auth.callback")
    val secret = configuration.get[String]("auth.secret")
    oidcConfiguration.setSecret(secret)
    oidcConfiguration.setDiscoveryURI(s"$authUrl/auth/realms/tdr/.well-known/openid-configuration")
    val oidcClient = new OidcClient[OidcConfiguration](oidcConfiguration)
    oidcClient.setCallbackUrl(callback)
    oidcClient
  }

  @Provides
  def provideConfig(oidcClient: OidcClient[OidcConfiguration]): Config = {
    val clients = new Clients(oidcClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new FrontendHttpActionAdaptor())
    config.addAuthorizer("custom", new CustomAuthoriser)
    config
  }
}

/*
pac4j has a default authoriser which checks for csrf tokens. The problem
is that it doesn't work and there's not a lot of documentation on how to
make it work.

The other problem is that play has csrf checkers which are more than
adequate so what we need is to turn off the pac4j one.

If you don't provide an authoriser, it gives you the csrf one by default
so the trick is to give it a dummy authoriser to stop it checking the
csrf one. Which is what this does.

This has no effect on whether logged out users can see or not see
certain pages. This is authorisation which we're not doing in the front
end, although if we do decide to do front end authorisation, we can
expand the CustomAuthoriser class to something more useful.
*/
class CustomAuthoriser extends Authorizer[CommonProfile] {
  override def isAuthorized(context: WebContext, profiles: util.List[CommonProfile]): Boolean = true
}
