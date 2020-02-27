package util

import java.io.File
import java.net.URI
import java.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.oauth2.sdk.ResponseType
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import configuration.KeycloakConfiguration
import org.keycloak.representations.AccessToken
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito._
import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.authorization.checker.AuthorizationChecker
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.WebContext
import org.pac4j.core.engine.DefaultSecurityLogic
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.core.exception.http.{FoundAction, RedirectionAction, RedirectionActionHelper, TemporaryRedirectAction}
import org.pac4j.core.http.ajax.AjaxRequestResolver
import org.pac4j.core.profile.UserProfile
import org.pac4j.oidc.client.{KeycloakOidcClient, OidcClient}
import org.pac4j.oidc.config.{KeycloakOidcConfiguration, OidcConfiguration}
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.oidc.redirect.OidcRedirectionActionBuilder
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.SecurityComponents
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, ControllerComponents, Request}
import play.api.test.Helpers.stubControllerComponents
import play.api.test.Injecting
import play.mvc.Http

trait FrontEndTestHelper extends PlaySpec with MockitoSugar with Injecting with GuiceOneAppPerTest with BeforeAndAfterEach {

  override def fakeApplication(): Application = {
    val syncCacheApi = mock[PlayCacheSessionStore]
    GuiceApplicationBuilder()
      .in(new File("src/test/resources/application.conf"))
      .bindings(bind[PlayCacheSessionStore].toInstance(syncCacheApi))
      .build()
  }

  def getValidKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    doAnswer(_ => Some(accessToken)).when(keycloakMock).verifyToken(any[String])
    keycloakMock
  }

  def getInvalidKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    doAnswer(_ => Option.empty).when(keycloakMock).verifyToken(any[String])
    keycloakMock
  }

  def getAuthorisedSecurityComponents: SecurityComponents = {
    // Pac4j checks the session to see if there any profiles stored there. If there are, the request is authenticated.

    //Create the profile and add to the map
    val profile: OidcProfile = new OidcProfile()
    profile.setAccessToken(new BearerAccessToken("faketoken"))

    val profileMap: java.util.LinkedHashMap[String, OidcProfile] = new java.util.LinkedHashMap[String, OidcProfile]
    profileMap.put("OidcClient", profile)

    val sessionStore: PlaySessionStore = mock[PlaySessionStore]

    //Mock the get method to return the expected map.
    doAnswer(_ => java.util.Optional.of(profileMap)).when(sessionStore).get(any[PlayWebContext](), org.mockito.ArgumentMatchers.eq[String](Pac4jConstants.USER_PROFILES))

    val testConfig = new Config()

    //Return true on the isAuthorized method
    val logic = DefaultSecurityLogic.INSTANCE
    logic.setAuthorizationChecker((_: WebContext, _: util.List[UserProfile], _: String, _: util.Map[String, Authorizer[_ <: UserProfile]]) => true)
    testConfig.setSecurityLogic(logic)

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]
    doNothing().when(configuration).init()

    clients.setClients(new OidcClient(configuration))
    testConfig.setClients(clients)

    //Create a new controller with the session store and config. The parser and components don't affect the tests.

    new SecurityComponents {
      override def components: ControllerComponents = stubControllerComponents()
      override def config: Config = testConfig
      override def playSessionStore: PlaySessionStore = sessionStore
      //noinspection ScalaStyle
      override def parser: BodyParsers.Default = null
    }
  }

  def getUnauthorisedSecurityComponents: SecurityComponents = {
    val sessionStore: PlaySessionStore = mock[PlaySessionStore]

    val testConfig = new Config()
    val logic = DefaultSecurityLogic.INSTANCE
    logic setAuthorizationChecker ((_: WebContext, _: util.List[UserProfile], _: String, _: util.Map[String, Authorizer[_ <: UserProfile]]) => false)
    testConfig.setSecurityLogic(logic)

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]

    // Mock the init method to stop it calling out to the keycloak server
    doNothing().when(configuration).init()

    // Set some configuration parameters
    doReturn("tdr").when(configuration).getClientId
    doReturn("code").when(configuration).getResponseType
    doReturn(true).when(configuration).isUseNonce
    val providerMetadata = mock[OIDCProviderMetadata]
    doReturn(URI.create("/auth/realms/tdr/protocol/openid-connect/auth")).when(providerMetadata).getAuthorizationEndpointURI
    doReturn(providerMetadata).when(configuration).getProviderMetadata

    val resolver = mock[AjaxRequestResolver]
    doReturn(false).when(resolver).isAjax(any[PlayWebContext])

    // Create a concrete client
    val client = new OidcClient(configuration)
    client.setName("OidcClient")
    client.setAjaxRequestResolver(resolver)
    client.setRedirectionActionBuilder(new OidcRedirectionActionBuilder(configuration, client))
    client.setCallbackUrl("test")

    clients.setClients(client)
    testConfig.setClients(clients)

    //Create a new controller with the session store and config. The parser and components don't affect the tests.

    new SecurityComponents {
      override def components: ControllerComponents = stubControllerComponents()
      override def config: Config = testConfig
      override def playSessionStore: PlaySessionStore = sessionStore
      //noinspection ScalaStyle
      override def parser: BodyParsers.Default = null
    }
  }
}