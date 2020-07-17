package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux}
import reactor.core.scala.publisher.versioned._

trait VersionedSFluxCompanion {
  def fromStream[T](streamSupplier: () => Stream[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](streamSupplier()))
}
