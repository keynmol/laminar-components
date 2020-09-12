package example.backend

import scala.concurrent.duration._

import cats.effect._
import fs2.Pipe
import fs2.concurrent.SignallingRef
import org.http4s.HttpRoutes
import org.http4s.StaticFile
import org.http4s.dsl.io._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import example.shared.Protocol.GeneratedUUID

import example.shared.Protocol._
import example.shared.Protocol
import cats.effect.concurrent.Ref

import example.shared.Mode

import java.{util => ju}
import scala.{util => su}

import su.Random.{alphanumeric => randomStr}

class Routes(blocker: Blocker, frontendJS: String)(
    implicit timer: Timer[IO],
    cs: ContextShift[IO]
) {


  def routes = HttpRoutes.of[IO] {
    case GET -> Root / "ws" / "strings" =>
      for {
        keepProducing <- SignallingRef.apply[IO, Boolean](true)
        mode <- Ref.of[IO, Mode](Mode.Uuids)

        toClient: fs2.Stream[IO, WebSocketFrame] = fs2.Stream
          .repeatEval[IO, Protocol](
            mode.get.map {
              case Mode.Uuids   => GeneratedUUID(ju.UUID.randomUUID())
              case Mode.Strings => GeneratedString(randomStr.take(20).mkString)
            }
          )
          .zip(keepProducing.continuous)
          .filter(_._2)
          .map(_._1)
          .metered(1.second)
          .map(p => protocolEncoder(p).noSpaces)
          .debug()
          .map(s => Text(s.toString()))

        fromClient: Pipe[IO, WebSocketFrame, Unit] = stream =>
          stream
            .collect { case txt: Text => txt.str }
            .evalMap(frame => IO.fromEither(Protocol.fromString(frame)))
            .debug()
            .evalMap {
              case Stop                   => keepProducing.set(false)
              case Start                  => keepProducing.set(true)
              case StartGeneratingStrings => mode.set(Mode.Strings)
              case StartGeneratingUUIDs   => mode.set(Mode.Uuids)
              case _                      => IO.unit
            }

        builder <- WebSocketBuilder[IO].build(toClient, fromClient)
      } yield builder

    case request @ GET -> Root / "frontend" / "app.js" =>
      StaticFile
        .fromResource[IO](frontendJS, blocker, Some(request))
        .getOrElseF(NotFound())

    case request @ GET -> Root / "frontend" =>
      StaticFile
        .fromResource[IO]("index.html", blocker, Some(request))
        .getOrElseF(NotFound())

    case request @ GET -> Root / "assets" / path if staticFileAllowed(path) =>
      StaticFile
        .fromResource("/assets/" + path, blocker, Some(request))
        .getOrElseF(NotFound())
  }

  private def staticFileAllowed(path: String) =
    List(".gif", ".js", ".css", ".map", ".html", ".webm").exists(path.endsWith)

}
