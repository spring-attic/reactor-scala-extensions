package reactor.core.scala.publisher

import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait FluxLike[T/*, Self[+U] <: FluxLike[U, Flux]*/] { self: Flux[T] =>
  def flatten[S](implicit ev: T <:< Flux[S]): Flux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)
}
