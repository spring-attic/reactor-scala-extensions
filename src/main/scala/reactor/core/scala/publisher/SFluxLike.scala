package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux}
import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait SFluxLike[T, Self[U] <: SFluxLike[U, Self]] {
  self: Self[T] =>

  final def concatMap[V](mapper: T => Publisher[_ <: V], prefetch: Int = XS_BUFFER_SIZE): SFlux[V] = new ReactiveSFlux[V](coreFlux.concatMap[V](mapper, prefetch))

  private[publisher] def coreFlux: JFlux[T]

  final def flatten[S](implicit ev: T <:< SFlux[S]): SFlux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)
}
