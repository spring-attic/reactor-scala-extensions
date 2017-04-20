package reactor.core.scala.publisher

import org.reactivestreams.Publisher

trait MapablePublisher[T] extends Publisher[T]{
  def map[U](mapper: T => U): MapablePublisher[U]
}
