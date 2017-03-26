package reactor.core.scala.publisher

import reactor.core.Receiver
import reactor.core.publisher.{ConnectableFlux => JConnectableFlux}

class ConnectableFlux[T]private (private val jConnectableFlux: JConnectableFlux[T]) extends Flux[T](jConnectableFlux) with Receiver {
  override def upstream(): AnyRef = jConnectableFlux.upstream()
}

object ConnectableFlux {
  def apply[T](jConnectableFlux: JConnectableFlux[T]) = new ConnectableFlux[T](jConnectableFlux)
}
