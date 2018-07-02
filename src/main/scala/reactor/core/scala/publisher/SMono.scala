package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean}
import java.util.concurrent.{Callable, CompletableFuture}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{MonoSink, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.core.{Scannable => JScannable}
import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SMono[T] extends SMonoLike[T, SMono] with MapablePublisher[T] {
  self =>

  final def asJava(): JMono[T] = coreMono

  private[publisher] def coreMono: JMono[T]

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(subscriptionDelay))

  final def map[R](mapper: T => R): SMono[R] = coreMono.map[R](mapper)

  final def name(name: String): SMono[T] = coreMono.name(name)

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

}

object SMono {

  def create[T](callback: MonoSink[T] => Unit): SMono[T] = JMono.create[T](callback)

  def defer[T](supplier: () => SMono[T]): SMono[T] = JMono.defer[T](supplier)

  def delay(duration: Duration, timer: Scheduler = Schedulers.parallel()): SMono[Long] = new ReactiveSMono[Long](JMono.delay(duration, timer))

  def empty[T]: SMono[T] = JMono.empty[T]()

  def firstEmitter[T](monos: SMono[_ <: T]*): SMono[T] = JMono.first[T](monos.map(_.asJava()): _*)

  def fromPublisher[T](source: Publisher[_ <: T]): SMono[T] = JMono.from[T](source)

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

  def ignoreElements[T](source: Publisher[T]): SMono[T] = JMono.ignoreElements(source)

  def just[T](data: T): SMono[T] = new ReactiveSMono[T](JMono.just(data))

  def justOrEmpty[T](data: Option[_ <: T]): SMono[T] = JMono.justOrEmpty[T](data)

  def justOrEmpty[T](data: Any): SMono[T] = {
    data match {
      case o: Option[T] => JMono.justOrEmpty[T](o)
      case other: T => JMono.justOrEmpty[T](other)
    }
  }

  def never[T]: SMono[T] = JMono.never[T]()

  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T], isEqual: (T, T) => Boolean = (t1: T, t2: T) => t1 == t2, bufferSize: Int = SMALL_BUFFER_SIZE): SMono[Boolean] =
    new ReactiveSMono[JBoolean](JMono.sequenceEqual[T](source1, source2, isEqual, bufferSize)).map(Boolean2boolean)

  def raiseError[T](error: Throwable): SMono[T] = JMono.error[T](error)
}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] with Scannable {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)

  override private[scala] def jScannable: JScannable = JScannable.from(coreMono)
}