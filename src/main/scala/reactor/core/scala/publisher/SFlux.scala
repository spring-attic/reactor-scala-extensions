package reactor.core.scala.publisher

import java.lang.{Long => JLong}
import java.util.concurrent.Callable
import java.util.function.BiFunction

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{FluxSink, SynchronousSink, Flux => JFlux}
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.util.concurrent.Queues.{SMALL_BUFFER_SIZE, XS_BUFFER_SIZE}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import scala.reflect.ClassTag

trait SFlux[T] extends SFluxLike[T, SFlux] with Publisher[T] {
  self =>

  private[publisher] def coreFlux: JFlux[T]

  def doOnRequest(f: Long => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnRequest(f))

  final def index(): SFlux[(Long, T)] = index[(Long, T)]((x, y) => (x, y))

  final def index[I](indexMapper: (Long, T) => I): SFlux[I] = new ReactiveSFlux[I](coreFlux.index[I](new BiFunction[JLong, T, I] {
    override def apply(t: JLong, u: T) = indexMapper(Long2long(t), u)
  }))

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

  def error[T](e: Throwable, whenRequested: Boolean = false): SFlux[T] = new ReactiveSFlux[T](JFlux.error(e, whenRequested))

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
      if(delayError) JFlux.mergeSequentialDelayError[I](sources, maxConcurrency, prefetch)
      else JFlux.mergeSequential[I](sources, maxConcurrency, prefetch))

  def push[T](emitter: FluxSink[T] => Unit, backPressure: FluxSink.OverflowStrategy = OverflowStrategy.BUFFER): SFlux[T] = new ReactiveSFlux[T](JFlux.push(emitter, backPressure))
}

private[publisher] class ReactiveSFlux[T](publisher: Publisher[T]) extends SFlux[T] {
  override private[publisher] def coreFlux = JFlux.from(publisher)
}
