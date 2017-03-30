package reactor.core.scala.publisher

import reactor.core.Receiver
import reactor.core.publisher.{ConnectableFlux => JConnectableFlux}

class ConnectableFlux[T]private (private val jConnectableFlux: JConnectableFlux[T]) extends Flux[T](jConnectableFlux) with Receiver {

  /**
    * Connects to the upstream source when the first [[org.reactivestreams.Subscriber]] subscribes and disconnects
    * when all Subscribers cancelled or the upstream source completed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/refCount.png" alt="">
    *
    * @return a reference counting [[Flux]]
    */
  final def refCount() = Flux(jConnectableFlux.refCount())

  override def upstream(): AnyRef = jConnectableFlux.upstream()
}

object ConnectableFlux {
  def apply[T](jConnectableFlux: JConnectableFlux[T]) = new ConnectableFlux[T](jConnectableFlux)
}
