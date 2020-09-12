package com.velvetbeam
package laminar.websocket

import scala.util.Try

import com.raquo.airstream.ownership.DynamicSubscription
import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Binder
import com.raquo.laminar.nodes.ReactiveElement
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.MessageEvent
import org.scalajs.dom.raw.WebSocket

trait BinderWithStartStop[-El <: ReactiveElement.Base] extends Binder[El] {

  protected var subscribed = false

  def doStart(): Unit
  def doStop(): Unit

  def stop(): Unit = {
    if (subscribed) {
      doStop()
    }
  }

  def start(): Unit = {
    if (subscribed) {
      doStart()
    }
  }
  override def bind(element: El): DynamicSubscription = {
    ReactiveElement.bindSubscription(element) { ctx =>
      subscribed = true
      start()

      new Subscription(ctx.owner, cleanup = () => {
        stop()
        subscribed = false
      })
    }
  }

}

trait StringCodec[P, +E] {
  def fromString(msg: String): Either[E, P]
  def toString(msg: P): String
}

object StringCodec {

  def apply[T, E](from: String => Either[E, T], to: T => String) =
    new StringCodec[T, E] {
      override def fromString(msg: String): Either[E, T] = from(msg)

      override def toString(msg: T): String = to(msg)

    }

  implicit val stringProtocol: StringCodec[String, Nothing] =
    StringCodec[String, Nothing](s => Right(s), identity)

  implicit val intProtocol: StringCodec[Int, Throwable] =
    StringCodec[Int, Throwable](s => Try(s.toInt).toEither, _.toString())

}

sealed trait WebSocketMessage[+T, +E]
case object ConnectionOpened extends WebSocketMessage[Nothing, Nothing]
case object ConnectionClosed extends WebSocketMessage[Nothing, Nothing]
final case class Data[T](message: T) extends WebSocketMessage[T, Nothing]
final case class ProtocolError[T, E](error: E) extends WebSocketMessage[T, E]

trait WebsocketEventbus[T, +E] {
  def events: EventStream[WebSocketMessage[T, E]]
  def send[T1 <: T](t: T1): Unit
}

class LaminarWebsocket[T](url: String)(
    implicit prot: StringCodec[T, Throwable]
) {

  def bind(el: Element): WebsocketEventbus[T, Throwable] = {
    val serverToClient = new EventBus[WebSocketMessage[T, Throwable]]
    val clientToServer = new EventBus[T]

    val bus = new WebsocketEventbus[T, Throwable] {
      override val events = serverToClient.events
      override def send[T1 <: T](t: T1): Unit = clientToServer.writer.onNext(t)
    }

    var websocket: Option[WebSocket] = None

    val binder = new BinderWithStartStop[Element] {

      override def doStart(): Unit = {

        val sock = new WebSocket(url)
        websocket = Option(sock)

        sock.addEventListener[Event](
          "message", {
            case event: MessageEvent =>
              serverToClient.writer.onNext(
                prot.fromString(event.data.asInstanceOf[String]) match {
                  case Left(error)  => ProtocolError(error)
                  case Right(value) => Data(value)
                }
              )
          }
        )

        sock.addEventListener[Event](
          "close", {
            case _ =>
              serverToClient.writer.onNext(
                ConnectionClosed
              )
          }
        )

        sock.addEventListener[Event](
          "open", {
            case _ =>
              serverToClient.writer.onNext(
                ConnectionOpened
              )
          }
        )

      }

      override def doStop(): Unit = {
        websocket.foreach { sock => sock.close() }
        websocket = None
      }
    }

    val sender: T => Unit = msg => websocket.foreach(_.send(prot.toString(msg)))

    el.amend(
      binder,
      clientToServer.events --> sender
    )

    bus
  }
}
