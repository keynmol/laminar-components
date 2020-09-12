package example.frontend

import com.raquo.laminar.api.L._
import org.scalajs.dom
import com.velvetbeam.laminar.websocket._

object Client {

  case class App private (node: Element, stream: EventStream[String])

  object App {
    def apply(url: String) = {
      val container = div("I am based on websocket!")

      val socket =
        new LaminarWebsocket[String](url)

      val messageBus = socket.bind(container)

      val $periodicWriter = EventStream.periodic(500).map { number =>
        messageBus.send(number.toString)
      }

      val $fromServer = messageBus.events.collect {
        case Data(message) => message
      }

      val $streamStopped = Var(false)

      val b = button(
        child.text <-- $streamStopped.signal.map {
          case false => "Stop"
          case true  => "Start"
        },
        onClick.mapTo(! $streamStopped.now()) --> $streamStopped.writer,
        $streamStopped.signal.changes --> {
          case false => messageBus.send("start")
          case true  => messageBus.send("stop")
        },
        $periodicWriter --> Observer.empty
      )

      container.amend(child.text <-- $fromServer)

      new App(div(container, b), $fromServer)
    }
  }

  def main(args: Array[String]): Unit = {
    val app = App("ws://localhost:9000/ws/strings")

    documentEvents.onDomContentLoaded.foreach { _ =>
      render(dom.document.getElementById("appContainer"), app.node)
    }(unsafeWindowOwner)
  }
}
