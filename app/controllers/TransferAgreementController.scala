package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddTransferAgreement.AddTransferAgreement
import graphql.codegen.types.AddTransferAgreementInput
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementController @Inject()(val controllerComponents: SecurityComponents,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val keycloakConfiguration: KeycloakConfiguration)
                                           (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private val secureAction = Secure("OidcClient")
  private val options: Seq[(String, String)] = Seq("Yes" -> "true", "No" -> "false")
  private val addTransferAgreementClient = graphqlConfiguration.getClient[AddTransferAgreement.Data, AddTransferAgreement.Variables]()

  val transferAgreementForm = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("Must answer yes", b => b),
      "crownCopyright" -> boolean
        .verifying("Must answer yes", b => b),
      "english" -> boolean
        .verifying("Must answer yes", b => b),
      "digital" -> boolean
        .verifying("Must answer yes", b => b),
      "droAppraisalselection" -> boolean
        .verifying("Must answer yes", b => b),
      "droSensitivity" -> boolean
        .verifying("Must answer yes", b => b)
    )(TransferAgreementData.apply)(TransferAgreementData.unapply)
  )

  def transferAgreement(consignmentId: Long): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.transferAgreement(consignmentId, transferAgreementForm, options))
  }

  def transferAgreementSubmit(consignmentId: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementData] => Future[Result] = { formWithErrors: Form[TransferAgreementData] =>
      Future.successful(BadRequest(views.html.transferAgreement(consignmentId, formWithErrors, options)))
    }

    val successFunction: TransferAgreementData => Future[Result] = { formData: TransferAgreementData =>
      val addTransferAgreementInput: AddTransferAgreementInput = AddTransferAgreementInput(consignmentId,
        Some(formData.publicRecord),
        Some(formData.crownCopyright),
        Some(formData.english),
        Some(formData.digital),
        Some(formData.droAppraisalselection),
        Some(formData.droSensitivity)
      )

      val variables: AddTransferAgreement.Variables = AddTransferAgreement.Variables(addTransferAgreementInput)

      addTransferAgreementClient.getResult(request.token.bearerAccessToken, AddTransferAgreement.document, Some(variables)).map(data => {
        if(data.data.isDefined) {
          Redirect(routes.DashboardController.dashboard())
        } else {
          BadRequest(views.html.error(data.errors.map(e => e.message).mkString))
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
                                 droAppraisalselection: Boolean,
                                 droSensitivity: Boolean)
