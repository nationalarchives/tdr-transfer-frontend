package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import graphql.codegen.AddMultipleFileStatuses.{addMultipleFileStatuses => amfs}
import graphql.codegen.types.{AddFileStatusInput, AddMultipleFileStatusesInput}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar.mock
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FileStatusServiceSpec extends AnyFlatSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val graphQlConfig = mock[GraphQLConfiguration]
  private val token = new BearerAccessToken("some-token")

  "addFileStatus" should "add a file status" in {
    val input = AddMultipleFileStatusesInput(List(AddFileStatusInput(UUID.randomUUID(), "Upload", "Success")))
    val graphQlClientForAddFileStatus = mock[GraphQLClient[amfs.Data, amfs.Variables]]
    when(graphQlConfig.getClient[amfs.Data, amfs.Variables]())
      .thenReturn(graphQlClientForAddFileStatus)

    val graphQlResponse =
      GraphQlResponse(Some(amfs.Data(List(amfs.AddMultipleFileStatuses(input.statuses.head.fileId, "Upload", "Success")))), Nil)
    when(graphQlClientForAddFileStatus.getResult(token, amfs.document, Some(amfs.Variables(input))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new FileStatusService(graphQlConfig).addFileStatus(input, token).futureValue
    val testItem = response.head
    testItem.fileId should equal(input.statuses.head.fileId)
    testItem.statusType should equal(input.statuses.head.statusType)
    testItem.statusValue should equal(input.statuses.head.statusValue)
  }

  "addFileStatus" should "return an error when the API has an error" in {
    val preInput = AddFileStatusInput(UUID.randomUUID(), "Upload", "Success")
    val input = AddMultipleFileStatusesInput(List(preInput))
    val graphQlClientForAddFileStatus = mock[GraphQLClient[amfs.Data, amfs.Variables]]
    when(graphQlConfig.getClient[amfs.Data, amfs.Variables]())
      .thenReturn(graphQlClientForAddFileStatus)

    when(graphQlClientForAddFileStatus.getResult(token, amfs.document, Some(amfs.Variables(input))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    val results = new FileStatusService(graphQlConfig).addFileStatus(input, token)
    results.failed.futureValue shouldBe a[HttpError]
  }
}
