package controllers

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.libs.typedmap.{TypedEntry, TypedKey, TypedMap}
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test._

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting with MockitoSugar {

  "HomeController GET" should {

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val idToken = TypedEntry(TypedKey[String]("id_token"), "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfR1hGZ3Z0T1E1MFN2ekVpS2JnUXZoTHE3RHNNNzc4SDZFOFdQNWx3bGo0In0.eyJqdGkiOiIxNWRjMjE2YS0xODM3LTQ0NzQtODExZC01NGNkNTAwMmM1NzEiLCJleHAiOjE1Nzg1NTc5NzMsIm5iZiI6MCwiaWF0IjoxNTc4NTU3NjczLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvdGRyIiwiYXVkIjoidGRyIiwic3ViIjoiY2Y3NWFmNTgtNTA1NS00NGM5LTg2ZTYtYzkzYWU2ZmUxMzRjIiwidHlwIjoiSUQiLCJhenAiOiJ0ZHIiLCJhdXRoX3RpbWUiOjE1Nzg1NTc2NzMsInNlc3Npb25fc3RhdGUiOiIwYTU3NjAwZS04YTk1LTRhNzEtODlmMi0wMDc2NTcxMmUxOWYiLCJhY3IiOiIxIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJ1c2VyIn0.YNKfg2ZU-kr6TPQH91gfT0SQTxWKdV7mhhu1if4EqXptFrT-9f6qwnOaYXqoVezxgH1gHioMMEZBh6psNEAUDcxU2e2WgvM9QLQsm1EXZkWZRgwBFeZWYLohsuqNt0yAvnC0LU2IiuPEu2E3vVv7Ndi8S7c3lFZmIbKGLj5p_krNJOaj1wSF1_xGB6TS8zCd2qAD__guG-qkf1C_VVSopR4zMykJDlxZQjoNm2VWhMUBJrdjrhZ4TQvngwnX1ql6jFiO2i7aLu9PxB37KSv12l1I_uSET6B7VdngPdS1o9Tm-7mwEskm16tZx6qr8lGm5hEwFDBKMSdntn42v1mrOw")
      val id = TypedEntry(TypedKey[Long]("Id"), 1L)
      val t: TypedMap = TypedMap(idToken, id)
      val home = controller.index().apply(FakeRequest(GET, "/").withSession(("a" -> "b")).withCookies(Cookie("a", "b")))
      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }
//
//    "render the index page from the router" in {
//      val request = FakeRequest(GET, "/")
//      val home = route(app, request).get
//
//      status(home) mustBe OK
//      contentType(home) mustBe Some("text/html")
//      contentAsString(home) must include ("Welcome to Play")
//    }
  }
}
