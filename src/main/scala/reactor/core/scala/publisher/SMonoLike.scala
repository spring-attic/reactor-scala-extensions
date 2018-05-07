package reactor.core.scala.publisher

trait SMonoLike[T, Self[U] <: SMonoLike[U, Self]] { self: Self[T] =>

}
