package testUtils

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddConsignmentStatus.addConsignmentStatus.AddConsignmentStatus
import graphql.codegen.AddConsignmentStatus.{addConsignmentStatus => acs}
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.GetConsignment.{getConsignment => gcd}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => gcsu}
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.{ConsignmentStatuses => ecs}
import graphql.codegen.GetConsignments.{getConsignments => gc}
import graphql.codegen.GetConsignmentsForMetadataReview.{getConsignmentsForMetadataReview, getConsignmentsForMetadataReview => gcfmr}
import graphql.codegen.UpdateClientSideDraftMetadataFileName.{updateClientSideDraftMetadataFileName => ucsdmfn}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.keycloak.representations.AccessToken
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.session.{SessionStore, SessionStoreFactory}
import org.pac4j.core.context.{CallContext, FrameworkParameters}
import org.pac4j.core.engine.DefaultSecurityLogic
import org.pac4j.core.http.ajax.AjaxRequestResolver
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.metadata.OidcOpMetadataResolver
import org.pac4j.oidc.profile.{OidcProfile, OidcProfileDefinition}
import org.pac4j.oidc.redirect.OidcRedirectionActionBuilder
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.SecurityComponents
import org.pac4j.play.store.PlayCacheSessionStore
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.{PatienceConfig, scaled}
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1, TableFor2}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, ControllerComponents}
import play.api.test.Helpers.stubControllerComponents
import play.api.test.Injecting
import play.api.{Application, Configuration}
import services.Statuses.{InProgressValue, SeriesType, StatusType, StatusValue}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils.UserDetails
import uk.gov.nationalarchives.tdr.keycloak.Token
import viewsapi.FrontEndInfo

import java.io.File
import java.net.URI
import java.time.{LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.existentials

trait FrontEndTestHelper extends PlaySpec with MockitoSugar with Injecting with GuiceOneAppPerTest with BeforeAndAfterEach with TableDrivenPropertyChecks {

  implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit class AwaitFuture[T](future: Future[T]) {
    def await(timeout: Duration = 2.seconds): T = {
      Await.result(future, timeout)
    }
  }

  def setConsignmentTypeResponse(wiremockServer: WireMockServer, consignmentType: String): StubMapping = {
    val dataString = s"""{"data": {"getConsignment": {"consignmentType": "$consignmentType"}}}"""
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentType"))
        .willReturn(okJson(dataString))
    )
  }

  def setConsignmentReferenceResponse(wiremockServer: WireMockServer, consignmentRef: String = "TEST-TDR-2021-GB"): StubMapping = {
    val dataString = s"""{"data": {"getConsignment": {"consignmentReference": "$consignmentRef"}}}"""
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentReference"))
        .willReturn(okJson(dataString))
    )
  }

  def setAddConsignmentStatusResponse(
      wiremockServer: WireMockServer,
      consignmentId: UUID = UUID.randomUUID(),
      statusType: StatusType = SeriesType,
      statusValue: StatusValue = InProgressValue
  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[acs.Data, acs.Variables]()
    val dataString = client
      .GraphqlData(Option(acs.Data(AddConsignmentStatus(UUID.randomUUID(), consignmentId, statusType.id, statusValue.value, ZonedDateTime.now(), None))))
      .asJson
      .printWith(Printer.noSpaces)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("addConsignmentStatus"))
        .willReturn(okJson(dataString))
    )
  }

  def setConsignmentDetailsResponse(
      wiremockServer: WireMockServer,
      parentFolder: Option[String] = None,
      consignmentReference: String = "TEST-TDR-2021-GB",
      parentFolderId: Option[UUID] = None,
      clientSideDraftMetadataFileName: Option[String] = None,
      consignmentStatuses: List[gcd.GetConsignment.ConsignmentStatuses]
  ): StubMapping = {
    val folderOrNull = parentFolder.map(folder => s""" "$folder" """).getOrElse("null")
    val folderIdOrNull = parentFolderId.map(id => s""" "$id" """).getOrElse("null")
    val fileNameOrNull = clientSideDraftMetadataFileName.map(fn => s""" "$fn" """).getOrElse("null")
    val dataString =
      s"""{"data": {"getConsignment": {
         |"consignmentReference": "$consignmentReference",
         | "parentFolder": $folderOrNull,
         | "parentFolderId": $folderIdOrNull,
         | "userid" : "${UUID.randomUUID()}",
         | "seriesid": "${UUID.randomUUID()}",
         | "clientSideDraftMetadataFileName": $fileNameOrNull,
         | "consignmentStatuses": ${consignmentStatuses.asJson.printWith(Printer.noSpaces)}
         | }}} """.stripMargin

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
        .willReturn(okJson(dataString))
    )
  }

  def setConsignmentStatusResponse(
      config: Configuration,
      wiremockServer: WireMockServer,
      seriesId: Option[UUID] = None,
      consignmentStatuses: List[ConsignmentStatuses] = Nil
  ): StubMapping = {
    val client = new GraphQLConfiguration(config).getClient[gcs.Data, gcs.Variables]()
    val consignmentResponse = gcs.Data(
      Option(
        GetConsignment(
          seriesId,
          Some("MOCK1"),
          consignmentStatuses
        )
      )
    )
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentStatus"))
        .willReturn(okJson(dataString))
    )
  }

  def setConsignmentSummaryResponse(
      wiremockServer: WireMockServer,
      seriesName: Option[String] = None,
      transferringBodyName: Option[String] = None,
      totalFiles: Int = 0,
      consignmentReference: String = "TEST-TDR-2021-GB"
  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcsu.Data, gcsu.Variables]()
    val consignmentSummaryResponse = gcsu.Data(
      Option(
        gcsu.GetConsignment(
          seriesName,
          transferringBodyName,
          totalFiles,
          consignmentReference
        )
      )
    )
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentSummaryResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentSummary"))
        .willReturn(okJson(dataString))
    )
  }

  def setUpdateConsignmentStatus(wiremockServer: WireMockServer): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[ucs.Data, ucs.Variables]()
    val data = client.GraphqlData(Option(ucs.Data(Option(1))), Nil)
    val ucsDataString = data.asJson.noSpaces
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("updateConsignmentStatus"))
        .willReturn(okJson(ucsDataString))
    )
  }

  def setGetConsignmentsForMetadataReviewResponse(wiremockServer: WireMockServer): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcfmr.Data, gcfmr.Variables]()
    val consignment =
      gcfmr.GetConsignmentsForMetadataReview(Some(UUID.fromString("fe214a75-cfc4-4767-a7ab-e9b4b2ca700d")), "TDR-2024-TEST", Some("SeriesName"), Some("TransferringBody"))
    val getConsignmentsForReviewResponse: getConsignmentsForMetadataReview.Data = gcfmr.Data(List(consignment))
    val data = client.GraphqlData(Some(getConsignmentsForReviewResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentsForMetadataReview"))
        .willReturn(okJson(dataString))
    )
  }

  def setConsignmentViewTransfersResponse(
      wiremockServer: WireMockServer,
      consignmentType: String,
      noConsignment: Boolean = false,
      statuses: List[ecs] = List(),
      totalPages: Int = 1
  ): List[Edges] = {

    val client = new GraphQLConfiguration(app.configuration).getClient[gc.Data, gc.Variables]()
    val consignmentId = UUID.randomUUID()
    val edges = generateConsignmentEdges(consignmentId, consignmentType, statuses)

    val consignments = gc.Data(
      gc.Consignments(
        if (noConsignment) None else edges.some,
        Consignments.PageInfo(hasNextPage = false, None),
        totalPages.some
      )
    )

    val data: client.GraphqlData = client.GraphqlData(Some(consignments))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignments"))
        .willReturn(okJson(dataString))
    )
    edges.map(_.get)
  }

  def setUpdateClientSideFileNameResponse(wireMockServer: WireMockServer): Unit = {
    val client: GraphQLClient[ucsdmfn.Data, ucsdmfn.Variables] = new GraphQLConfiguration(app.configuration).getClient[ucsdmfn.Data, ucsdmfn.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(ucsdmfn.Data(Some(1))))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wireMockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("updateClientSideDraftMetadataFileName"))
        .willReturn(okJson(dataString))
    )
  }

  def generateConsignmentEdges(consignmentId: UUID, consignmentType: String, statuses: List[ecs]): List[Some[Edges]] = {
    val someDateTime = Some(ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault()))
    List(
      Some(
        Edges(
          Node(
            consignmentid = Some(consignmentId),
            consignmentReference = s"consignment-ref-1",
            consignmentType = Some(consignmentType),
            exportDatetime = someDateTime,
            createdDatetime = someDateTime,
            statuses,
            totalFiles = 1
          ),
          "Cursor"
        )
      )
    )
  }

  val userTypes: TableFor1[String] = Table(
    "User type",
    "judgment",
    "standard"
  )

  val userChecks: TableFor2[KeycloakConfiguration, String] = Table(
    ("user", "url"),
    (getValidJudgmentUserKeycloakConfiguration, "consignment"),
    (getValidStandardUserKeycloakConfiguration, "judgment")
  )

  val consignmentStatuses: TableFor1[String] = Table(
    "Export status",
    "InProgress",
    "Completed",
    "Failed"
  )

  def frontEndInfoConfiguration: ApplicationConfig = {
    val frontEndInfoConfiguration: ApplicationConfig = mock[ApplicationConfig]
    when(frontEndInfoConfiguration.frontEndInfo).thenReturn(
      FrontEndInfo(
        "https://mock-api-url.com/graphql",
        "mockStage",
        "mockRegion",
        "https://mock-upload-url.com",
        "https://mock-auth-url.com",
        "mockClientId",
        "mockRealm",
        "","",""
      )
    )
    frontEndInfoConfiguration
  }

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
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidStandardUserKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    accessToken.setOtherClaims("standard_user", "true")
    accessToken.setName("Standard Username")
    accessToken.setEmail("test@example.com")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidJudgmentUserKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    accessToken.setOtherClaims("judgment_user", "true")
    accessToken.setName("Judgment Username")
    accessToken.setEmail("test@example.com")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidTNAUserKeycloakConfiguration(isTransferAdvisor: Boolean = false): KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    accessToken.setOtherClaims("tna_user", "true")
    if (isTransferAdvisor) accessToken.setOtherClaims("transfer_adviser", "true")
    accessToken.setName("TNA Username")
    accessToken.setEmail("test@example.com")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    when(keycloakMock.userDetails(any[String])).thenReturn(Future(UserDetails("email@test.com")))
    keycloakMock
  }

  def getValidKeycloakConfigurationWithoutBody: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getInvalidKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    doAnswer(_ => Option.empty).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getAuthorisedSecurityComponents: SecurityComponents = {
    // This is the dummy token generated with jwt.io
    val standardUserJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlN0YW5kYXJkIFVzZXJuYW1lIn0" +
      ".PvFaiOxzQ2xSybRHnJTSMtms0erV-SIxHIomUmu-aoE"
    getAuthorisedSecurityComponents(standardUserJwtToken)
  }

  def getAuthorisedSecurityComponentsForJudgmentUser: SecurityComponents = {
    // This is the dummy token generated with jwt.io
    val judgmentUserJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6Ikp1ZGdtZW50IFVzZXJuYW1lIn0" +
      ".U57U5JIR4mkeIPJGd_m-bGZF5AyeulMUGdbTxyFeVdg"
    getAuthorisedSecurityComponents(judgmentUserJwtToken)
  }

  def getAuthorisedSecurityComponents(jwtToken: String): SecurityComponents = {
    // Pac4j checks the session to see if there any profiles stored there. If there are, the request is authenticated.

    // Create the profile and add to the map
    val profile: OidcProfile = new OidcProfile()

    profile.setAccessToken(new BearerAccessToken(jwtToken))
    profile.addAttribute(OidcProfileDefinition.EXPIRATION, Date.from(LocalDateTime.now().plusDays(10).toInstant(ZoneOffset.UTC)))

    val profileMap: java.util.LinkedHashMap[String, OidcProfile] = new java.util.LinkedHashMap[String, OidcProfile]
    profileMap.put("OidcClient", profile)

    val playCacheSessionStore: SessionStore = mock[PlayCacheSessionStore]

    // Mock the get method to return the expected map.
    doAnswer(_ => java.util.Optional.of(profileMap))
      .when(playCacheSessionStore)
      .get(
        any[PlayWebContext](),
        org.mockito.ArgumentMatchers.eq[String](Pac4jConstants.USER_PROFILES)
      )

    val testConfig = new Config()

    // Return true on the isAuthorized method
    val logic = DefaultSecurityLogic.INSTANCE
    logic.setAuthorizationChecker((_, _, _, _, _, _) => true)
    testConfig.setSecurityLogic(logic)

    // There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]
    doNothing().when(configuration).init()

    clients.setClients(new OidcClient(configuration))
    testConfig.setClients(clients)

    // Create a new controller with the session store and config. The parser and components don't affect the tests.
    securityComponents(testConfig, playCacheSessionStore)
  }

  private def securityComponents(testConfig: Config, playCacheSessionStore: SessionStore): SecurityComponents = new SecurityComponents {
    override def components: ControllerComponents = stubControllerComponents()

    override def config: Config = testConfig

    config.setSessionStoreFactory(new SessionStoreFactory {
      override def newSessionStore(parameters: FrameworkParameters): SessionStore = playCacheSessionStore
    })

    // scalastyle:off null
    override def parser: BodyParsers.Default = null
    // scalastyle:on null
  }

  def getUnauthorisedSecurityComponents: SecurityComponents = {
    val testConfig = new Config()
    val playCacheSessionStore: SessionStore = mock[PlayCacheSessionStore]

    val logic = DefaultSecurityLogic.INSTANCE
    logic setAuthorizationChecker ((_, _, _, _, _, _) => false)
    testConfig.setSecurityLogic(logic)

    // There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]
    val providerMetadata = mock[OIDCProviderMetadata]

    /*
    configuration = mock(OidcConfiguration.class);
    var metadata = mock(OIDCProviderMetadata.class);
    when(metadata.getIssuer()).thenReturn(new Issuer(PAC4J_URL));
    when(metadata.getJWKSetURI()).thenReturn(new URI(PAC4J_BASE_URL));
    val metadataResolver = mock(OidcOpMetadataResolver.class);
    when(metadataResolver.load()).thenReturn(metadata);
    when(configuration.getOpMetadataResolver()).thenReturn(metadataResolver);

     */

    // Mock the init method to stop it calling out to the keycloak server
    doNothing().when(configuration).init()

    // Set some configuration parameters
    doReturn("tdr").when(configuration).getClientId
    doReturn("code").when(configuration).getResponseType
    doReturn(true).when(configuration).isUseNonce
    val metadataResolver = mock[OidcOpMetadataResolver]
    doReturn(URI.create("/auth/realms/tdr/protocol/openid-connect/auth")).when(providerMetadata).getAuthorizationEndpointURI
    doReturn(providerMetadata).when(metadataResolver).load
    doReturn(metadataResolver).when(configuration).getOpMetadataResolver

    val resolver = mock[AjaxRequestResolver]
    doReturn(false).when(resolver).isAjax(any[CallContext])

    // Create a concrete client
    val client = new OidcClient(configuration)
    client.setName("OidcClient")
    client.setAjaxRequestResolver(resolver)
    client.setRedirectionActionBuilder(new OidcRedirectionActionBuilder(client))
    client.setCallbackUrl("test")

    clients.setClients(client)
    testConfig.setClients(clients)

    // Create a new controller with the session store and config. The parser and components don't affect the tests.
    securityComponents(testConfig, playCacheSessionStore)
  }
  def stubUpdateTransferInitiatedResponse(wiremockServer: WireMockServer, consignmentId: UUID): Any = {
    val utClient = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
    val utData: utClient.GraphqlData = utClient.GraphqlData(Some(ut.Data(Option(1))), List())
    val utDataString: String = utData.asJson.printWith(Printer(dropNullValues = false, ""))

    val utQuery: String =
      s"""{"query":"mutation updateTransferInitiated($$consignmentId:UUID!)
         |                 {updateTransferInitiated(consignmentid:$$consignmentId)}",
         | "variables":{"consignmentId": "${consignmentId.toString}"}
         |}""".stripMargin.replaceAll("\n\\s*", "")

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(utQuery))
        .willReturn(okJson(utDataString))
    )
  }

  def stubFinalTransferConfirmationResponseWithServer(wiremockServer: WireMockServer, consignmentId: UUID, errors: List[GraphQLClient.Error] = Nil): Unit = {

    val addFinalTransferConfirmationResponse = errors.size match {
      case 0 =>
        Some(
          new aftc.AddFinalTransferConfirmation(
            consignmentId,
            legalCustodyTransferConfirmed = true
          )
        )
      case _ => None
    }
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(addFinalTransferConfirmationResponse.map(ftc => aftc.Data(ftc)), errors)

    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation addFinalTransferConfirmation($$input:AddFinalTransferConfirmationInput!)
                            {addFinalTransferConfirmation(addFinalTransferConfirmationInput:$$input)
                            {consignmentId legalCustodyTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"$consignmentId",
                                 "legalCustodyTransferConfirmed":true
                                }
                       }
                             }""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(query))
        .willReturn(okJson(dataString))
    )
  }

  def toDummyConsignmentStatuses(statusMap: Map[StatusType, StatusValue], dummyConsignmentId: UUID = UUID.randomUUID()): List[ConsignmentStatuses] =
    statusMap.map { case (statusType, statusValue) =>
      ConsignmentStatuses(
        consignmentStatusId = UUID.randomUUID(),
        consignmentId = dummyConsignmentId,
        statusType = statusType.id,
        value = statusValue.value,
        createdDatetime = ZonedDateTime.now(),
        modifiedDatetime = Some(ZonedDateTime.now())
      )
    }.toList

  def downloadLinkHTML(consignmentId: UUID): String = {
    val linkHTML: String = s"""<a class="govuk-button govuk-button--secondary download-metadata" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">
                              |    <span aria-hidden="true" class="tna-button-icon">
                              |        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 23 23">
                              |            <path fill="#020202" d="m11.5 16.75-6.563-6.563 1.838-1.903 3.412 3.413V1h2.626v10.697l3.412-3.413 1.837 1.903L11.5 16.75ZM3.625 22c-.722 0-1.34-.257-1.853-.77A2.533 2.533 0 0 1 1 19.375v-3.938h2.625v3.938h15.75v-3.938H22v3.938c0 .722-.257 1.34-.77 1.855a2.522 2.522 0 0 1-1.855.77H3.625Z"></path>
                              |        </svg>
                              |    </span>
                              |    Download metadata
                              |</a>
                              |""".stripMargin
    linkHTML
  }
}
