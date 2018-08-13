package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.concurrent.{Callable, CompletableFuture}
import java.util.function.Function

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.publisher.{MonoSink, Signal, SignalType, SynchronousSink, Flux => JFlux, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.core.{Disposable, Scannable => JScannable}
import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * A Reactive Streams [[Publisher]] with basic rx operators that completes successfully by emitting an element, or
  * with an error.
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mono.png" alt="">
  * <p>
  *
  * <p>The rx operators will offer aliases for input [[Mono]] type to preserve the "at most one"
  * property of the resulting [[Mono]]. For instance [[Mono.flatMap flatMap]] returns a [[Flux]] with
  * possibly
  * more than 1 emission. Its alternative enforcing [[Mono]] input is [[Mono.`then` then]].
  *
  * <p>`SMono[Unit]` should be used for [[Publisher]] that just completes without any value.
  *
  * <p>It is intended to be used in implementations and return types, input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>Note that using state in the `scala.Function` / lambdas used within Mono operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the type of the single value of this class
  * @see [[SFlux]]
  */
trait SMono[T] extends SMonoLike[T, SMono] with MapablePublisher[T] {
  self =>

  /**
    * Join the termination signals from this mono and another source into the returned
    * void mono
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/and.png" alt="">
    * <p>
    *
    * @param other the [[Publisher]] to wait for
    *                          complete
    * @return a new combined [[SMono]]
    * @see [[SMono.when]]
    */
  final def and(other: Publisher[_]): SMono[Unit] = {
    new ReactiveSMono(coreMono.and(other match {
      case f: SFlux[_] => f.coreFlux
      case m: SMono[_] => m.coreMono
    })) map[Unit] (_ => ())
  }

  /**
    * Transform this [[Mono]] into a target type.
    *
    * `mono.as(Flux::from).subscribe()`
    *
    * @param transformer the { @link Function} applying this { @link Mono}
    * @tparam P the returned instance type
    * @return the transformed { @link Mono} to instance P
    * @see [[Mono.compose]] for a bounded conversion to [[org.reactivestreams.Publisher]]
    */
  final def as[P](transformer: SMono[T] => P): P = transformer(this)

  /**
    * Get the underlying [[reactor.core.publisher.Mono]]
    * @return [[reactor.core.publisher.Mono]]
    */
  final def asJava(): JMono[T] = coreMono

  /**
    * Block until a next signal is received, will return null if onComplete, T if onNext, throw a
    * `Exceptions.DownstreamException` if checked error or origin RuntimeException if unchecked.
    * If the default timeout `30 seconds` has elapsed,a [[RuntimeException]]  will be thrown.
    *
    * Note that each block() will subscribe a new single (MonoSink) subscriber, in other words, the result might
    * miss signal from hot publishers.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/block.png" alt="">
    * <p>
    *
    * @param timeout maximum time period to wait for before raising a [[RuntimeException]]. Defaulted to [[Duration.Inf]]
    * @return T the result
    */
  final def block(timeout: Duration = Duration.Inf): T =
    if(timeout == Duration.Inf) coreMono.block()
    else coreMono.block(timeout)

  /**
    * Subscribe to this [[Mono]] and <strong>block</strong> until a next signal is
    * received, the Mono completes empty or a timeout expires. Returns an [[Option]]
    * for the first two cases, which can be used to replace the empty case with an
    * Exception via [[Option.orElse(throw exception)]].
    * In case the Mono itself errors, the original exception is thrown (wrapped in a
    * [[RuntimeException]] if it was a checked exception).
    * If the provided timeout expires, a [[RuntimeException]] is thrown.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.1.RELEASE/src/docs/marble/block.png" alt="">
    * <p>
    * Note that each block() will trigger a new subscription: in other words, the result
    * might miss signal from hot publishers.
    *
    * @param timeout maximum time period to wait for before raising a [[RuntimeException]]. Defaulted to [[Duration.Inf]]
    * @return T the result
    */
  final def blockOption(timeout: Duration = Duration.Inf): Option[T] =
    if(timeout == Duration.Inf) coreMono.blockOptional()
    else coreMono.blockOptional(timeout)

  /**
    * Cast the current [[SMono]] produced type into a target produced type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/cast1.png" alt="">
    *
    * @tparam E the [[SMono]] output type
    * @param clazz the target type to cast to
    * @return a casted [[SMono]]
    */
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

  final def doOnTerminate(onTerminate: () => Unit): SMono[T] = coreMono.doOnTerminate(onTerminate)

  final def elapsed(scheduler: Scheduler = Schedulers.parallel()): SMono[(Long, T)] = new ReactiveSMono[(Long, T)](coreMono.elapsed().map((t: Tuple2[JLong, T]) => javaTupleLongAndT2ScalaTupleLongAndT[T](t)))

  final def expandDeep(expander: T => Publisher[_ <: T], capacityHint: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreMono.expandDeep(expander, capacityHint)

  final def expand(expander: T => Publisher[_ <: T], capacityHint: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreMono.expand(expander, capacityHint)

  final def filter(tester: T => Boolean): SMono[T] = coreMono.filter(tester)

  final def filterWhen(asyncPredicate: T => _ <: MapablePublisher[Boolean]): SMono[T] = {
    val asyncPredicateFunction = new Function[T, Publisher[JBoolean]] {
      override def apply(t: T): Publisher[JBoolean] = asyncPredicate(t).map(Boolean2boolean(_))
    }
    coreMono.filterWhen(asyncPredicateFunction)
  }

  final def flatMap[R](transformer: T => SMono[R]): SMono[R] = coreMono.flatMap[R]((t: T) => transformer(t).coreMono)

  final def flatMapMany[R](mapper: T => Publisher[R]): SFlux[R] = coreMono.flatMapMany(mapper)

  final def flatMapMany[R](mapperOnNext: T => Publisher[R],
                           mapperOnError: Throwable => Publisher[R],
                           mapperOnComplete: () => Publisher[R]): SFlux[R] =
    coreMono.flatMapMany(mapperOnNext, mapperOnError, mapperOnComplete)

  final def flatMapIterable[R](mapper: T => Iterable[R]): SFlux[R] = coreMono.flatMapIterable(mapper.andThen(it => it.asJava))

  final def flux(): SFlux[T] = coreMono.flux()

  final def hasElement: SMono[Boolean] = coreMono.hasElement.map[Boolean](scalaFunction2JavaFunction((jb: JBoolean) => boolean2Boolean(jb.booleanValue())))

  final def handle[R](handler: (T, SynchronousSink[R]) => Unit): SMono[R] = coreMono.handle[R](handler)

  final def ignoreElement: SMono[T] = coreMono.ignoreElement()

  final def map[R](mapper: T => R): SMono[R] = coreMono.map[R](mapper)

  final def materialize(): SMono[Signal[T]] = coreMono.materialize()

  final def mergeWith(other: Publisher[_ <: T]): SFlux[T] = coreMono.mergeWith(other)

  final def name(name: String): SMono[T] = coreMono.name(name)

  final def ofType[U](clazz: Class[U]): SMono[U] = coreMono.ofType[U](clazz)

  final def onErrorMap(mapper: PartialFunction[Throwable, Throwable]): SMono[T] =
    coreMono.onErrorMap((t: Throwable) => if (mapper.isDefinedAt(t)) mapper(t) else t)

  private def defaultToMonoError[U](t: Throwable): SMono[U] = SMono.raiseError[U](t)

  final def onErrorRecover[U <: T](pf: PartialFunction[Throwable, U]): SMono[T] = {
    def recover(t: Throwable): SMono[U] = pf.andThen(u => SMono.just(u)).applyOrElse(t, defaultToMonoError)

    onErrorResume(recover)
  }

  final def onErrorResume(fallback: Throwable => SMono[_ <: T]): SMono[T] = {
    val fallbackFunction = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).coreMono
    }
    coreMono.onErrorResume(fallbackFunction)
  }

  final def or(other: SMono[_ <: T]): SMono[T] = coreMono.or(other.coreMono)

  final def publish[R](transform: SMono[T] => SMono[R]): SMono[R] = {
    val transformFunction = new Function[JMono[T], JMono[R]] {
      override def apply(t: JMono[T]): JMono[R] = transform(SMono.this).coreMono
    }
    coreMono.publish[R](transformFunction)
  }

  final def repeat(numRepeat: Long = Long.MaxValue, predicate: () => Boolean = () => true): SFlux[T] = coreMono.repeat(numRepeat, predicate)

  final def repeatWhen(whenFactory: SFlux[Long] => _ <: Publisher[_]): SFlux[T] = {
    val when = new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = whenFactory(new ReactiveSFlux[Long](t))
    }
    coreMono.repeatWhen(when)
  }

  final def repeatWhenEmpty(repeatFactory: SFlux[Long] => Publisher[_]): SMono[T] = {
    val when = new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = repeatFactory(new ReactiveSFlux[Long](t))
    }
    coreMono.repeatWhenEmpty(when)
  }

  final def single(): SMono[T] = coreMono.single()

  final def subscribe(): Disposable = coreMono.subscribe()

  final def subscribe(consumer: T => Unit): Disposable = coreMono.subscribe(consumer)

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer)

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer, completeConsumer)

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit, subscriptionConsumer: Subscription => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer, completeConsumer, subscriptionConsumer)

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

  final def switchIfEmpty(alternate: SMono[_ <: T]): SMono[T] = coreMono.switchIfEmpty(alternate.coreMono)

  final def tag(key: String, value: String): SMono[T] = coreMono.tag(key, value)

  final def take(duration: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = coreMono.take(duration, timer)

  final def takeUntilOther(other: Publisher[_]): SMono[T] = coreMono.takeUntilOther(other)

  final def `then`(): SMono[Unit] = new ReactiveSMono[Unit](coreMono.`then`().map((_: Void) => ()))

  final def `then`[V](other: SMono[V]): SMono[V] = coreMono.`then`(other.coreMono)

  final def thenEmpty(other: MapablePublisher[Unit]): SMono[Unit] = new ReactiveSMono[Unit]((coreMono: JMono[T]).thenEmpty(other).map((_: Void) => ()))

  final def thenMany[V](other: Publisher[V]): SFlux[V] = coreMono.thenMany(other)

  final def timeout(timeout: Duration, fallback: Option[SMono[_ <: T]] = None, timer: Scheduler = Schedulers.parallel()): SMono[T] =
    coreMono.timeout(timeout, fallback.map(_.coreMono).orNull[JMono[_ <: T]], timer)

  final def timeoutWhen[U](firstTimeout: Publisher[U], fallback: Option[SMono[_ <: T]] = None, timer: Scheduler = Schedulers.parallel()): SMono[T] = {
    val x: JMono[T] = fallback.map((sm: SMono[_ <: T]) => coreMono.timeout[U](firstTimeout, sm.coreMono))
      .getOrElse(coreMono.timeout[U](firstTimeout))
    new ReactiveSMono[T](x)
  }

//  How to test this?
  final def timestamp(scheduler: Scheduler = Schedulers.parallel()): SMono[(Long, T)] = new ReactiveSMono[(Long, T)](coreMono.timestamp(scheduler).map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))

  final def toFuture: Future[T] = {
    val promise = Promise[T]()
    coreMono.toFuture.handle[Unit]((value: T, throwable: Throwable) => {
      Option(value).foreach(v => promise.complete(Try(v)))
      Option(throwable).foreach(t => promise.failure(t))
      ()
    })
    promise.future
  }

  final def transform[V](transformer: SMono[T] => Publisher[V]): SMono[V] = coreMono.transform[V]((_: JMono[T]) => transformer(SMono.this))

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

  /**
    * Aggregate given void publishers into a new a `Mono` that will be
    * fulfilled when all of the given `Monos` have been fulfilled. If any Mono terminates without value,
    * the returned sequence will be terminated immediately and pending results cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[SMono]].
    */
  def when(sources: Iterable[_ <: Publisher[Unit] with MapablePublisher[Unit]]): SMono[Unit] = {
    new ReactiveSMono[Unit](
      JMono.when(sources.map(s => s.map((_: Unit) => None.orNull: Void)).asJava).map((_: Void) => ())
    )
  }

  /**
    * Aggregate given publishers into a new `Mono` that will be fulfilled
    * when all of the given `sources` have been fulfilled. An error will cause
    * pending results to be cancelled and immediate error emission to the returned [[SMono]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[SMono]].
    */
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