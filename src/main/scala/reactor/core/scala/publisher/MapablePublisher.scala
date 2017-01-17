package reactor.core.scala.publisher

trait MapablePublisher[T] {
  def map[U](mapper: T => U): MapablePublisher[U]
}
