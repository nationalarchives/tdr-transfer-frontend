package services

import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import services.ConsignmentStatusService.statusValue
import services.Statuses._

import java.util.UUID

trait StatusAware {
  val pageStatus: StatusType with PageCorrelating

  def statusAwareRedirect(consignmentId: UUID, recordedStatuses: Seq[ConsignmentStatuses]): Option[Result] = {
    lazy val redirect = {
      val lastCompletedStatus = pageStatus.dependencies.toSeq
        .sortBy(SEQUENCE.indexOf)
        .reduce((latestObservedCompleted, current) => if (statusValue(current)(recordedStatuses) == CompletedValue) current else latestObservedCompleted)
      val (_, statusesAfterLastCompleted) = SEQUENCE.splitAt(SEQUENCE.indexOf(lastCompletedStatus) + 1)
      statusesAfterLastCompleted
        .collectFirst({ case status: PageCorrelating => status })
        .map(a => Redirect(a.baseRoute(consignmentId)))
    }
    Option.when(!shouldBeAccessible(recordedStatuses))(redirect).flatten
  }

  def shouldBeAccessible(recordedStatuses: Seq[ConsignmentStatuses]): Boolean = {
    println(s"Dependencies: ${pageStatus.dependencies}")
    pageStatus.dependencies.forall(dependency => statusValue(dependency)(recordedStatuses) == CompletedValue)
  }

  def shouldBeInteractable(recordedStatuses: Seq[ConsignmentStatuses]): Boolean = !recordedStatuses
    .filter { consignmentStatus => Seq(InProgressValue, CompletedValue).contains(StatusValue(consignmentStatus.value)) }
    .flatMap(cs => toStatusType(cs.statusType).dependencies)
    .contains(pageStatus)
}
