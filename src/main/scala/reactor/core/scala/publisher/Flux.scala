package reactor.core.scala.publisher

import java.util.function.Function

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux => JFlux}
import java.lang.{Long => JLong}

import scala.concurrent.duration.Duration

/**
  * Created by winarto on 1/4/17.
  */
class Flux[T](jFlux: JFlux[T]) extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)

  def count(): Mono[Long] = {
    new Mono[Long](jFlux.count().map(new Function[JLong, Long] {
      override def apply(t: JLong): Long = Long2long(t)
    }))
  }

  def take(n: Long): Flux[T] = {
    new Flux[T](jFlux.take(n))
  }

  def sample(duration: Duration): Flux[T] = {
    new Flux[T](jFlux.sample(duration))
  }
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
