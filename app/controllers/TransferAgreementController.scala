package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddTransferAgreement.AddTransferAgreement
import graphql.codegen.GetConsignment
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.types.AddTransferAgreementInput
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.data
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader, Result}
import services.GetConsignmentService
import sttp.client.SttpClientException

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementController @Inject()(val controllerComponents: SecurityComponents,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val getConsignmentService: GetConsignmentService,
                                            langs: Langs)
                                           (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private val options: Seq[(String, String)] = Seq("Yes" -> "true", "No" -> "false")
  private val addTransferAgreementClient = graphqlConfiguration.getClient[AddTransferAgreement.Data, AddTransferAgreement.Variables]()
  implicit val language: Lang = langs.availables.head

  val transferAgreementForm = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying(messagesApi("transferAgreement.publicRecord.error"), b => b),
      "crownCopyright" -> boolean
        .verifying(messagesApi("transferAgreement.crownCopyright.error"), b => b),
      "english" -> boolean
        .verifying(messagesApi("transferAgreement.english.error"), b => b),
      "digital" -> boolean
        .verifying(messagesApi("transferAgreement.digital.error"), b => b),
      "droAppraisalSelection" -> boolean
        .verifying(messagesApi("transferAgreement.droAppraisalSelection.error"), b => b),
      "droSensitivity" -> boolean
        .verifying(messagesApi("transferAgreement.droSensitivity.error"), b => b)
    )(TransferAgreementData.apply)(TransferAgreementData.unapply)
  )

  def transferAgreement(consignmentId: Long): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val consignmentExists = getConsignmentService.consignmentExists(consignmentId, request.token.bearerAccessToken)
    consignmentExists.map {
      case true => Ok(views.html.transferAgreement(consignmentId, transferAgreementForm, options))
      case false => NotFound(views.html.notFoundError("The consignment you are trying to access does not exist"))
    }.recover {
      case cause => {
        BadRequest(views.html.error(cause.getMessage))
      }
    }
  }

  def transferAgreementSubmit(consignmentId: Long): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementData] => Future[Result] = { formWithErrors: Form[TransferAgreementData] =>
      Future.successful(BadRequest(views.html.transferAgreement(consignmentId, formWithErrors, options)))
    }

    val successFunction: TransferAgreementData => Future[Result] = { formData: TransferAgreementData =>
      val addTransferAgreementInput: AddTransferAgreementInput = AddTransferAgreementInput(consignmentId,
        Some(formData.publicRecord),
        Some(formData.crownCopyright),
        Some(formData.english),
        Some(formData.digital),
        Some(formData.droAppraisalSelection),
        Some(formData.droSensitivity)
      )

      val variables: AddTransferAgreement.Variables = AddTransferAgreement.Variables(addTransferAgreementInput)

      addTransferAgreementClient.getResult(request.token.bearerAccessToken, AddTransferAgreement.document, Some(variables)).map(data => {
        if(data.data.isDefined) {
          Redirect(routes.UploadController.uploadPage(consignmentId))
        } else {
          InternalServerError(views.html.error(data.errors.map(e => e.message).mkString))
        }
      })
    }

    val formValidationResult: Form[TransferAgreementData] = transferAgreementForm.bindFromRequest

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferAgreementData(publicRecord: Boolean,
                                 crownCopyright: Boolean,
                                 english: Boolean,
                                 digital: Boolean,
                                 droAppraisalSelection: Boolean,
                                 droSensitivity: Boolean)
