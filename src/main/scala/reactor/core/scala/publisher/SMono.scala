package reactor.core.scala.publisher

import java.util.concurrent.{Callable, CompletableFuture}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Mono => JMono}
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

trait SMono[T] extends SMonoLike[T, SMono] with MapablePublisher[T] {
  self =>

  private[publisher] def coreMono: JMono[T]

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(subscriptionDelay))

  final def map[R](mapper: T => R): SMono[R] = coreMono.map[R](mapper)

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

}

object SMono {
  def just[T](data: T): SMono[T] = new ReactiveSMono[T](JMono.just(data))

  def raiseError[T](error: Throwable): SMono[T] = JMono.error[T](error)

  def from[T](source: Publisher[_ <: T]): SMono[T] = JMono.from[T](source)

  def fromCallable[T](supplier: Callable[T]): SMono[T] = JMono.fromCallable[T](supplier)

  def fromDirect[I](source: Publisher[_ <: I]): SMono[I] = JMono.fromDirect[I](source)

  def fromFuture[T](future: Future[T])(implicit executionContext: ExecutionContext): SMono[T] = {
    val completableFuture = new CompletableFuture[T]()
    future onComplete {
      case Success(t) => completableFuture.complete(t)
      case Failure(error) => completableFuture.completeExceptionally(error)
    }
    JMono.fromFuture[T](completableFuture)
  }
}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)
}