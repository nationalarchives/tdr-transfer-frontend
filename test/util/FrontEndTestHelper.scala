package util

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.mockito.ArgumentMatchers.any
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.SecurityComponents
import org.pac4j.play.store.PlaySessionStore
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{BodyParsers, ControllerComponents}
import play.api.test.Helpers.stubControllerComponents

trait FrontEndTestHelper {

  def getAuthorisedSecurityComponents(): SecurityComponents = {
    // Pac4j checks the session to see if there any profiles stored there. If there are, the request is authenticated.

    //Create the profile and add to the map
    val profile: OidcProfile = new OidcProfile()
    profile.setAccessToken(new BearerAccessToken("faketoken"))

    val profileMap: java.util.LinkedHashMap[String, OidcProfile] = new java.util.LinkedHashMap[String, OidcProfile]
    profileMap.put("OidcClient", profile)

    val sessionStore: PlaySessionStore = MockitoSugar.mock[PlaySessionStore]

    //Mock the get method to return the expected map.
    org.mockito.Mockito.doAnswer(_ => profileMap).when(sessionStore).get(any[PlayWebContext](), org.mockito.ArgumentMatchers.eq[String](Pac4jConstants.USER_PROFILES))

    val testConfig = new Config()

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    clients.setClients(new OidcClient())
    testConfig.setClients(clients)

    //Create a new controller with the session store and config. The parser and components don't affect the tests.

    new SecurityComponents {
      override def components: ControllerComponents = stubControllerComponents()
      override def config: Config = testConfig
      override def playSessionStore: PlaySessionStore = sessionStore
      override def parser: BodyParsers.Default = null
    }
  }
}