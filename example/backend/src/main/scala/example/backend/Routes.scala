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

class Routes(blocker: Blocker, frontendJS: String)(
    implicit timer: Timer[IO],
    cs: ContextShift[IO]
) {
  def routes = HttpRoutes.of[IO] {
    case GET -> Root / "ws" / "strings" =>
      for {
        mode <- SignallingRef.apply[IO, Boolean](true)

        toClient: fs2.Stream[IO, WebSocketFrame] = fs2.Stream
          .repeatEval(IO(s"Hello! ${java.util.UUID.randomUUID().toString}"))
          .zip(mode.continuous)
          .filter(_._2)
          .map(_._1)
          .metered(500.millis)
          .map(s => Text(s))

        fromClient: Pipe[IO, WebSocketFrame, Unit] = stream =>
          stream
            .evalTap(s => IO(println(s)))
            .collect { case txt: Text => txt.str }
            .evalMap {
              case "stop"  => mode.set(false)
              case "start" => mode.set(true)
              case _       => IO.unit
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
