package reactor.core.scala.publisher

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux => JFlux}

/**
  * Created by winarto on 1/4/17.
  */
class Flux[T](jFlux: JFlux[T]) extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)
}

object Flux {
  def from[T](source: Publisher[_ <: T]): Flux[T] = {
    new Flux[T](
      JFlux.from(source)
    )
  }
}
