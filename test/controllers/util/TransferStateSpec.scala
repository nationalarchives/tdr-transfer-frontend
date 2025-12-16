package controllers.util

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import controllers.{FileChecksProgress, TransferProgress}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableFor3
import services.ConsignmentStatusService
import services.Statuses._
import testUtils.FrontEndTestHelper

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransferStateSpec extends FrontEndTestHelper {
  val statusService: ConsignmentStatusService = mock[ConsignmentStatusService]
  val state = new TransferState(statusService)
  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  private def setStatus(statusType: StatusType, statusValue: StatusValue) =
    ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), statusType.id, statusValue.value, someDateTime, None)

  private val fileChecksAllSucceededStatuses = clientChecksStatuses.map(setStatus(_, CompletedValue)).toList
  private val fileChecksWithIssuesStatuses = clientChecksStatuses.map(setStatus(_, CompletedWithIssuesValue)).toList
  private val fileChecksInProgress = clientChecksStatuses.map(setStatus(_, InProgressValue)).toList

  val fileChecksStates: TableFor3[String, List[ConsignmentStatuses], FileChecksProgress] = Table(
    ("Description", "Consignment Statuses", "Expected File Checks Progress"),
    ("file checks in progress", fileChecksInProgress, FileChecksProgress(allChecksSucceeded = false, allChecksCompleted = false)),
    ("all file checks successfully completed", fileChecksAllSucceededStatuses, FileChecksProgress(allChecksSucceeded = true, allChecksCompleted = true)),
    ("file checks completed", List(setStatus(ClientChecksType, CompletedValue)), FileChecksProgress(allChecksSucceeded = false, allChecksCompleted = true)),
    ("file checks completed with issues", fileChecksWithIssuesStatuses, FileChecksProgress(allChecksSucceeded = false, allChecksCompleted = true))
  )

  forAll(fileChecksStates) { (description, statuses, expectedState) => {
      "getFileChecksState" should {
        s"return the correct file checks state for $description" in {
          when(statusService.getConsignmentStatuses(any[UUID], any[BearerAccessToken])).thenReturn(Future(statuses))
          val result = state.getFileChecksProgress(UUID.randomUUID(), mock[BearerAccessToken]).futureValue
          result shouldEqual expectedState
        }
      }
    }
  }

  val transferStates: TableFor3[String, List[ConsignmentStatuses], TransferProgress] = Table(
    ("Description", "Consignment Statuses","Expected Transfer State"),
    ("all file checks completed and export triggered", fileChecksAllSucceededStatuses :+ setStatus(ExportType, CompletedValue),
      TransferProgress(CompletedValue.value, CompletedValue.value)),
    ("all file checks completed and export not triggered", fileChecksAllSucceededStatuses, TransferProgress(CompletedValue.value)),
    ("file checks not completed", fileChecksInProgress, TransferProgress(InProgressValue.value)),
    ("file checks completed with issues", fileChecksWithIssuesStatuses, TransferProgress(CompletedWithIssuesValue.value))
  )

  forAll(transferStates) { (description, fileCheckStatuses, expectedState) => {
      "getTransferState" should {
        s"return the correct transfer progress for $description" in {
          when(statusService.getConsignmentStatuses(any[UUID], any[BearerAccessToken])).thenReturn(Future(fileCheckStatuses))
          val result = state.getTransferProgress(UUID.randomUUID(), mock[BearerAccessToken]).futureValue
          result shouldEqual expectedState
        }
      }
    }
  }
}
