package reactor.core.scala.publisher

import reactor.core.Disposable
import reactor.core.publisher.ConnectableFlux
import reactor.core.scheduler.Scheduler

import scala.concurrent.duration.Duration

class ConnectableSFlux[T]private(private val connectableFlux: ConnectableFlux[T]) extends SFlux[T] {

  /**
    * Connects this [[ConnectableSFlux]] to the upstream source when the specified amount of
    * [[org.reactivestreams.Subscriber]] subscribes and calls the supplied consumer with a runnable that allows disconnecting.
    *
    * @param minSubscribers the minimum number of subscribers. Default is 1
    * @param cancelSupport  the consumer that will receive the [[Disposable]] that allows disconnecting. Default is do nothing
    *
    *                                                                  <p>
    *                                                                  <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/autoconnect.png" alt="">
    * @return a { @link Flux} that connects to the upstream source when the given amount of subscribers subscribed
    */
  final def autoConnect(minSubscribers: Int = 1, cancelSupport: Disposable => Unit = _ => ()): SFlux[T] = SFlux.fromPublisher(connectableFlux.autoConnect(minSubscribers, cancelSupport))

  /**
    * Connect this [[ConnectableSFlux]] to its source and return a [[Disposable]] that
    * can be used for disconnecting.
    *
    * @return the [[Disposable]] that allows disconnecting the connection after.
    */
  final def connect(): Disposable = connectableFlux.connect()

  /**
    * Connects this [[ConnectableSFlux]] to its source and sends a [[Disposable]] to a callback that
    * can be used for disconnecting.
    *
    * <p>The call should be idempotent in respect of connecting the first
    * and subsequent times. In addition the disconnection should be also tied
    * to a particular connection (so two different connection can't disconnect the other).
    *
    * @param cancelSupport the callback is called with a Cancellation instance that can
    *                      be called to disconnect the source, even synchronously.
    */
  final def connect(cancelSupport: Disposable => Unit): Unit = connectableFlux.connect(cancelSupport)

  /**
    * Connects to the upstream source when the first [[org.reactivestreams.Subscriber]] subscribes and disconnects
    * when all Subscribers cancelled or the upstream source completed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/refCount.png" alt="">
    *
    * @return a reference counting [[SFlux]]
    */
  final def refCount(): SFlux[T] = SFlux.fromPublisher(connectableFlux.refCount())

  /**
    * Connects to the upstream source when the given number of [[org.reactivestreams.Subscriber]] subscribes and disconnects
    * when all Subscribers cancelled or the upstream source completed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/refCount.png" alt="">
    *
    * @param minSubscribers the number of subscribers expected to subscribe before connection
    * @return a reference counting [[SFlux]]
    */
  final def refCount(minSubscribers: Int): SFlux[T] = SFlux.fromPublisher(connectableFlux.refCount(minSubscribers))

  /**
    * Connects to the upstream source when the given number of [[org.reactivestreams.Subscriber]] subscribes.
    * Disconnection can happen in two scenarios: when the upstream source completes (or errors) then
    * there is an immediate disconnection. However, when all subscribers have cancelled,
    * a <strong>deferred</strong> disconnection is scheduled. If any new subscriber comes
    * in during the `gracePeriod` that follows, the disconnection is cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/refCount.png" alt="">
    *
    * @param minSubscribers the number of subscribers expected to subscribe before connection
    * @param gracePeriod    the [[Duration]] for which to wait for new subscribers before actually
    *                                   disconnecting when all subscribers have cancelled.
    * @return a reference counting [[SFlux]] with a grace period for disconnection
    */
  final def refCount(minSubscribers: Int, gracePeriod: Duration): SFlux[T] = SFlux.fromPublisher(connectableFlux.refCount(minSubscribers, gracePeriod))

  /**
    * Connects to the upstream source when the given number of [[org.reactivestreams.Subscriber]] subscribes.
    * Disconnection can happen in two scenarios: when the upstream source completes (or errors) then
    * there is an immediate disconnection. However, when all subscribers have cancelled,
    * a <strong>deferred</strong> disconnection is scheduled. If any new subscriber comes
    * in during the `gracePeriod` that follows, the disconnection is cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/refCount.png" alt="">
    *
    * @param minSubscribers the number of subscribers expected to subscribe before connection
    * @param gracePeriod    the [[Duration]] for which to wait for new subscribers before actually
    *                                   disconnecting when all subscribers have cancelled.
    * @param scheduler the [[Scheduler]] on which to run timeouts
    * @return a reference counting [[SFlux]] with a grace period for disconnection
    */
  final def refCount(minSubscribers: Int, gracePeriod: Duration, scheduler: Scheduler): SFlux[T] = SFlux.fromPublisher(connectableFlux.refCount(minSubscribers, gracePeriod, scheduler))

  override private[publisher] def coreFlux: ConnectableFlux[T] = connectableFlux
}

object ConnectableSFlux {
  def apply[T](jConnectableFlux: ConnectableFlux[T]) = new ConnectableSFlux[T](jConnectableFlux)
}
