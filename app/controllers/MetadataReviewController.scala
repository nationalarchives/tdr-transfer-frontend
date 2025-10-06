package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentsForMetadataReview.getConsignmentsForMetadataReview.GetConsignmentsForMetadataReview
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService
import services.Statuses.MetadataReviewType

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID
import javax.inject.Inject

class MetadataReviewController @Inject() (
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService
) extends TokenSecurity {

  def metadataReviews(): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    for {
      consignments <- consignmentService.getConsignmentsForReview(request.token.bearerAccessToken)
    } yield {
      Ok(views.html.tna.metadataReviews(consignments.map(toMetadataReviewRequest).sortBy(_.submittedDate)(Ordering[ZonedDateTime])))
    }
  }

  private def toMetadataReviewRequest(consignment: GetConsignmentsForMetadataReview): MetadataReviewRequest = {
    val formatter = DateTimeFormatter.ofPattern("d'%s' MMMM yyyy")
    val status = consignment.consignmentStatuses.find(_.statusType == MetadataReviewType.id)
    val submittedDate = if (status.exists(_.modifiedDatetime.nonEmpty)) {
      status.map(_.modifiedDatetime.get)
    } else {
      status.map(_.createdDatetime)
    }
    MetadataReviewRequest(
      consignmentId = consignment.consignmentid.get,
      consignmentRef = consignment.consignmentReference,
      body = consignment.transferringBodyName.getOrElse(""),
      series = consignment.seriesName.getOrElse(""),
      submittedDate = submittedDate.get,
      submittedDateHtml = formatDate(formatter, submittedDate.get)
    )
  }

  private def formatDate(formatter: DateTimeFormatter, dateTime: ZonedDateTime): String = {
    val dayOfMonth = dateTime.getDayOfMonth
    val date = formatter.format(dateTime).format(getDaySuffix(dayOfMonth))
    val noOfDays = LocalDate.now().toEpochDay - dateTime.toLocalDate.toEpochDay
    val days = noOfDays match {
      case 0   => "Today"
      case 1   => "Yesterday"
      case day => s"$day days ago"
    }
    s"$date <br> ($days)"
  }

  private def getDaySuffix(day: Int): String = day match {
    case 1 | 21 | 31 => "st"
    case 2 | 22      => "nd"
    case 3 | 23      => "rd"
    case _           => "th"
  }
}

case class MetadataReviewRequest(consignmentId: UUID, consignmentRef: String, body: String, series: String, submittedDate: ZonedDateTime, submittedDateHtml: String)
