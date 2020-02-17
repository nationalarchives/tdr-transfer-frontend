package util

import java.io.File

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import org.keycloak.representations.AccessToken
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.redirect.RedirectAction
import org.pac4j.oidc.client.{KeycloakOidcClient, OidcClient}
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
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
import play.api.mvc.{BodyParsers, ControllerComponents}
import play.api.test.Helpers.stubControllerComponents
import play.api.test.Injecting

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

  def getAuthorisedSecurityComponents(): SecurityComponents = {
    // Pac4j checks the session to see if there any profiles stored there. If there are, the request is authenticated.

    //Create the profile and add to the map
    val profile: OidcProfile = new OidcProfile()

    profile.setAccessToken(new BearerAccessToken("test"))

    val profileMap: java.util.LinkedHashMap[String, OidcProfile] = new java.util.LinkedHashMap[String, OidcProfile]
    profileMap.put("OidcClient", profile)

    val sessionStore: PlaySessionStore = mock[PlaySessionStore]

    //Mock the get method to return the expected map.
    doAnswer(_ => profileMap).when(sessionStore).get(any[PlayWebContext](), org.mockito.ArgumentMatchers.eq[String](Pac4jConstants.USER_PROFILES))

    val testConfig = new Config()

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

  def getUnauthorisedSecurityComponents(): SecurityComponents = {
    val sessionStore: PlaySessionStore = mock[PlaySessionStore]

    val testConfig = new Config()

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]

    // Mock the init method to stop it calling out to the keycloak server
    doNothing().when(configuration).init()

    // Mock the redirect return when the client is unauthorised
    val client = mock[KeycloakOidcClient]
    doAnswer(_ => "OidcClient").when(client).getName

    doAnswer(_ => RedirectAction.redirect("/auth/realms/tdr/protocol/openid-connect/auth")).when(client).getRedirectAction(any[PlayWebContext])

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