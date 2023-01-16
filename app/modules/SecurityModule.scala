package modules

import com.google.inject.{AbstractModule, Provides}
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import configuration.CustomSavedRequestHandler
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.engine.{DefaultCallbackLogic, DefaultSecurityLogic}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.PlayCacheSessionStore
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}

class SecurityModule extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[SessionStore]).to(classOf[PlayCacheSessionStore])
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])

    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/")
    callbackController.setRenewSession(false)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new LogoutController()
    val configuration = Configuration.load(Environment.simple())
    logoutController.setDefaultUrl(configuration.get[String]("logout.url"))
    // Logs out of the pac4j session. It does this by updating the pac4j class stored in redis
    logoutController.setLocalLogout(true)
    // Logs out of the keycloak session
    logoutController.setCentralLogout(true)
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides
  def provideOidcClient: OidcClient = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId("tdr")
    val configuration = Configuration.load(Environment.simple())
    val authUrl = configuration.get[String]("auth.url")
    val callback = configuration.get[String]("auth.callback")
    val secret = configuration.get[String]("auth.secret")
    oidcConfiguration.setSecret(secret)
    oidcConfiguration.setDiscoveryURI(s"$authUrl/realms/tdr/.well-known/openid-configuration")
    oidcConfiguration.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    oidcConfiguration.setPreferredJwsAlgorithm(JWSAlgorithm.RS256)
    // Setting this causes pac4j to get a new access token using the refresh token when the original access token expires
    oidcConfiguration.setExpireSessionWithToken(true)
    val oidcClient = new OidcClient(oidcConfiguration)
    oidcClient.setCallbackUrl(callback)
    oidcClient
  }

  @Provides
  def provideConfig(oidcClient: OidcClient): Config = {
    val clients = new Clients(oidcClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new FrontendHttpActionAdaptor())
    val authoriser = new RequireAnyRoleAuthorizer("tdr-google-user")
    config.setAuthorizer(authoriser)
    val customRequestHandler = new CustomSavedRequestHandler
    val callbackLogic = DefaultCallbackLogic.INSTANCE
    callbackLogic.setSavedRequestHandler(customRequestHandler)
    config.setCallbackLogic(callbackLogic)
    val securityLogic = DefaultSecurityLogic.INSTANCE
    securityLogic.setSavedRequestHandler(customRequestHandler)

    config
  }
}
