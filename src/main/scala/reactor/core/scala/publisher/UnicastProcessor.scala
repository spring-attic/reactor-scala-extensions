package reactor.core.scala.publisher

import org.reactivestreams.Subscription
import reactor.core.{Scannable, publisher}
import reactor.core.publisher.{UnicastProcessor => JUnicastProcessor}

/**
  * A Processor implementation that takes a custom queue and allows
  * only a single subscriber.
  *
  * <p>
  * The implementation keeps the order of signals.
  *
  * @tparam T the input and output type
  */
class UnicastProcessor[T](val jUnicastProcessor: JUnicastProcessor[T]) extends SFlux[T] with FluxProcessor[T, T] {

  override def onComplete(): Unit = jUnicastProcessor.onComplete()

  override def onError(t: Throwable): Unit = jUnicastProcessor.onError(t)

  override def onNext(t: T): Unit = jUnicastProcessor.onNext(t)

  override def onSubscribe(s: Subscription): Unit = jUnicastProcessor.onSubscribe(s)

  override protected def jFluxProcessor: publisher.FluxProcessor[T, T] = jUnicastProcessor

  override def jScannable: Scannable = jFluxProcessor

  override private[publisher] def coreFlux = jUnicastProcessor
}

object UnicastProcessor {

  private[publisher] def apply[T](jUnicastProcessor: JUnicastProcessor[T]): UnicastProcessor[T] = new UnicastProcessor[T](jUnicastProcessor)

  /**
    * Create a unicast [[FluxProcessor]] that will buffer on a given queue in an
    * unbounded fashion.
    *
    * @tparam T the relayed type
    * @return a unicast [[FluxProcessor]]
    */
  def create[T](): UnicastProcessor[T] = apply[T](JUnicastProcessor.create[T]())
}
