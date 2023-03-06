package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import controllers.AdditionalMetadataController.MetadataProgress
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignment.getConsignment.GetConsignment.ConsignmentStatuses
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, DisplayPropertiesService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  implicit val ec: ExecutionContext = ExecutionContext.global

  "AdditionalMetadataController" should {
    "render the additional metadata start page" in {
      val parentFolder = "parentFolder"
      val parentFolderId = UUID.randomUUID()
      val consignmentId = UUID.randomUUID()
      val consignmentStatuses = List(ConsignmentStatuses("DescriptiveMetadata", "NotEntered"), ConsignmentStatuses("ClosureMetadata", "NotEntered"))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = Option(parentFolderId), consignmentStatuses = consignmentStatuses)
      setDisplayPropertiesResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
      val startPageAsString = contentAsString(response)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(startPageAsString, userType = "standard")

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      startPageAsString must include(s"""<h1 class="govuk-heading-l">Descriptive & closure metadata</h1>""")

      startPageAsString must include(s"""
           |<div class="govuk-notification-banner govuk-!-margin-bottom-4" role="region" aria-labelledby="govuk-notification-banner-title" data-module="govuk-notification-banner">
           |    <div class="govuk-notification-banner__header">
           |        <h2 class="govuk-notification-banner__title" id="govuk-notification-banner-title">
           |            notification.savedProgress.title
           |        </h2>
           |    </div>
           |    <div class="govuk-notification-banner__content">
           |        <h3 class="govuk-notification-banner__heading">
           |            notification.savedProgress.heading
           |        </h3>
           |        <p class="govuk-body">notification.savedProgress.metadataInfo</p>
           |    </div>
           |</div>
           |""".stripMargin)

      startPageAsString must include(
        s"""<p class="govuk-body">You can now add or edit closure and descriptive metadata to your records.</p>""".stripMargin
      )
      startPageAsString must include(
        s"""<div class="govuk-warning-text">
           |    <span class="govuk-warning-text__icon" aria-hidden="true">!</span>
           |    <strong class="govuk-warning-text__text">
           |        <span class="govuk-warning-text__assistive">Warning</span>
           |        additionalMetadata.warningMessage
           |    </strong>
           |</div>""".stripMargin
      )

      verifyCard(
        startPageAsString,
        consignmentId.toString,
        "Descriptive metadata",
        "descriptive",
        "Add descriptive metadata to your files",
        "You do not need to add descriptive metadata but it can enhance your record.",
        MetadataProgress("NOT ENTERED", "grey")
      )
      verifyCard(
        startPageAsString,
        consignmentId.toString,
        "Closure metadata",
        "closure",
        "Add closure and associated metadata to your files",
        "You must add closure metadata to closed files and folders.",
        MetadataProgress("NOT ENTERED", "grey")
      )
    }

    val metadataTypeTable = Table(
      "metadataType",
      "Descriptive",
      "Closure"
    )

    forAll(metadataTypeTable) { metadataType =>
      val statusesTable = Table(
        ("consignmentStatuses", "progress"),
        (ConsignmentStatuses(s"${metadataType}Metadata", "NotEntered") :: Nil, MetadataProgress("NOT ENTERED", "grey")),
        (ConsignmentStatuses(s"${metadataType}Metadata", "Completed") :: Nil, MetadataProgress("ENTERED", "blue")),
        (ConsignmentStatuses(s"${metadataType}Metadata", "Incomplete") :: Nil, MetadataProgress("INCOMPLETE", "red")),
        (Nil, MetadataProgress("NOT ENTERED", "grey"))
      )
      forAll(statusesTable) { (consignmentStatuses, progress) =>
        val statusValue = consignmentStatuses.headOption.map(_.value).getOrElse("Missing Status")
        s"render the progress value for $metadataType with status $statusValue" in {
          val consignmentId = UUID.randomUUID()
          setConsignmentTypeResponse(wiremockServer, "standard")
          setConsignmentDetailsResponse(wiremockServer, consignmentStatuses = consignmentStatuses)
          setDisplayPropertiesResponse(wiremockServer)

          val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
          val consignmentService = new ConsignmentService(graphQLConfiguration)
          val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
          val controller =
            new AdditionalMetadataController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
          val response = controller
            .start(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))
          val startPageAsString = contentAsString(response)
          val (title, description) = if (metadataType == "Closure") {
            ("Add closure and associated metadata to your files", "You must add closure metadata to closed files and folders.")
          } else {
            ("Add descriptive metadata to your files", "You do not need to add descriptive metadata but it can enhance your record.")

          }
          verifyCard(
            startPageAsString,
            consignmentId.toString,
            s"$metadataType metadata",
            metadataType.toLowerCase(),
            title,
            description,
            progress
          )
        }
      }
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val displayPropertiesService = new DisplayPropertiesService(graphQLConfiguration)
      val controller = new AdditionalMetadataController(consignmentService, displayPropertiesService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .start(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  def verifyCard(page: String, consignmentId: String, name: String, metadataType: String, title: String, description: String, progress: MetadataProgress): Unit = {

    page must include(s"""<div class="tdr-card tdr-metadata-card">
                         |    <div class="tdr-card__content">
                         |      <h2 class="govuk-heading-s">
                         |        <a class="govuk-link govuk-link--no-visited-state" href="/consignment/$consignmentId/additional-metadata/files/$metadataType">$name</a>
                         |      </h2>
                         |      <strong class="tdr-metadata-card__state govuk-tag govuk-tag--${progress.colour}">
                         |        ${progress.value}
                         |      </strong>
                         |      <p class="govuk-body">$title</p>
                         |      <p class="govuk-inset-text govuk-!-margin-top-0">$description</p>
                         |      <details class="govuk-details" data-module="govuk-details">
                         |        <summary class="govuk-details__summary">
                         |          <span class="govuk-details__summary-text">What ${name.toLowerCase} you can provide</span>
                         |        </summary>
                         |        <div class="govuk-details__text">
                         |          <ul class="govuk-list govuk-list--bullet govuk-list--spaced">""".stripMargin)

    if (metadataType.equals("closure")) {
      page must include(
        """          <ul class="govuk-list govuk-list--bullet govuk-list--spaced">
          |<Spaces>
          |              <li>FOI decision asserted, this is the date of the Advisory Council approval</li>
          |<Spaces>
          |              <li>Closure start date</li>
          |<Spaces>
          |              <li>Closure period</li>
          |<Spaces>
          |              <li>FOI exemption code</li>
          |<Spaces>
          |          </ul>""".stripMargin.replace("<Spaces>", "            ")
      )
    } else {
      page must include(
        """          <ul class="govuk-list govuk-list--bullet govuk-list--spaced">
          |<Spaces>
          |              <li>Descriptive</li>
          |<Spaces>
          |              <li>Language</li>
          |<Spaces>
          |          </ul>""".stripMargin.replace("<Spaces>", "            ")
      )
      page must include(
        """<details class="govuk-details govuk-!-margin-bottom-2" data-module="govuk-details">
          |  <summary class="govuk-details__summary">
          |    <span class="govuk-details__summary-text">Records with sensitive descriptions</span>
          |  </summary>
          |  <div class="govuk-details__text">
          |    <p class="govuk-body">additionalMetadata.descriptive.sensitive</p>
          |  </div>""".stripMargin
      )
    }
  }
}
