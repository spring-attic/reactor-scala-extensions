package reactor.core.scala.publisher

import org.reactivestreams.Publisher

import scala.annotation.unchecked.uncheckedVariance

trait MapablePublisher[+T] extends Publisher[T @uncheckedVariance]{
  def map[U](mapper: T => U): MapablePublisher[U]
}
