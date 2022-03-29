package configuration

import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

object GraphQLBackend {
  implicit val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
}
