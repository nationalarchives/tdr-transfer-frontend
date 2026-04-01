package controllers

import java.util.Base64
import spray.json._

import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime

case class Payload(sub: String, name: String, iat: Int, exp: Int) // exp is the expiration date

object ExpirationDateTest extends DefaultJsonProtocol  {

  implicit val payloadJsonFormat: RootJsonFormat[Payload] = jsonFormat4(Payload) // defines a contract to deserialize the JSON object

  def batToExpDate(batValue: String): Instant = {
    val jwtTokenPayload = batValue.split('.')(1)
    val millis: Int = new String(Base64.getDecoder.decode(jwtTokenPayload)).parseJson.convertTo[Payload].exp
    val result = Instant.ofEpochMilli(millis.toLong * 1000)
    val fw = new FileWriter("test.txt", true)
    try {
      fw.write(LocalDateTime.now() + " " + result.toString + " " + batValue + "\n")
    }
    finally fw.close()
    result
  }

}