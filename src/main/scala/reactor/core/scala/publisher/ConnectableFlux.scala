package reactor.core.scala.publisher

import reactor.core.publisher.{ConnectableFlux => JConnectableFlux}
import reactor.core.{Disposable, Receiver}

class ConnectableFlux[T]private (private val jConnectableFlux: JConnectableFlux[T]) extends Flux[T](jConnectableFlux) with Receiver {

  /**
    * Connects this [[ConnectableFlux]] to the upstream source when the first [[org.reactivestreams.Subscriber]]
    * subscribes.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/autoconnect.png" alt="">
    *
    * @return a [[Flux]] that connects to the upstream source when the first [[org.reactivestreams.Subscriber]] subscribes
    */
  final def autoConnect() = Flux(jConnectableFlux.autoConnect())

  /**
    * Connects this [[ConnectableFlux]] to the upstream source when the specified amount of
    * [[org.reactivestreams.Subscriber]] subscribes.
    * <p>
    * Subscribing and immediately unsubscribing Subscribers also contribute the the subscription count
    * that triggers the connection.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/autoconnect.png" alt="">
    *
    * @param minSubscribers the minimum number of subscribers
    * @return a [[Flux]] that connects to the upstream source when the given amount of Subscribers subscribe
    */
  final def autoConnect(minSubscribers: Int) = Flux(jConnectableFlux.autoConnect(minSubscribers))

  /**
    * Connects this [[ConnectableFlux]] to the upstream source when the specified amount of
    * [[org.reactivestreams.Subscriber]] subscribes and calls the supplied consumer with a runnable that allows disconnecting.
    *
    * @param minSubscribers the minimum number of subscribers
    * @param cancelSupport  the consumer that will receive the [[Disposable]] that allows disconnecting
    *
    *                                                                  <p>
    *                                                                  <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/autoconnect.png" alt="">
    * @return a { @link Flux} that connects to the upstream source when the given amount of subscribers subscribed
    */
  final def autoConnect(minSubscribers: Int, cancelSupport: Disposable => Unit) = Flux(jConnectableFlux.autoConnect(minSubscribers, cancelSupport))

  /**
    * Connect this [[ConnectableFlux]] to its source and return a [[Runnable]] that
    * can be used for disconnecting.
    *
    * @return the [[Disposable]] that allows disconnecting the connection after.
    */
  final def connect(): Disposable = jConnectableFlux.connect()

  /**
    * Connects this [[ConnectableFlux]] to its source and sends a [[Disposable]] to a callback that
    * can be used for disconnecting.
    *
    * <p>The call should be idempotent in respect of connecting the first
    * and subsequent times. In addition the disconnection should be also tied
    * to a particular connection (so two different connection can't disconnect the other).
    *
    * @param cancelSupport the callback is called with a Cancellation instance that can
    *                      be called to disconnect the source, even synchronously.
    */
  final def connect(cancelSupport: Disposable => Unit): Unit = jConnectableFlux.connect(cancelSupport)

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

  /**
    * Connects to the upstream source when the given number of [[org.reactivestreams.Subscriber]] subscribes and disconnects
    * when all Subscribers cancelled or the upstream source completed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/refCount.png" alt="">
    *
    * @param minSubscribers the number of subscribers expected to subscribe before connection
    * @return a reference counting [[Flux]]
    */
  final def refCount(minSubscribers: Int) = Flux(jConnectableFlux.refCount(minSubscribers))

  override def upstream(): AnyRef = jConnectableFlux.upstream()
}

object ConnectableFlux {
  def apply[T](jConnectableFlux: JConnectableFlux[T]) = new ConnectableFlux[T](jConnectableFlux)
}
