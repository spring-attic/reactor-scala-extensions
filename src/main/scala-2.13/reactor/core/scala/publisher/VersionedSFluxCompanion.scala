package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux}
import scala.jdk.StreamConverters._

trait VersionedSFluxCompanion {
  @deprecated("Use fromLazyList, will be removed in 1.0.0", "0.8.0")
  def fromStream[T](streamSupplier: () => Stream[T]): SFlux[T] = fromLazyList(() => streamSupplier().to(LazyList))

  def fromLazyList[T](lazyListSupplier: () => LazyList[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](lazyListSupplier().asJavaSeqStream))

}
