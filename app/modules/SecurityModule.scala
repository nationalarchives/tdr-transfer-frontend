package modules

import com.google.inject.{AbstractModule, Provides}
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
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
    config
  }
}
