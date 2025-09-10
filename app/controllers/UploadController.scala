package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.{AddFileAndMetadataInput, AddMultipleFileStatusesInput, StartUploadInput}
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services._
import viewsapi.Caching.preventCaching

import java.net.URLEncoder
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val frontEndInfoConfiguration: ApplicationConfig,
    val consignmentService: ConsignmentService,
    val uploadService: UploadService,
    val fileStatusService: FileStatusService,
    val backendChecksService: BackendChecksService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def startUpload(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[StartUploadInput](body.toString).toOption
    }) match {
      case None        => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
      case Some(input) => uploadService.startUpload(input, request.token.bearerAccessToken).map(Ok(_))
    }
  }

  def saveClientMetadata(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddFileAndMetadataInput](body.toString()).toOption
    }) match {
      case Some(metadataInput) => uploadService.saveClientMetadata(metadataInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None                => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  def addFileStatus(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddMultipleFileStatusesInput](body.toString()).toOption
    }) match {
      case Some(addMultipleFileStatusesInput: AddMultipleFileStatusesInput) =>
        fileStatusService.addMultipleFileStatuses(addMultipleFileStatusesInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  def uploadPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType, UploadType)
      val transferAgreementStatus: Option[String] = statusesToValue.get(TransferAgreementType).flatten
      val uploadStatus: Option[String] = statusesToValue.get(UploadType).flatten
      val pageHeadingUpload = "Upload your records"
      val pageHeadingUploading = "Uploading your records"

      transferAgreementStatus match {
        case Some(CompletedValue.value) =>
          uploadStatus match {
            case Some(InProgressValue.value) | Some(CompletedWithIssuesValue.value) =>
              Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case Some(CompletedValue.value) =>
              Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case None =>
              Ok(views.html.standard.upload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading, frontEndInfoConfiguration.frontEndInfo, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
          }
        case Some(InProgressValue.value) =>
          Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId))
        case None =>
          Redirect(routes.TransferAgreementPart1Controller.transferAgreement(consignmentId))
        case _ =>
          throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
      }
    }
  }

  def judgmentUploadPage(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    def buildBackUrl: String = {
      if (frontEndInfoConfiguration.blockJudgmentPressSummaries) {
        routes.BeforeUploadingController.beforeUploading(consignmentId).url
      } else {

        val ncn = request.getQueryString("judgment_neutral_citation").filter(_.nonEmpty)
        val noNcn = request.getQueryString("judgment_no_neutral_citation").filter(_.nonEmpty)
        val reference = request.getQueryString("judgment_reference").filter(_.nonEmpty)
        val params = Seq(
          ncn.map(v => s"judgment_neutral_citation=${URLEncoder.encode(v, "UTF-8")}"),
          noNcn.map(v => s"judgment_no_neutral_citation=${URLEncoder.encode(v, "UTF-8")}"),
          reference.map(v => s"judgment_reference=${URLEncoder.encode(v, "UTF-8")}")
        ).flatten
        val base = routes.JudgmentNeutralCitationController.addNCN(consignmentId).url
        if (params.nonEmpty) s"$base?${params.mkString("&")}" else base
      }
    }

    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val uploadStatus: Option[String] = consignmentStatusService.getStatusValues(consignmentStatuses, UploadType).values.headOption.flatten
      val pageHeadingUpload = "Upload document"
      val pageHeadingUploading = "Uploading document"

      uploadStatus match {
        case Some(InProgressValue.value) | Some(CompletedWithIssuesValue.value) =>
          Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = true))
            .uncache()
        case Some(CompletedValue.value) =>
          Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = true))
            .uncache()
        case None =>
          val backUrl = buildBackUrl
          Ok(
            views.html.judgment
              .judgmentUpload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading, frontEndInfoConfiguration.frontEndInfo, request.token.name, backUrl)
          ).uncache()
        case _ =>
          throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
      }
    }
  }
}
