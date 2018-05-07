package reactor.core.scala.publisher

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Mono => JMono}

trait SMono[T] extends SMonoLike[T, SMono] with Publisher[T] { self =>

  private[publisher] def coreMono: JMono[T]

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)
}