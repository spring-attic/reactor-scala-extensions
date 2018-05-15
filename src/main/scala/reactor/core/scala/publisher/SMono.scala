package reactor.core.scala.publisher

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Mono => JMono}
import reactor.core.scheduler.{Scheduler, Schedulers}

import scala.concurrent.duration.Duration

trait SMono[T] extends SMonoLike[T, SMono] with Publisher[T] { self =>

  private[publisher] def coreMono: JMono[T]

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(subscriptionDelay))

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

}

object SMono {
  def just[T](data: T): SMono[T] = new ReactiveSMono[T](JMono.just(data))
}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)
}