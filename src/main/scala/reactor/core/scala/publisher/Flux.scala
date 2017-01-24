package reactor.core.scala.publisher

import java.util.function.Function

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux => JFlux}
import java.lang.{Long => JLong}

import scala.concurrent.duration.Duration

/**
  * A Reactive Streams [[Publisher]] with rx operators that emits 0 to N elements, and then completes
  * (successfully or with an error).
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/flux.png" alt="">
  * <p>
  *
  * <p>It is intended to be used in implementations and return types. Input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>If it is known that the underlying [[Publisher]] will emit 0 or 1 element, [[Mono]] should be used
  * instead.
  *
  * <p>Note that using state in the [[scala.Function1]] / lambdas used within Flux operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the element type of this Reactive Streams [[Publisher]]
  * @see [[Mono]]
  */
class Flux[T](private val jFlux: JFlux[T]) extends Publisher[T] with MapablePublisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)

  def count(): Mono[Long] = Mono[Long](jFlux.count().map(new Function[JLong, Long] {
    override def apply(t: JLong) = Long2long(t)
  }))

  def take(n: Long) = new Flux[T](jFlux.take(n))

  def sample(duration: Duration) = new Flux[T](jFlux.sample(duration))

  override def map[U](mapper: (T) => U) = new Flux[U](jFlux.map(mapper))

  /**
    * Provide a default unique value if this sequence is completed without any data
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[Flux]]
    */
  final def defaultIfEmpty(defaultV: T) = new Flux[T](jFlux.defaultIfEmpty(defaultV))

  final def asJava(): JFlux[T] = jFlux
}

object Flux {
  def from[T](source: Publisher[_ <: T]): Flux[T] = {
    new Flux[T](
      JFlux.from(source)
    )
  }

  def just[T](data: T*): Flux[T] = {
    new Flux[T](
      JFlux.just(data:_*)
    )
  }
}
