package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent._

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {
      val controller = new HomeController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      checkContent(home)
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      checkContent(home)
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request).get

      status(home) mustBe OK
      checkContent(home)
    }
  }

  private def checkContent(result: Future[Result]): Unit = {
    contentType(result) mustBe Some("text/html")
    contentAsString(result) must include ("Transfer Digital Records")
    contentAsString(result) must include ("Welcome")
  }
}
