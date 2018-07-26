package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean}
import java.util.concurrent.{Callable, CompletableFuture}
import java.util.function.Function

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.publisher.{MonoSink, SignalType, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.core.{Scannable => JScannable}
import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait SMono[T] extends SMonoLike[T, SMono] with MapablePublisher[T] {
  self =>

  final def and(other: Publisher[_]): SMono[Unit] = {
    new ReactiveSMono(coreMono.and(other match {
      case f: SFlux[_] => f.coreFlux
      case m: SMono[_] => m.coreMono
    })) map[Unit] (_ => ())
  }

  final def asJava(): JMono[T] = coreMono

  final def block(): T = coreMono.block()

  final def block(timeout: Duration): T = coreMono.block(timeout)

  final def blockOption(): Option[T] = coreMono.blockOptional()

  final def blockOption(timeout: Duration): Option[T] = coreMono.blockOptional(timeout)

  final def cast[E](clazz: Class[E]): SMono[E] = coreMono.cast(clazz)

  final def cache(): SMono[T] = coreMono.cache()

  final def cache(ttl: Duration): SMono[T] = coreMono.cache(ttl)

  final def cancelOn(scheduler: Scheduler): SMono[T] = coreMono.cancelOn(scheduler)

  final def compose[V](transformer: SMono[T] => Publisher[V]): SMono[V] = {
    val transformerFunction = new Function[JMono[T], Publisher[V]] {
      override def apply(t: JMono[T]): Publisher[V] = transformer(SMono.this)
    }
    coreMono.compose(transformerFunction)
  }

  final def concatWith(other: Publisher[T]): SFlux[T] = coreMono.concatWith(other)

  final def ++(other: Publisher[T]): SFlux[T] = concatWith(other)

  private[publisher] def coreMono: JMono[T]

  final def defaultIfEmpty(defaultV: T): SMono[T] = coreMono.defaultIfEmpty(defaultV)

  final def delayElement(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = coreMono.delayElement(delay)

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(subscriptionDelay))

  final def delayUntil(triggerProvider: T => Publisher[_]): SMono[T] = coreMono.delayUntil(triggerProvider)

  final def dematerialize[X](): SMono[X] = coreMono.dematerialize[X]()

  final def doAfterSuccessOrError(afterTerminate: Try[_ <: T] => Unit): SMono[T] = {
    val biConsumer = (t: T, u: Throwable) => Option(t) match {
      case Some(s) => afterTerminate(Success(s))
      case Some(null) | None => afterTerminate(Failure(u))
    }
    coreMono.doAfterSuccessOrError(biConsumer)
  }

  final def doAfterTerminate(afterTerminate: () => Unit): SMono[T] = coreMono.doAfterTerminate(afterTerminate)

  final def doFinally(onFinally: SignalType => Unit): SMono[T] = coreMono.doFinally(onFinally)

  final def doOnCancel(onCancel: () => Unit): SMono[T] = coreMono.doOnCancel(onCancel)

  final def doOnNext(onNext: T => Unit): SMono[T] = coreMono.doOnNext(onNext)

  final def doOnSuccess(onSuccess: T => Unit): SMono[T] = coreMono.doOnSuccess(onSuccess)

  final def doOnError(onError: Throwable => Unit): SMono[T] = coreMono.doOnError(onError)

  final def doOnRequest(consumer: Long => Unit): SMono[T] = coreMono.doOnRequest(consumer)

  final def doOnSubscribe(onSubscribe: Subscription => Unit): SMono[T] = coreMono.doOnSubscribe(onSubscribe)

  final def doOnTerminate(onTerminate:() => Unit): SMono[T] = coreMono.doOnTerminate(onTerminate)

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

  def when(sources: Iterable[_ <: Publisher[Unit] with MapablePublisher[Unit]]): SMono[Unit] = {
    new ReactiveSMono[Unit](
      JMono.when(sources.map(s => s.map((_: Unit) => None.orNull: Void)).asJava).map((_: Void) => ())
    )
  }

  def when(sources: Publisher[Unit] with MapablePublisher[Unit]*): SMono[Unit] = new ReactiveSMono[Unit](
    JMono.when(sources.map(s => s.map((_: Unit) => None.orNull: Void)).asJava).map((_: Void) => ())
  )

  def zipDelayError[T1, T2](p1: SMono[_ <: T1], p2: SMono[_ <: T2]): SMono[(T1, T2)] = {
    new ReactiveSMono[(T1, T2)](JMono.zipDelayError[T1, T2](p1.coreMono, p2.coreMono).map((t: Tuple2[T1, T2]) => tupleTwo2ScalaTuple2(t)))
  }

  def zipDelayError[T1, T2, T3](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3]): SMono[(T1, T2, T3)] = {
    new ReactiveSMono[(T1, T2, T3)](JMono.zipDelayError[T1, T2, T3](p1.coreMono, p2.coreMono, p3.coreMono).map((t: Tuple3[T1, T2, T3]) => tupleThree2ScalaTuple3(t)))
  }

  def zipDelayError[T1, T2, T3, T4](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4]): SMono[(T1, T2, T3, T4)] = {
    new ReactiveSMono[(T1, T2, T3, T4)](
      JMono.zipDelayError[T1, T2, T3, T4](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono).map((t: Tuple4[T1, T2, T3, T4]) => tupleFour2ScalaTuple4(t))
    )
  }

  def zipDelayError[T1, T2, T3, T4, T5](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4], p5: SMono[_ <: T5]): SMono[(T1, T2, T3, T4, T5)] = {
    new ReactiveSMono[(T1, T2, T3, T4, T5)](
      JMono.zipDelayError[T1, T2, T3, T4, T5](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono, p5.coreMono).map((t: Tuple5[T1, T2, T3, T4, T5]) => tupleFive2ScalaTuple5(t))
    )
  }

  def zipDelayError[T1, T2, T3, T4, T5, T6](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4], p5: SMono[_ <: T5], p6: SMono[_ <: T6]): SMono[(T1, T2, T3, T4, T5, T6)] = new ReactiveSMono[(T1, T2, T3, T4, T5, T6)](
    JMono.zipDelayError[T1, T2, T3, T4, T5, T6](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono, p5.coreMono, p6.coreMono).map((t: Tuple6[T1, T2, T3, T4, T5, T6]) => tupleSix2ScalaTuple6(t))
  )

  def whenDelayError(sources: Iterable[_ <: Publisher[_] with MapablePublisher[_]]): SMono[Unit] = new ReactiveSMono[Unit](
    JMono.whenDelayError(sources.map(s => s.map((_: Any) => None.orNull: Void)).asJava).map((_: Void) => ())
  )

  def zipDelayError[R](monos: Iterable[_ <: SMono[_]], combinator: Array[AnyRef] => _ <: R): SMono[R] = {
    new ReactiveSMono[R](JMono.zipDelayError[R](monos.map(_.asJava()).asJava, new Function[Array[AnyRef], R] {
      override def apply(t: Array[AnyRef]): R = {
        val v = t.map { v => v: AnyRef }
        combinator(v)
      }
    }))
  }

  def zipDelayError[R](combinator: Array[Any] => R, monos: SMono[_]*): SMono[R] = {
    val combinatorFunction = new Function[Array[AnyRef], R] {
      override def apply(t: Array[AnyRef]): R = {
        val v = t.map { v => v: Any }
        combinator(v)
      }
    }
    new ReactiveSMono[R](JMono.zipDelayError[R](combinatorFunction, monos.map(_.asJava()): _*))
  }

  def zip[R](combinator: Array[AnyRef] => R, monos: SMono[_]*): SMono[R] = new ReactiveSMono[R](JMono.zip(combinator, monos.map(_.asJava()).toArray: _*))

  def zip[R](monos: Iterable[_ <: SMono[_]], combinator: Array[AnyRef] => R): SMono[R] =
    new ReactiveSMono[R](JMono.zip(monos.map(_.asJava()).asJava, new Function[Array[Object], R] {
      override def apply(t: Array[Object]) = combinator(t.map { v => Option(v): Option[AnyRef] }.filterNot(_.isEmpty).map(_.getOrElse(None.orNull)))
    }))
}

private[publisher] class ReactiveSMono[T](publisher: Publisher[T]) extends SMono[T] with Scannable {
  override private[publisher] def coreMono: JMono[T] = JMono.from[T](publisher)

  override private[scala] def jScannable: JScannable = JScannable.from(coreMono)
}