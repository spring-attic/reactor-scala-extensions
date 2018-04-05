package reactor.core.scala.publisher

import scala.language.higherKinds

trait SFluxLike[T, Self[U] <: SFluxLike[U, Self]] { self: Self[T] =>
}
