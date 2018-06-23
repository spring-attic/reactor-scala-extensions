package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean, Iterable => JIterable, Long => JLong}
import java.util
import java.util.concurrent.Callable
import java.util.function.{BiFunction, Function, Supplier}
import java.util.{Collection => JCollection, List => JList, Map => JMap}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{BufferOverflowStrategy, FluxSink, Signal, SignalType, SynchronousSink, Flux => JFlux, GroupedFlux => JGroupedFlux}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.core.{Disposable, Scannable => JScannable}
import reactor.util.concurrent.Queues.{SMALL_BUFFER_SIZE, XS_BUFFER_SIZE}
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Infinite
import scala.language.postfixOps
import scala.reflect.ClassTag

trait SFlux[T] extends SFluxLike[T, SFlux] with MapablePublisher[T] {
  self =>

  final def all(predicate: T => Boolean): SMono[Boolean] = new ReactiveSMono[Boolean](coreFlux.all(predicate).map((b: JBoolean) => Boolean2boolean(b)))

  final def any(predicate: T => Boolean): SMono[Boolean] = new ReactiveSMono[Boolean](coreFlux.any(predicate).map((b: JBoolean) => Boolean2boolean(b)))

  final def as[P](transformer: SFlux[T] => P): P = {
    coreFlux.as[P](new Function[JFlux[T], P] {
      override def apply(t: JFlux[T]): P = transformer(t)
    })
  }

  final def blockFirst(timeout: Duration = Duration.Inf): Option[T] = timeout match {
    case _: Infinite => Option(coreFlux.blockFirst())
    case t => Option(coreFlux.blockFirst(t))
  }

  final def blockLast(timeout: Duration = Duration.Inf): Option[T] = timeout match {
    case _: Infinite => Option(coreFlux.blockLast())
    case t => Option(coreFlux.blockLast(t))
  }

  final def buffer[C >: mutable.Buffer[T]](maxSize: Int = Int.MaxValue, bufferSupplier: () => C = () => mutable.ListBuffer.empty[T]): SFlux[Seq[T]] = {
    new ReactiveSFlux[Seq[T]](coreFlux.buffer(maxSize, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))
  }

  final def bufferTimeSpan(timespan: Duration, timer: Scheduler = Schedulers.parallel())(timeshift: Duration = timespan): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](coreFlux.buffer(timespan, timeshift, timer).map((l: JList[T]) => l.asScala))

  final def bufferPublisher(other: Publisher[_]): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.buffer(other).map((l: JList[T]) => l.asScala))

  final def bufferTimeout[C >: mutable.Buffer[T]](maxSize: Int, timespan: Duration, timer: Scheduler = Schedulers.parallel(), bufferSupplier: () => C = () => mutable.ListBuffer.empty[T]): SFlux[Seq[T]] = {
    new ReactiveSFlux[Seq[T]](coreFlux.bufferTimeout(maxSize, timespan, timer, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))
  }

  final def bufferUntil(predicate: T => Boolean, cutBefore: Boolean = false): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.bufferUntil(predicate, cutBefore).map((l: JList[T]) => l.asScala))

  final def bufferWhen[U, V, C >: mutable.Buffer[T]](bucketOpening: Publisher[U], closeSelector: U => Publisher[V], bufferSupplier: () => C = () => mutable.ListBuffer.empty[T]): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](coreFlux.bufferWhen(bucketOpening, closeSelector, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))

  final def bufferWhile(predicate: T => Boolean): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.bufferWhile(predicate).map((l: JList[T]) => l.asScala))

  final def cache(history: Int = Int.MaxValue, ttl: Duration = Duration.Inf): SFlux[T] = {
    ttl match {
      case _: Duration.Infinite => new ReactiveSFlux[T](coreFlux.cache(history))
      case _ => new ReactiveSFlux[T](coreFlux.cache(history, ttl))
    }
  }

  final def cast[E](implicit classTag: ClassTag[E]): SFlux[E] = new ReactiveSFlux[E](coreFlux.cast(classTag.runtimeClass.asInstanceOf[Class[E]]))

  final def collectSeq(): SMono[Seq[T]] = new ReactiveSMono[Seq[T]](coreFlux.collectList().map((l: JList[T]) => l.asScala))

  final def collectMap[K](keyExtractor: T => K): SMono[Map[K, T]] = collectMap[K, T](keyExtractor, (t: T) => t)

  final def collectMap[K, V](keyExtractor: T => K, valueExtractor: T => V, mapSupplier: () => mutable.Map[K, V] = () => mutable.HashMap.empty[K, V]): SMono[Map[K, V]] =
    new ReactiveSMono[Map[K, V]](coreFlux.collectMap[K, V](keyExtractor, valueExtractor, new Supplier[JMap[K, V]] {
      override def get(): JMap[K, V] = mapSupplier().asJava
    }).map((m: JMap[K, V]) => m.asScala.toMap))

  final def collectMultimap[K](keyExtractor: T => K): SMono[Map[K, Traversable[T]]] = collectMultimap(keyExtractor, (t: T) => t)

  final def collectMultimap[K, V](keyExtractor: T => K, valueExtractor: T => V, mapSupplier: () => mutable.Map[K, util.Collection[V]] = () => mutable.HashMap.empty[K, util.Collection[V]]): SMono[Map[K, Traversable[V]]] =
    new ReactiveSMono[Map[K, Traversable[V]]](coreFlux.collectMultimap[K, V](keyExtractor, valueExtractor,
      new Supplier[util.Map[K, util.Collection[V]]] {
        override def get(): util.Map[K, util.Collection[V]] = {
          mapSupplier().asJava
        }
      }).map((m: JMap[K, JCollection[V]]) => m.asScala.toMap.mapValues((vs: JCollection[V]) => vs.asScala.toSeq)))

  final def collectSortedSeq(ordering: Ordering[T] = None.orNull): SMono[Seq[T]] = new ReactiveSMono[Seq[T]](coreFlux.collectSortedList(ordering).map((l: JList[T]) => l.asScala))

  final def compose[V](transformer: Flux[T] => Publisher[V]): SFlux[V] = new ReactiveSFlux[V](coreFlux.compose[V](transformer))

  final def concatMapDelayError[V](mapper: T => Publisher[_ <: V], delayUntilEnd: Boolean = false, prefetch: Int = XS_BUFFER_SIZE): SFlux[V] =
    new ReactiveSFlux[V](coreFlux.concatMapDelayError[V](mapper, delayUntilEnd, prefetch))

  final def concatMapIterable[R](mapper: T => Iterable[_ <: R], prefetch: Int = XS_BUFFER_SIZE): SFlux[R] =
    new ReactiveSFlux[R](coreFlux.concatMapIterable(new Function[T, JIterable[R]] {
      override def apply(t: T): JIterable[R] = mapper(t)
    }, prefetch))

  final def concatWith(other: Publisher[_ <: T]): SFlux[T] = coreFlux.concatWith(other)

  private[publisher] def coreFlux: JFlux[T]

  final def count(): SMono[Long] = new ReactiveSMono[Long](coreFlux.count())

  final def delayElements(delay: Duration, timer: Scheduler = Schedulers.parallel()): SFlux[T] = new ReactiveSFlux[T](coreFlux.delayElements(delay, timer))

  final def delaySequence(delay: Duration, timer: Scheduler = Schedulers.parallel()): SFlux[T] = new ReactiveSFlux[T](coreFlux.delaySequence(delay, timer))

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SFlux[T] = new ReactiveSFlux[T](coreFlux.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SFlux[T] = new ReactiveSFlux[T](coreFlux.delaySubscription(subscriptionDelay))

  final def dematerialize[X](): Flux[X] = Flux(coreFlux.dematerialize[X]())

  final def distinct(): SFlux[T] = distinct(identity)

  final def distinct[V](keySelector: T => V): SFlux[T] = new ReactiveSFlux[T](coreFlux.distinct[V](keySelector))

  final def distinctUntilChanged(): SFlux[T] = distinctUntilChanged(identity)

  final def distinctUntilChanged[V](keySelector: T => V, keyComparator: (V, V) => Boolean = (x: V, y: V) => x == y): SFlux[T] = new ReactiveSFlux[T](coreFlux.distinctUntilChanged[V](keySelector, keyComparator))

  final def doAfterTerminate(afterTerminate: () => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doAfterTerminate(afterTerminate))

  final def doOnCancel(onCancel: () => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnCancel(onCancel))

  final def doOnComplete(onComplete: () => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnComplete(onComplete))

  final def doOnEach(signalConsumer: Signal[T] => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnEach(signalConsumer))

  final def doOnError(onError: Throwable => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnError(onError))

  final def doOnNext(onNext: T => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnNext(onNext))

  final def doOnRequest(f: Long => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnRequest(f))

  final def doOnSubscribe(onSubscribe: Subscription => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnSubscribe(onSubscribe))

  final def doOnTerminate(onTerminate: () => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnTerminate(onTerminate))

  final def doFinally(onFinally: SignalType => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doFinally(onFinally))

  final def elapsed(scheduler: Scheduler = Schedulers.parallel()): SFlux[(Long, T)] = new ReactiveSFlux[(Long, T)](coreFlux.elapsed(scheduler).map(new Function[Tuple2[JLong, T], (Long, T)] {
    override def apply(t: Tuple2[JLong, T]): (Long, T) = (Long2long(t.getT1), t.getT2)
  }))

  final def elementAt(index: Int, defaultValue: Option[T] = None): SMono[T] = new ReactiveSMono[T](
    defaultValue.map((t: T) => coreFlux.elementAt(index, t))
      .getOrElse(coreFlux.elementAt(index)))

  final def expandDeep(expander: T => Publisher[_ <: T], capacity: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreFlux.expandDeep(expander, capacity)

  final def expand(expander: T => Publisher[_ <: T], capacityHint: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreFlux.expand(expander, capacityHint)

  final def filter(p: T => Boolean): SFlux[T] = coreFlux.filter(p)

  final def filterWhen(asyncPredicate: T => _ <: MapablePublisher[Boolean], bufferSize: Int = SMALL_BUFFER_SIZE): SFlux[T] = {
    val asyncPredicateFunction = new Function[T, Publisher[JBoolean]] {
      override def apply(t: T): Publisher[JBoolean] = asyncPredicate(t).map(Boolean2boolean(_))
    }
    coreFlux.filterWhen(asyncPredicateFunction, bufferSize)
  }

  final def flatMap[R](mapperOnNext: T => Publisher[_ <: R],
                       mapperOnError: Throwable => Publisher[_ <: R],
                       mapperOnComplete: () => Publisher[_ <: R]): SFlux[R] = coreFlux.flatMap[R](mapperOnNext, mapperOnError, mapperOnComplete)

  final def flatMapIterable[R](mapper: T => Iterable[_ <: R], prefetch: Int = SMALL_BUFFER_SIZE): SFlux[R] = coreFlux.flatMapIterable(new Function[T, JIterable[R]] {
    override def apply(t: T): JIterable[R] = mapper(t)
  }, prefetch)

  final def flatMapSequential[R](mapper: T => Publisher[_ <: R], maxConcurrency: Int = SMALL_BUFFER_SIZE, prefetch: Int = XS_BUFFER_SIZE, delayError: Boolean = false): SFlux[R] =
    if (!delayError) coreFlux.flatMapSequential[R](mapper, maxConcurrency, prefetch)
    else coreFlux.flatMapSequentialDelayError[R](mapper, maxConcurrency, prefetch)


  final def groupBy[K](keyMapper: T => K): SFlux[GroupedFlux[K, T]] =
    groupBy(keyMapper, identity)

  final def groupBy[K, V](keyMapper: T => K, valueMapper: T => V, prefetch: Int = SMALL_BUFFER_SIZE): SFlux[GroupedFlux[K, V]] = {
    val jFluxOfGroupedFlux: JFlux[JGroupedFlux[K, V]] = coreFlux.groupBy(keyMapper, valueMapper, prefetch)
    new ReactiveSFlux[GroupedFlux[K, V]](jFluxOfGroupedFlux.map((jg: JGroupedFlux[K, V]) => GroupedFlux(jg)))
  }

  final def handle[R](handler: (T, SynchronousSink[R]) => Unit): SFlux[R] = coreFlux.handle[R](handler)

  final def hasElement(value: T): SMono[Boolean] = new ReactiveSMono[JBoolean](coreFlux.hasElement(value)).map(Boolean2boolean)

  final def hasElements: SMono[Boolean] = new ReactiveSMono[JBoolean](coreFlux.hasElements()).map(Boolean2boolean)

  final def ignoreElements(): SMono[T] = coreFlux.ignoreElements()

  final def index(): SFlux[(Long, T)] = index[(Long, T)]((x, y) => (x, y))

  final def index[I](indexMapper: (Long, T) => I): SFlux[I] = new ReactiveSFlux[I](coreFlux.index[I](new BiFunction[JLong, T, I] {
    override def apply(t: JLong, u: T) = indexMapper(Long2long(t), u)
  }))

  final def last(defaultValue: Option[T] = None): SMono[T] = new ReactiveSMono[T](
    defaultValue map (coreFlux.last(_)) getOrElse coreFlux.last()
  )

  override final def map[V](mapper: T => V): SFlux[V] = coreFlux.map[V](mapper)

  final def materialize(): SFlux[Signal[T]] = coreFlux.materialize()

  final def mergeWith(other: Publisher[_ <: T]): SFlux[T] = coreFlux.mergeWith(other)

  final def name(name: String): SFlux[T] = coreFlux.name(name)

  final def next(): SMono[T] = coreFlux.next()

  final def nonEmpty: SMono[Boolean] = hasElements

  final def ofType[U](implicit classTag: ClassTag[U]): SFlux[U] = coreFlux.ofType(classTag.runtimeClass.asInstanceOf[Class[U]])

  final def onBackpressureBuffer(): SFlux[T] = coreFlux.onBackpressureBuffer()

  final def onBackpressureBuffer(maxSize: Int): SFlux[T] = coreFlux.onBackpressureBuffer(maxSize)

  final def onBackpressureBuffer(maxSize: Int, onOverflow: T => Unit): SFlux[T] = coreFlux.onBackpressureBuffer(maxSize, onOverflow)

  final def onBackpressureBuffer(maxSize: Int, bufferOverflowStrategy: BufferOverflowStrategy): SFlux[T] = coreFlux.onBackpressureBuffer(maxSize, bufferOverflowStrategy)

  final def onBackpressureBuffer(maxSize: Int, onBufferOverflow: T => Unit, bufferOverflowStrategy: BufferOverflowStrategy): SFlux[T] = coreFlux.onBackpressureBuffer(maxSize, onBufferOverflow, bufferOverflowStrategy)

  final def onBackpressureDrop(): SFlux[T] = coreFlux.onBackpressureDrop()

  final def onBackpressureDrop(onDropped: T => Unit): SFlux[T] = coreFlux.onBackpressureDrop(onDropped)

  final def onBackpressureError(): SFlux[T] = coreFlux.onBackpressureError()

  final def onBackpressureLatest(): SFlux[T] = coreFlux.onBackpressureLatest()

  final def onErrorMap(mapper: Throwable => _ <: Throwable): SFlux[T] = coreFlux.onErrorMap(mapper)

  final def onErrorReturn(fallbackValue: T, predicate: Throwable => Boolean = (_: Throwable ) => true): SFlux[T] = coreFlux.onErrorReturn(predicate, fallbackValue)

  final def or(other: Publisher[_ <: T]): SFlux[T] = coreFlux.or(other)

  final def publishNext(): SMono[T] = coreFlux.publishNext()

  final def reduce(aggregator: (T, T) => T): SMono[T] = coreFlux.reduce(aggregator)

  final def reduceWith[A](initial: () => A, accumulator: (A, T) => A): SMono[A] = coreFlux.reduceWith[A](initial, accumulator)

  final def repeat(numRepeat: Long = Long.MaxValue, predicate: () => Boolean = () => true): SFlux[T] = coreFlux.repeat(numRepeat, predicate)

  final def retry(numRetries: Long = Long.MaxValue, retryMatcher: Throwable => Boolean = (_: Throwable) => true): SFlux[T] = coreFlux.retry(numRetries, retryMatcher)

  final def retryWhen(whenFactory: SFlux[Throwable] => Publisher[_]): SFlux[T] = {
    val func = new Function[JFlux[Throwable], Publisher[_]] {
      override def apply(t: JFlux[Throwable]): Publisher[_] = whenFactory(new ReactiveSFlux[Throwable](t))
    }
    coreFlux.retryWhen(func)
  }

  final def sample(timespan: Duration): SFlux[T] = coreFlux.sample(timespan)

  final def sampleFirst(timespan: Duration): SFlux[T] = coreFlux.sampleFirst(timespan)

  final def subscribe(): Disposable = coreFlux.subscribe()

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreFlux.subscribe(s)

  def take(n: Long): SFlux[T] = new ReactiveSFlux[T](coreFlux.take(n))
}

object SFlux {
  def apply[T](elements: T*): SFlux[T] = SFlux.fromIterable(elements)

  def combineLatest[T1, T2](p1: Publisher[T1], p2: Publisher[T2]): SFlux[(T1, T2)] =
    new ReactiveSFlux[(T1, T2)](JFlux.combineLatest(p1, p2, (t1: T1, t2: T2) => (t1, t2)))

  def combineLatest[T](sources: Publisher[T]*): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](JFlux.combineLatest[T, Seq[T]]
      (sources, (arr: Array[AnyRef]) => arr.toSeq map (_.asInstanceOf[T])))

  def combineLatestMap[T1, T2, V](p1: Publisher[T1], p2: Publisher[T2], mapper: (T1, T2) => V): SFlux[V] =
    new ReactiveSFlux[V](JFlux.combineLatest(p1, p2, mapper))

  def combineLatestMap[T: ClassTag, V](mapper: Array[T] => V, sources: Publisher[T]*): SFlux[V] = {
    val f = (arr: Array[AnyRef]) => {
      val x: Seq[T] = arr.toSeq map (_.asInstanceOf[T])
      mapper(x.toArray)
    }
    new ReactiveSFlux[V](JFlux.combineLatest(sources, f))
  }

  def concat[T](sources: Publisher[T]*): SFlux[T] = new ReactiveSFlux(JFlux.concat(sources))

  def concatDelayError[T](sources: Publisher[T]*): SFlux[T] = new ReactiveSFlux[T](JFlux.concatDelayError(sources: _*))

  def create[T](emitter: FluxSink[T] => Unit, backPressure: FluxSink.OverflowStrategy = OverflowStrategy.BUFFER): SFlux[T] = new ReactiveSFlux[T](JFlux.create(emitter, backPressure))

  def defer[T](f: => SFlux[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.defer(() => f))

  def empty[T]: SFlux[T] = new ReactiveSFlux(JFlux.empty[T]())

  def firstEmitter[I](sources: Publisher[_ <: I]*): SFlux[I] = new ReactiveSFlux[I](JFlux.first[I](sources: _*))

  def fromArray[T <: AnyRef](array: Array[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromArray[T](array))

  def fromIterable[T](iterable: Iterable[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromIterable(iterable.asJava))

  def fromPublisher[T](source: Publisher[_ <: T]): SFlux[T] = new ReactiveSFlux[T](JFlux.from(source))

  def fromStream[T](streamSupplier: () => Stream[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](streamSupplier()))

  def generate[T, S](generator: (S, SynchronousSink[T]) => S,
                     stateSupplier: Option[Callable[S]] = None,
                     stateConsumer: Option[S => Unit] = None): SFlux[T] = new ReactiveSFlux[T](
    JFlux.generate[T, S](stateSupplier.orNull[Callable[S]], generator, stateConsumer.orNull[S => Unit])
  )

  def interval(period: Duration, scheduler: Scheduler = Schedulers.parallel())(implicit delay: Duration = period): SFlux[Long] =
    new ReactiveSFlux[Long](JFlux.interval(delay, period).map((l: JLong) => Long2long(l)))

  def just[T](data: T*): SFlux[T] = apply[T](data: _*)

  def mergeSequentialPublisher[T](sources: Publisher[Publisher[T]], delayError: Boolean = false, maxConcurrency: Int = SMALL_BUFFER_SIZE, prefetch: Int = XS_BUFFER_SIZE): SFlux[T] =
    new ReactiveSFlux[T](
      if (delayError) JFlux.mergeSequentialDelayError[T](sources, maxConcurrency, prefetch)
      else JFlux.mergeSequential[T](sources, maxConcurrency, prefetch)
    )

  def mergeSequential[I](sources: Seq[Publisher[_ <: I]], delayError: Boolean = false, prefetch: Int = XS_BUFFER_SIZE): SFlux[I] =
    new ReactiveSFlux[I](
      if (delayError) JFlux.mergeSequentialDelayError(prefetch, sources: _*)
      else JFlux.mergeSequential(prefetch, sources: _*)
    )

  def mergeSequentialIterable[I](sources: Iterable[Publisher[_ <: I]], delayError: Boolean = false, maxConcurrency: Int = SMALL_BUFFER_SIZE, prefetch: Int = XS_BUFFER_SIZE) =
    new ReactiveSFlux[I](
      if (delayError) JFlux.mergeSequentialDelayError[I](sources, maxConcurrency, prefetch)
      else JFlux.mergeSequential[I](sources, maxConcurrency, prefetch))

  def never[T](): SFlux[T] = new ReactiveSFlux[T](JFlux.never[T]())

  def push[T](emitter: FluxSink[T] => Unit, backPressure: FluxSink.OverflowStrategy = OverflowStrategy.BUFFER): SFlux[T] = new ReactiveSFlux[T](JFlux.push(emitter, backPressure))

  def raiseError[T](e: Throwable, whenRequested: Boolean = false): SFlux[T] = new ReactiveSFlux[T](JFlux.error(e, whenRequested))

  def range(start: Int, count: Int): SFlux[Int] = new ReactiveSFlux[Int](JFlux.range(start, count).map((i: java.lang.Integer) => Integer2int(i)))

  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit, eager: Boolean = false): SFlux[T] =
    new ReactiveSFlux[T](JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup, eager))

  def zip[T1, T2](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2]): SFlux[(T1, T2)] =
    new ReactiveSFlux[(T1, T2)](JFlux.zip[T1, T2, (T1, T2)](source1, source2, (t1: T1, t2: T2) => (t1, t2)))

  def zip3[T1, T2, T3](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3]): SFlux[(T1, T2, T3)] = {
    new ReactiveSFlux[(T1, T2, T3)](JFlux.zip[T1, T2, T3](source1, source2, source3)
      .map[(T1, T2, T3)]((t: Tuple3[T1, T2, T3]) => tupleThree2ScalaTuple3[T1, T2, T3](t)))
  }

  def zip4[T1, T2, T3, T4](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4]): SFlux[(T1, T2, T3, T4)] =
    new ReactiveSFlux[(T1, T2, T3, T4)](JFlux.zip[T1, T2, T3, T4](source1, source2, source3, source4)
      .map[(T1, T2, T3, T4)]((t: Tuple4[T1, T2, T3, T4]) => tupleFour2ScalaTuple4(t)))

  def zip5[T1, T2, T3, T4, T5](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5]): SFlux[(T1, T2, T3, T4, T5)] =
    new ReactiveSFlux[(T1, T2, T3, T4, T5)](JFlux.zip[T1, T2, T3, T4, T5](source1, source2, source3, source4, source5)
      .map[(T1, T2, T3, T4, T5)]((t: Tuple5[T1, T2, T3, T4, T5]) => tupleFive2ScalaTuple5(t)))

  def zip6[T1, T2, T3, T4, T5, T6](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5], source6: Publisher[_ <: T6]): SFlux[(T1, T2, T3, T4, T5, T6)] =
    new ReactiveSFlux[(T1, T2, T3, T4, T5, T6)](JFlux.zip[T1, T2, T3, T4, T5, T6](source1, source2, source3, source4, source5, source6)
      .map((t: Tuple6[T1, T2, T3, T4, T5, T6]) => tupleSix2ScalaTuple6(t)))

  def zipMap[T1, T2, O](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], combinator: (T1, T2) => O): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[T1, T2, O](source1, source2, combinator))

  def zipMapIterable[O](sources: Iterable[_ <: Publisher[_]], combinator: Array[_] => O, prefetch: Int = SMALL_BUFFER_SIZE): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[O](sources, prefetch, combinator))

  def zipMap[I, O](combinator: Array[AnyRef] => O, sources: Seq[Publisher[_ <: I]], prefetch: Int = XS_BUFFER_SIZE): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[I, O](combinator, prefetch, sources: _*))
}

private[publisher] class ReactiveSFlux[T](publisher: Publisher[T]) extends SFlux[T] with Scannable {
  override private[publisher] val coreFlux: JFlux[T] = JFlux.from(publisher)

  override def scanUnsafe(key: JScannable.Attr[_]): Option[AnyRef] = Option(jScannable.scanUnsafe(key))

  override val jScannable: JScannable = JScannable.from(coreFlux)
}
