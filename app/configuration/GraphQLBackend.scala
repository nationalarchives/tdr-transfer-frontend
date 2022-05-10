package configuration

import sttp.client3.{HttpClientFutureBackend, SttpBackend}

import scala.concurrent.Future

object GraphQLBackend {
  implicit val backend: SttpBackend[Future, Any] = HttpClientFutureBackend()
}
