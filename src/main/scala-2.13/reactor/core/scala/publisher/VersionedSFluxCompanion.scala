package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux}
import scala.jdk.StreamConverters._

trait VersionedSFluxCompanion {
  def fromStream[T](streamSupplier: () => LazyList[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](streamSupplier().asJavaSeqStream))
}
