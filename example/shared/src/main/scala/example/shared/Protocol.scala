package example.shared

import java.{util => ju}

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.syntax._

sealed trait Mode
object Mode {
  case object Uuids extends Mode
  case object Strings extends Mode
}

sealed trait Protocol

object Protocol {
  case class GeneratedUUID(uuid: ju.UUID) extends Protocol
  case class GeneratedString(str: String) extends Protocol
  case object StartGeneratingUUIDs extends Protocol
  case object StartGeneratingStrings extends Protocol
  case object Stop extends Protocol
  case object Start extends Protocol
  case class ServerAcknowledge(name: String) extends Protocol

  import io.circe.generic.auto._

  implicit val protocolDecoder: Decoder[Protocol] = new Decoder[Protocol] {

    def apply(cursor: HCursor) =
      cursor.downField("type").as[String].flatMap {
        case "GeneratedUUID"          => cursor.as[GeneratedUUID]
        case "GeneratedString"        => cursor.as[GeneratedString]
        case "ServerAcknowledge"      => cursor.as[ServerAcknowledge]
        case "StartGeneratingUUIDs"   => Right(StartGeneratingUUIDs)
        case "StartGeneratingStrings" => Right(StartGeneratingStrings)
        case "Stop"                   => Right(Stop)
        case "Start"                  => Right(Start)

      }
  }

  implicit val protocolEncoder: Encoder[Protocol] = Encoder.instance {
    case StartGeneratingUUIDs   => Json.obj("type" := "StartGeneratingUUIDs")
    case StartGeneratingStrings => Json.obj("type" := "StartGeneratingStrings")
    case Stop                   => Json.obj("type" := "Stop")
    case Start                  => Json.obj("type" := "Start")
    case gu: GeneratedUUID =>
      gu.asJson.deepMerge(Json.obj("type" := "GeneratedUUID"))
    case gs: GeneratedString =>
      gs.asJson.deepMerge(Json.obj("type" := "GeneratedString"))
    case sa: ServerAcknowledge =>
      sa.asJson.deepMerge(Json.obj("type" := "ServerAcknowledge"))
  }

  implicit val protocolCodec: Codec[Protocol] = Codec[Protocol]

  def fromString(s: String) =
    io.circe.parser.parse(s).flatMap(protocolDecoder.decodeJson)

}
