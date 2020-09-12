## LaminarWebsocket

Typical usage is as such:


```scala mdoc:compile-only
import com.raquo.laminar.api.L._
import com.velvetbeam.laminar.websocket._

sealed trait Protocol
case object Ping extends Protocol
case object Pong extends Protocol

case class UnknownMessage(msg: String) extends RuntimeException(s"Unknown message: $msg")

val fromString: String => Either[UnknownMessage, Protocol] = s => s.trim.toLowerCase match {
    case "ping" => Right(Ping)
    case "pong" => Right(Pong)
    case other  => Left(UnknownMessage(other))
}

implicit val protocolCodec = StringCodec[Protocol, UnknownMessage](fromString, p => p.toString.toLowerCase)

val socket = new LaminarWebsocket[Protocol]("ws://localhost:1111/websocket-endpoint")

val myDiv = div("My purpose is to demonstrate websockets :(")

val messageBus = socket.bind(myDiv)

// rendering messages received from the server
val $fromServer = messageBus.events.collect {
    case Data(Ping)           => span("ping!")
    case Data(Pong)           => span("pong!")
    case ProtocolError(ex)    => span(ex.toString)
}

val clientPinger = EventStream.periodic(1000)

myDiv.amend(
    child <-- $fromServer,
    // sending messages to the server
    clientPinger --> Observer.apply[Any](_ => messageBus.send(Ping))
)
```
