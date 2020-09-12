package example.frontend

import com.raquo.laminar.api.L._
import com.velvetbeam.laminar.websocket._
import example.shared.Mode
import example.shared.Protocol
import io.circe.Printer
import org.scalajs.dom

object Client {

  case class WebsocketApp private (node: Element)

  object WebsocketApp {
    def apply(url: String) = {
      implicit val codec = new StringCodec[Protocol, Throwable] {
        override def fromString(msg: String): Either[Throwable, Protocol] = {
          io.circe.parser
            .parse(msg)
            .flatMap(Protocol.protocolDecoder.decodeJson)
        }

        override def toString(msg: Protocol): String =
          Protocol.protocolEncoder(msg).printWith(Printer.noSpaces)
      }

      val $acknowledgedConsumer = Var(Option.empty[String])

      val container = div(
        "Websocket app example",
        children <-- $acknowledgedConsumer.signal.map(
          _.map(consumerId =>
            div(s"Acknowledged by the server, I am now known as $consumerId")
          ).toVector
        )
      )

      import example.shared.Protocol._

      val socket =
        new LaminarWebsocket[example.shared.Protocol](url)

      val messageBus = socket.bind(container)

      val $fromServer = messageBus.events.collect {
        case Data(GeneratedString(str)) => div(s"Received random string: $str")
        case Data(GeneratedUUID(uuid))  => div(s"Received uuid: $uuid")
        case Data(ServerAcknowledge(name)) =>
          $acknowledgedConsumer.set(Some(name)); emptyNode
        case ProtocolError(error) =>
          div(color := "red", strong(s"Received protocol error: $error"))
        case cnn @ (ConnectionOpened | ConnectionClosed) =>
          div(s"Connection status: $cnn")
      }

      val (streamStopped, toggleStopped) = cycler(false, true)

      val startStopButton = button(
        child.text <-- streamStopped.signal.map {
          case false => "Stop"
          case true  => "Start"
        },
        onClick --> toggleStopped,
        streamStopped.changes --> {
          case false => messageBus.send(Start)
          case true  => messageBus.send(Stop)
        }
      )

      val (cycleModes, toggleMode) = cycler(Mode.Uuids, Mode.Strings)

      val modeSwitchMode = button(
        child.text <-- cycleModes.map {
          case Mode.Uuids   => "Switch to random strings generation"
          case Mode.Strings => "Switch to random UUID generation"
        },
        onClick --> toggleMode,
        cycleModes.changes --> {
          case Mode.Uuids   => messageBus.send(StartGeneratingUUIDs)
          case Mode.Strings => messageBus.send(StartGeneratingStrings)
        }
      )

      container.amend(child <-- $fromServer)

      new WebsocketApp(div(container, startStopButton, modeSwitchMode))
    }
  }

  def cycler[T](first: T, rest: T*): (Signal[T], Observer[Any]) = {
    val current = Var(0)
    val len = rest.length + 1
    val vec = first +: Vector(rest: _*)
    val toggle = Observer[Any](_ => {
      println(current.now())
      println(len)
      current.update(cur => (cur + 1) % len); current.now()
    })

    (current.signal.map(vec), toggle)
  }

  def main(args: Array[String]): Unit = {
    val app = WebsocketApp("ws://localhost:9000/ws/strings")

    documentEvents.onDomContentLoaded.foreach { _ =>
      render(dom.document.getElementById("appContainer"), app.node)
    }(unsafeWindowOwner)
  }
}
