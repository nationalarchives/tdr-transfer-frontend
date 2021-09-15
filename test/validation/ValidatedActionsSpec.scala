package validation

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status.OK
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import sangria.ast.Document
import sttp.client.{NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import util.FrontEndTestHelper
import play.api.test.Helpers.{contentAsString, status => playStatus, _}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class ValidatedActionsSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  class MockValidatedActions(graphqlConfigurationMock: GraphQLConfiguration) extends ValidatedActions {
    implicit val ec: ExecutionContext = ExecutionContext.global

    override def keycloakConfiguration: KeycloakConfiguration = getValidKeycloakConfiguration

    override protected def controllerComponents: SecurityComponents = getAuthorisedSecurityComponents

    override val graphqlConfiguration: GraphQLConfiguration = graphqlConfigurationMock
  }

  private def mockUploadPermittedGraphqlResponse(transferAgreementData: itac.Data, consignmentStatusData: gcs.Data) = {
    val graphqlConfigurationMock: GraphQLConfiguration = mock[GraphQLConfiguration]
    val transferAgreementClient = mock[GraphQLClient[itac.Data, itac.Variables]]
    val consignmentStatusClient = mock[GraphQLClient[gcs.Data, gcs.Variables]]

    when(transferAgreementClient.getResult[Future](any[BearerAccessToken], any[Document], any[Option[itac.Variables]])
      (any[SttpBackend[Future, Nothing, NothingT]], any[ClassTag[Future[_]]]))
      .thenReturn(Future(GraphQlResponse(Option(transferAgreementData), List())))

    when(consignmentStatusClient.getResult[Future](any[BearerAccessToken], any[Document], any[Option[gcs.Variables]])
      (any[SttpBackend[Future, Nothing, NothingT]], any[ClassTag[Future[_]]]))
      .thenReturn(Future(GraphQlResponse(Option(consignmentStatusData), List())))

    when(graphqlConfigurationMock.getClient[itac.Data, itac.Variables]()).thenReturn(transferAgreementClient)
    when(graphqlConfigurationMock.getClient[gcs.Data, gcs.Variables]()).thenReturn(consignmentStatusClient)

    graphqlConfigurationMock
  }

  def mockConsignmentExistsGraphqlResponse(getConsignmentData: gc.Data): GraphQLConfiguration = {
    val graphqlConfigurationMock: GraphQLConfiguration = mock[GraphQLConfiguration]
    val client = mock[GraphQLClient[gc.Data, gc.Variables]]

    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[gc.Variables]])
      (any[SttpBackend[Future, Nothing, NothingT]], any[ClassTag[Future[_]]]))
      .thenReturn(Future(GraphQlResponse(Option(getConsignmentData), List())))

    when(graphqlConfigurationMock.getClient[gc.Data, gc.Variables]()).thenReturn(client)

    graphqlConfigurationMock
  }

  "uploadPermitted function" should {
    "call the controller function if the transfer agreement exists and the upload is not in progress" in {
      val graphqlConfigurationMock = mockUploadPermittedGraphqlResponse(itac.Data(Option(itac.GetTransferAgreement(true))), gcs.Data(Option(GetConsignment(CurrentStatus(None, Option.empty)))))

      val functionMock = mock[Request[AnyContent] => Result]

      new MockValidatedActions(graphqlConfigurationMock).uploadPermitted(UUID.randomUUID())(functionMock)(FakeRequest()).futureValue

      verify(functionMock).apply(any[Request[AnyContent]])
    }

    "return a redirect to the transfer agreement page if the transfer agreement is not complete" in {
      val consignmentId = UUID.randomUUID()

      val graphqlConfigurationMock = mockUploadPermittedGraphqlResponse(itac.Data(Option(itac.GetTransferAgreement(false))), gcs.Data(Option(GetConsignment(CurrentStatus(None, Option.empty)))))
      val functionMock = mock[Request[AnyContent] => Result]

      val response = new MockValidatedActions(graphqlConfigurationMock).uploadPermitted(consignmentId)(functionMock)(FakeRequest()).futureValue

      verify(functionMock, times(0)).apply(any[Request[AnyContent]])
      response.header.status must equal(303)
      response.header.headers("Location") must equal(s"/consignment/$consignmentId/transfer-agreement")
    }

    "render the upload in progress page if the upload is in progress" in {
      val consignmentId = UUID.randomUUID()

      val graphqlConfigurationMock = mockUploadPermittedGraphqlResponse(itac.Data(Option(itac.GetTransferAgreement(true))), gcs.Data(Option(GetConsignment(CurrentStatus(None, Option("InProgress"))))))
      val functionMock = mock[Request[AnyContent] => Result]

      val response = new MockValidatedActions(graphqlConfigurationMock).uploadPermitted(consignmentId)(functionMock)(FakeRequest())

      verify(functionMock, times(0)).apply(any[Request[AnyContent]])
      playStatus(response) must equal(OK)
      contentAsString(response) must include("Uploading records")
    }
  }
}
