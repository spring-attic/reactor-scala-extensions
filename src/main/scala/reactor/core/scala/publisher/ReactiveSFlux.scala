package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux}
import reactor.core.scala.Scannable
import reactor.core.{Scannable => JScannable}

private[publisher] class ReactiveSFlux[T](publisher: Publisher[T]) extends SFlux[T] with Scannable {
  override private[publisher] val coreFlux: JFlux[T] = JFlux.from(publisher)

  override val jScannable: JScannable = JScannable.from(coreFlux)
}
