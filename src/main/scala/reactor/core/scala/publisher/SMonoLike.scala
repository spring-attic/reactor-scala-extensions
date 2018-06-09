package reactor.core.scala.publisher

import scala.language.higherKinds

trait SMonoLike[T, Self[U] <: SMonoLike[U, Self]] {
  self: Self[T] =>

}
