package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import graphql.codegen.types.ConsignmentFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class ViewHistoryController @Inject() (val consignmentService: ConsignmentService, val keycloakConfiguration: KeycloakConfiguration, val controllerComponents: SecurityComponents)
    extends TokenSecurity {
  def viewConsignments(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val consignmentFilters = ConsignmentFilters(Some(request.token.userId), None)
    for {
      consignmentHistory <- consignmentService.getConsignments(consignmentFilters, request.token.bearerAccessToken)
      consignments = consignmentHistory.edges match {
        case Some(edges) => edges.flatMap(createView)
        case None        => Nil
      }
    } yield {
      Ok(views.html.viewHistory(consignments, request.token.name, request.token.email))
    }
  }

  private def createView(edges: Option[Edges]): Option[ConsignmentHistory] = {

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    edges.map(edges =>
      ConsignmentHistory(
        edges.node.consignmentid,
        edges.node.consignmentReference,
        getConsignmentStatus(edges.node.currentStatus),
        edges.node.exportDatetime.map(_.format(formatter)).getOrElse(""),
        edges.node.createdDatetime.map(_.format(formatter)).getOrElse(""),
        edges.node.totalFiles
      )
    )
  }

  private def getConsignmentStatus(currentStatus: CurrentStatus): String = {
    if (currentStatus.`export`.contains("Completed")) "Exported" else "In Progress"
  }
}

case class ConsignmentHistory(consignmentId: Option[UUID], reference: String, status: String, dateOfExport: String, dateStarted: String, numberOfFiles: Int)
