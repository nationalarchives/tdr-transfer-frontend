package controllers

import auth.UnprotectedPageController
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, list, mapping, nonEmptyText, seq, text}
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}

import javax.inject.{Inject, Singleton}

@Singleton
class NavigationController @Inject()(securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {

  def navigationForm(implicit messages: Messages): Form[NodesFormData] = Form(
    mapping(
      "nodes" -> seq(
        mapping(
          "nodeIdStr" -> nonEmptyText,
          "displayName" -> nonEmptyText,
          "isSelected" -> boolean
        )(NodesToDisplay.apply)(NodesToDisplay.unapply)
    ),
   "previouslySelected" -> text,
   "selected" -> list(text)
  )(NodesFormData.apply)(NodesFormData.unapply))

  def navigation(previouslySelectedIds: String = ""): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    //Ids would be the details of child files/folders for the viewed folder via a call to the API
    val ids = Seq(1, 2, 3, 4 ,5)
    Ok(views.html.navigation(navigationForm.fill(NodesFormData(ids.map(id => NodesToDisplay(id.toString, s"ID $id")), previouslySelectedIds, List()))))
  }

  def submit(): Action[AnyContent] = Action { implicit request: Request[AnyContent] => {
      val ids = Seq(6, 7, 8, 9)
      val errorFunction: Form[NodesFormData] => Result = { formWithErrors: Form[NodesFormData] =>
        Ok(views.html.navigation(navigationForm.fill(NodesFormData(ids.map(id => NodesToDisplay(id.toString, s"ID $id")), "", List()))))
      }

      val successFunction: NodesFormData => Result = { formData: NodesFormData =>
        val selected: String = formData.selected.mkString(",")
        val previouslySelectedIds: String = formData.previouslySelected + selected

        Ok(views.html.navigation(navigationForm.fill(NodesFormData(ids.map(id => NodesToDisplay(id.toString, s"ID $id")), previouslySelectedIds, List()))))
      }

      val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
  }
}

case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay], previouslySelected: String, selected: List[String])
case class NodesToDisplay(nodeIdStr: String, displayName: String, isSelected: Boolean = false)
