package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.scala.Scannable
import reactor.core.{Scannable => JScannable}
import reactor.core.publisher.{Mono => JMono}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] with Scannable {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)

  override private[scala] def jScannable: JScannable = JScannable.from(coreMono)
}
