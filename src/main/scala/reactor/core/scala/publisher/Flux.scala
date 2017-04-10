package reactor.core.scala.publisher

import java.lang.{Iterable => JIterable, Long => JLong}
import java.util
import java.util.concurrent.Callable
import java.util.function.{BiFunction, Consumer, Function, Supplier}
import java.util.logging.Level
import java.util.{Comparator, List => JList}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.Disposable
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{BufferOverflowStrategy, FluxSink, Signal, SignalType, SynchronousSink, Flux => JFlux, GroupedFlux => JGroupedFlux}
import reactor.core.scheduler.{Scheduler, TimedScheduler}
import reactor.util.Logger
import reactor.util.function.Tuple2

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration


/**
  * A Reactive Streams [[Publisher]] with rx operators that emits 0 to N elements, and then completes
  * (successfully or with an error).
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/flux.png" alt="">
  * <p>
  *
  * <p>It is intended to be used in implementations and return types. Input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>If it is known that the underlying [[Publisher]] will emit 0 or 1 element, [[Mono]] should be used
  * instead.
  *
  * <p>Note that using state in the lambdas used within Flux operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the element type of this Reactive Streams [[Publisher]]
  * @see [[Mono]]
  */
class Flux[T] private[publisher](private[publisher] val jFlux: JFlux[T]) extends Publisher[T] with MapablePublisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)

  /**
    *
    * Emit a single boolean true if all values of this sequence match
    * the given predicate.
    * <p>
    * The implementation uses short-circuit logic and completes with false if
    * the predicate doesn't match a value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/all.png" alt="">
    *
    * @param predicate the predicate to match all emitted items
    * @return a [[Mono]] of all evaluations
    */
  final def all(predicate: T => Boolean): Mono[Boolean] = Mono(jFlux.all(predicate)).map(Boolean2boolean)

  /**
    * Emit a single boolean true if any of the values of this [[Flux]] sequence match
    * the predicate.
    * <p>
    * The implementation uses short-circuit logic and completes with true if
    * the predicate matches a value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/any.png" alt="">
    *
    * @param predicate predicate tested upon values
    * @return a new [[Flux]] with <code>true</code> if any value satisfies a predicate and <code>false</code>
    *         otherwise
    *
    */
  final def any(predicate: T => Boolean): Mono[Boolean] = Mono(jFlux.any(predicate)).map(Boolean2boolean)

  /**
    * Immediately apply the given transformation to this [[Flux]] in order to generate a target type.
    *
    * `flux.as(Mono::from).subscribe()`
    *
    * @param transformer the [[Function1]] to immediately map this [[Flux]]
    *                    into a target type
    *                    instance.
    * @tparam P the returned type
    * @return a an instance of P
    * @see [[Flux.compose]] for a bounded conversion to [[Publisher]]
    */
  final def as[P](transformer: Flux[T] => P): P = jFlux.as(transformer)

  /**
    * Intercepts the onSubscribe call and makes sure calls to Subscription methods
    * only happen after the child Subscriber has returned from its onSubscribe method.
    *
    * <p>This helps with child Subscribers that don't expect a recursive call from
    * onSubscribe into their onNext because, for example, they request immediately from
    * their onSubscribe but don't finish their preparation before that and onNext
    * runs into a half-prepared state. This can happen with non Reactor mentality based Subscribers.
    *
    * @return non reentrant onSubscribe [[Flux]]
    */
  //  TODO: How to test?
  final def awaitOnSubscribe() = Flux(jFlux.awaitOnSubscribe())

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirst(): Option[T] = Option(jFlux.blockFirst())

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @param d max duration timeout to wait for.
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirst(d: Duration): Option[T] = Option(jFlux.blockFirst(d))

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @param timeout max duration timeout in millis to wait for.
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirstMillis(timeout: Long) = Option(jFlux.blockFirstMillis(timeout))

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLast() = Option(jFlux.blockLast())

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @param d max duration timeout to wait for.
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLast(d: Duration) = Option(jFlux.blockLast(d))

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @param timeout max duration timeout in millis to wait for.
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLastMillis(timeout: Long) = Option(jFlux.blockLastMillis(timeout))

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] on complete only.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffer.png"
    * alt="">
    *
    * @return a buffered [[Flux]] of at most one [[Seq]]
    * @see #collectList() for an alternative collecting algorithm returning [[Mono]]
    */
  final def buffer(): Flux[Seq[T]] = {
    Flux(jFlux.buffer()).map(_.asScala)
  }

  /**
    * Collect incoming values into multiple [[Seq]] buckets that will be pushed into the returned [[Flux]]
    * when the given max size is reached or onComplete is received.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param maxSize the maximum collected size
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def buffer(maxSize: Int): Flux[Seq[T]] = Flux(jFlux.buffer(maxSize)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] buckets that will be
    * pushed into the returned [[Flux]]
    * when the given max size is reached or onComplete is received.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param maxSize        the maximum collected size
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def buffer[C <: mutable.ListBuffer[T]](maxSize: Int, bufferSupplier: () => C): Flux[mutable.Seq[T]] = {
    Flux(jFlux.buffer(maxSize, new Supplier[JList[T]] {
      override def get(): JList[T] = bufferSupplier().asJava
    })).map(_.asScala)
  }

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into the returned [[Flux]] when the
    * given max size is reached or onComplete is received. A new container [[Seq]] will be created every given
    * skip count.
    * <p>
    * When Skip > Max Size : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskip.png"
    * alt="">
    * <p>
    * When Skip < Max Size : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskipover.png"
    * alt="">
    * <p>
    * When Skip == Max Size : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param skip    the number of items to skip before creating a new bucket
    * @param maxSize the max collected size
    * @return a microbatched [[Flux]] of possibly overlapped or gapped [[Seq]]
    */
  final def buffer(maxSize: Int, skip: Int): Flux[Seq[T]] = Flux(jFlux.buffer(maxSize, skip)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[mutable.Seq]] that will be pushed into
    * the returned [[Flux]] when the
    * given max size is reached or onComplete is received. A new container
    * [[mutable.Seq]] will be created every given
    * skip count.
    * <p>
    * When Skip > Max Size : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskip.png"
    * alt="">
    * <p>
    * When Skip < Max Size : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskipover.png"
    * alt="">
    * <p>
    * When Skip == Max Size : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param skip           the number of items to skip before creating a new bucket
    * @param maxSize        the max collected size
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[mutable.Seq]] type
    * @return a microbatched [[Flux]] of possibly overlapped or gapped
    *         [[mutable.Seq]]
    */
  final def buffer[C <: ListBuffer[T]](maxSize: Int, skip: Int, bufferSupplier: () => C): Flux[mutable.Seq[T]] = Flux(jFlux.buffer(maxSize, skip, new Supplier[JList[T]] {
    override def get(): JList[T] = bufferSupplier().asJava
  })).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given [[Publisher]] signals.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferboundary.png"
    * alt="">
    *
    * @param other the other [[Publisher]] to subscribe to for emiting and recycling receiving bucket
    * @return a microbatched [[Flux]] of [[Seq]] delimited by a
    *         [[Publisher]]
    */
  //TODO: How to test this?
  final def buffer(other: Publisher[_]): Flux[Seq[T]] = Flux(jFlux.buffer(other)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given [[Publisher]] signals.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferboundary.png"
    * alt="">
    *
    * @param other          the other [[Publisher]]  to subscribe to for emitting and recycling receiving bucket
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Flux]] of [[Seq]] delimited by a [[Publisher]]
    */
  //TODO: How to test?
  final def buffer[C <: ListBuffer[T]](other: Publisher[_], bufferSupplier: () => C): Flux[Seq[T]] = Flux(jFlux.buffer(other, new Supplier[JList[T]] {
    override def get(): JList[T] = bufferSupplier().asJava
  })).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given [[Publisher]] signals. Each
    * [[Seq]] bucket will last until the mapped [[Publisher]] receiving the boundary signal emits, thus releasing the
    * bucket to the returned [[Flux]].
    * <p>
    * When Open signal is strictly not overlapping Close signal : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferopenclose.png"
    * alt="">
    * <p>
    * When Open signal is strictly more frequent than Close signal : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferopencloseover.png"
    * alt="">
    * <p>
    * When Open signal is exactly coordinated with Close signal : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferboundary.png"
    * alt="">
    *
    * @param bucketOpening a [[Publisher]] to subscribe to for creating new receiving bucket signals.
    * @param closeSelector a [[Publisher]] factory provided the opening signal and returning a [[Publisher]] to
    *                      subscribe to for emitting relative bucket.
    * @tparam U the element type of the bucket-opening sequence
    * @tparam V the element type of the bucket-closing sequence
    * @return a microbatched [[Flux]] of [[Seq]] delimited by an opening [[Publisher]] and a relative
    *         closing [[Publisher]]
    */
  //TODO: How to test?
  final def buffer[U, V](bucketOpening: Publisher[U], closeSelector: U => Publisher[V]): Flux[Seq[T]] = Flux(jFlux.buffer[U, V](bucketOpening, closeSelector)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given [[Publisher]] signals. Each [[Seq]]
    * bucket will last until the mapped [[Publisher]] receiving the boundary signal emits, thus releasing the
    * bucket to the returned [[Flux]].
    * <p>
    * When Open signal is strictly not overlapping Close signal : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferopenclose.png"
    * alt="">
    * <p>
    * When Open signal is strictly more frequent than Close signal : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferopencloseover.png"
    * alt="">
    * <p>
    * When Open signal is exactly coordinated with Close signal : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/bufferboundary.png"
    * alt="">
    *
    * @param bucketOpening  a [[Publisher]] to subscribe to for creating new receiving bucket signals.
    * @param closeSelector  a [[Publisher]] factory provided the opening signal and returning a [[Publisher]] to
    *                       subscribe to for emitting relative bucket.
    * @param bufferSupplier the collection to use for each data segment
    * @tparam U the element type of the bucket-opening sequence
    * @tparam V the element type of the bucket-closing sequence
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Flux]] of [[Seq]] delimited by an opening [[Publisher]] and a relative
    *         closing [[Publisher]]
    */
  //TODO: How to test?
  final def buffer[U, V, C <: ListBuffer[T]](bucketOpening: Publisher[U],
                                             closeSelector: U => Publisher[V],
                                             bufferSupplier: () => C): Flux[Seq[T]] = Flux(jFlux.buffer(bucketOpening, closeSelector, new Supplier[JList[T]] {
    override def get(): JList[T] = bufferSupplier().asJava
  })).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into the returned [[Flux]] every
    * timespan.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan the duration to use to release a buffered list
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period
    */
  final def buffer(timespan: Duration): Flux[Seq[T]] = Flux(jFlux.buffer(timespan)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given `timeshift` period. Each [[Seq]]
    * bucket will last until the `timespan` has elapsed, thus releasing the bucket to the returned [[Flux]].
    * <p>
    * When timeshift > timespan : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshift.png"
    * alt="">
    * <p>
    * When timeshift < timespan : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshiftover.png"
    * alt="">
    * <p>
    * When timeshift == timespan : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan  the duration to use to release buffered lists
    * @param timeshift the duration to use to create a new bucket
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period timeshift and sized by timespan
    */
  final def buffer(timespan: Duration, timeshift: Duration): Flux[Seq[T]] = Flux(jFlux.buffer(timespan, timeshift)).map(_.asScala)

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] every timespan OR
    * maxSize items.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespansize.png"
    * alt="">
    *
    * @param maxSize  the max collected size
    * @param timespan the timeout to use to release a buffered list
    * @return a microbatched [[Flux]] of [[Seq]] delimited by given size or a given period timeout
    */
  final def bufferTimeout(maxSize: Int, timespan: Duration): Flux[Seq[T]] = Flux(jFlux.bufferTimeout(maxSize, timespan)).map(_.asScala)

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] every timespan OR
    * maxSize items.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespansize.png"
    * alt="">
    *
    * @param maxSize        the max collected size
    * @param timespan       the timeout to use to release a buffered list
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Flux]] of [[Seq]] delimited by given size or a given period timeout
    */
  //TODO: Test this
  final def bufferTimeout[C <: ListBuffer[T]](maxSize: Int, timespan: Duration, bufferSupplier: () => C): Flux[Seq[T]] = Flux(jFlux.bufferTimeout(maxSize, timespan, new Supplier[JList[T]] {
    override def get(): JList[T] = bufferSupplier().asJava
  })).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into the returned [[Flux]] every
    * timespan.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan the duration to use to release a buffered list in milliseconds
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period
    */
  final def bufferMillis(timespan: Long): Flux[Seq[T]] = Flux(jFlux.bufferMillis(timespan)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into the returned [[Flux]] every
    * timespan.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan theduration to use to release a buffered list in milliseconds
    * @param timer    the [[TimedScheduler]] to run on
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period
    */
  final def bufferMillis(timespan: Long, timer: TimedScheduler): Flux[Seq[T]] =
    Flux(jFlux.bufferMillis(timespan, timer)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given `timeshift` period. Each [[Seq]]
    * bucket will last until the `timespan` has elapsed, thus releasing the bucket to the returned [[Flux]].
    * <p>
    * When timeshift > timespan : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshift.png"
    * alt="">
    * <p>
    * When timeshift < timespan : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshiftover.png"
    * alt="">
    * <p>
    * When timeshift == timespan : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan  the duration to use to release buffered lists
    * @param timeshift the duration to use to create a new bucket
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period timeshift and sized by timespan
    */
  final def bufferMillis(timespan: Long, timeshift: Long): Flux[Seq[T]] = Flux(jFlux.bufferMillis(timespan, timeshift)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] delimited by the given `timeshift` period. Each [[Seq]]
    * bucket will last until the `timespan` has elapsed, thus releasing the bucket to the returned [[Flux]].
    * <p>
    * When timeshift > timespan : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshift.png"
    * alt="">
    * <p>
    * When timeshift < timespan : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimeshiftover.png"
    * alt="">
    * <p>
    * When timeshift == timespan : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespan.png"
    * alt="">
    *
    * @param timespan  the duration to use to release buffered lists
    * @param timeshift the duration to use to create a new bucket
    * @param timer     the [[TimedScheduler]] to run on
    * @return a microbatched [[Flux]] of [[Seq]] delimited by the given period timeshift and sized by timespan
    */
  final def bufferMillis(timespan: Long, timeshift: Long, timer: TimedScheduler): Flux[Seq[T]] = Flux(jFlux.bufferMillis(timespan, timeshift, timer)).map(_.asScala)

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] every timespan OR
    * maxSize items.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespansize.png"
    * alt="">
    *
    * @param maxSize  the max collected size
    * @param timespan the timeout in milliseconds to use to release a buffered list
    * @return a microbatched [[Flux]] of [[Seq]] delimited by given size or a given period timeout
    */
  final def bufferTimeoutMillis(maxSize: Int, timespan: Long): Flux[Seq[T]] = Flux(jFlux.bufferTimeoutMillis(maxSize, timespan)).map(_.asScala)

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] every timespan OR
    * maxSize items
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespansize.png"
    * alt="">
    *
    * @param maxSize  the max collected size
    * @param timespan the timeout to use to release a buffered list
    * @param timer    the [[TimedScheduler]] to run on
    * @return a microbatched [[Flux]] of [[Seq]] delimited by given size or a given period timeout
    */
  final def bufferTimeoutMillis(maxSize: Int, timespan: Long, timer: TimedScheduler): Flux[Seq[T]] = Flux(jFlux.bufferTimeoutMillis(maxSize, timespan, timer)).map(_.asScala)

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] every timespan OR
    * maxSize items
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffertimespansize.png"
    * alt="">
    *
    * @param maxSize        the max collected size
    * @param timespan       the timeout to use to release a buffered collection
    * @param timer          the [[TimedScheduler]] to run on
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Seq]] delimited by given size or a given period timeout
    */
  final def bufferTimeoutMillis[C <: ListBuffer[T]](maxSize: Int, timespan: Long, timer: TimedScheduler, bufferSupplier: () => C): Flux[Seq[T]] = Flux(jFlux.bufferTimeoutMillis(maxSize, timespan, timer, new Supplier[JList[T]] {
    override def get(): JList[T] = bufferSupplier().asJava
  })).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into
    * the returned [[Flux]] each time the given predicate returns true. Note that
    * the element that triggers the predicate to return true (and thus closes a buffer)
    * is included as last element in the emitted buffer.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    * <p>
    * On completion, if the latest buffer is non-empty and has not been closed it is
    * emitted. However, such a "partial" buffer isn't emitted in case of onError
    * termination.
    *
    * @param predicate a predicate that triggers the next buffer when it becomes true.
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def bufferUntil(predicate: T => Boolean): Flux[Seq[T]] = Flux(jFlux.bufferUntil(predicate)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into
    * the returned [[Flux]] each time the given predicate returns true. Note that
    * the buffer into which the element that triggers the predicate to return true
    * (and thus closes a buffer) is included depends on the `cutBefore` parameter:
    * set it to true to include the boundary element in the newly opened buffer, false to
    * include it in the closed buffer (as in [[Flux.bufferUntil]]).
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    * <p>
    * On completion, if the latest buffer is non-empty and has not been closed it is
    * emitted. However, such a "partial" buffer isn't emitted in case of onError
    * termination.
    *
    * @param predicate a predicate that triggers the next buffer when it becomes true.
    * @param cutBefore set to true to include the triggering element in the new buffer rather than the old.
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def bufferUntil(predicate: T => Boolean, cutBefore: Boolean): Flux[Seq[T]] = Flux(jFlux.bufferUntil(predicate, cutBefore)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into
    * the returned [[Flux]]. Each buffer continues aggregating values while the
    * given predicate returns true, and a new buffer is created as soon as the
    * predicate returns false... Note that the element that triggers the predicate
    * to return false (and thus closes a buffer) is NOT included in any emitted buffer.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    * <p>
    * On completion, if the latest buffer is non-empty and has not been closed it is
    * emitted. However, such a "partial" buffer isn't emitted in case of onError
    * termination.
    *
    * @param predicate a predicate that triggers the next buffer when it becomes false.
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def bufferWhile(predicate: T => Boolean): Flux[Seq[T]] = Flux(jFlux.bufferWhile(predicate)).map(_.asScala)

  /**
    * Turn this [[Flux]] into a hot source and cache last emitted signals for further [[Subscriber]]. Will
    * retain up an unbounded volume of onNext signals. Completion and Error will also be
    * replayed.
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/cache.png"
    * alt="">
    *
    * @return a replaying [[Flux]]
    */
  final def cache() = Flux(jFlux.cache())

  /**
    * Turn this [[Flux]] into a hot source and cache last emitted signals for further [[Subscriber]].
    * Will retain up to the given history size onNext signals. Completion and Error will also be
    * replayed.
    *
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/cache.png" alt="">
    *
    * @param history number of events retained in history excluding complete and error
    * @return a replaying [[Flux]]
    *
    */
  final def cache(history: Int) = Flux(jFlux.cache(history))

  /**
    * Turn this [[Flux]] into a hot source and cache last emitted signals for further
    * [[Subscriber]]. Will retain an unbounded history with per-item expiry timeout
    * Completion and Error will also be replayed.
    * <p>
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/cache.png"
    * alt="">
    *
    * @param ttl Time-to-live for each cached item.
    * @return a replaying [[Flux]]
    */
  final def cache(ttl: Duration) = Flux(jFlux.cache(ttl))

  /**
    * Turn this [[Flux]] into a hot source and cache last emitted signals for further
    * [[Subscriber]]. Will retain up to the given history size  with per-item expiry
    * timeout.
    * <p>
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/cache.png"
    * alt="">
    *
    * @param history number of events retained in history excluding complete and error
    * @param ttl     Time-to-live for each cached item.
    * @return a replaying [[Flux]]
    */
  final def cache(history: Int, ttl: Duration) = Flux(jFlux.cache(history, ttl))

  /**
    * Cast the current [[Flux]] produced type into a target produced type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/cast.png" alt="">
    *
    * @tparam E the [[Flux]] output type
    * @param clazz the target class to cast to
    * @return a casted [[Flux]]
    */
  final def cast[E](clazz: Class[E]) = Flux(jFlux.cast(clazz))

  /**
    * Prepare this [[Flux]] so that subscribers will cancel from it on a
    * specified
    * [[Scheduler]].
    *
    * @param scheduler the [[Scheduler]] to signal cancel  on
    * @return a scheduled cancel [[Flux]]
    */
  //  TODO: how to test this?
  final def cancelOn(scheduler: Scheduler) = Flux(jFlux.cancelOn(scheduler))

  /**
    * Activate assembly tracing for this particular [[Flux]], in case of an error
    * upstream of the checkpoint.
    * <p>
    * It should be placed towards the end of the reactive chain, as errors
    * triggered downstream of it cannot be observed and augmented with assembly trace.
    *
    * @return the assembly tracing [[Flux]].
    */
  //  TODO: how to test?
  final def checkpoint() = Flux(jFlux.checkpoint())

  /**
    * Activate assembly tracing for this particular [[Flux]] and give it
    * a description that will be reflected in the assembly traceback in case
    * of an error upstream of the checkpoint.
    * <p>
    * It should be placed towards the end of the reactive chain, as errors
    * triggered downstream of it cannot be observed and augmented with assembly trace.
    * <p>
    * The description could for example be a meaningful name for the assembled
    * flux or a wider correlation ID.
    *
    * @param description a description to include in the assembly traceback.
    * @return the assembly tracing [[Flux]].
    */
  //  TODO: how to test?
  final def checkpoint(description: String) = Flux(jFlux.checkpoint(description))

  /**
    * Collect the [[Flux]] sequence with the given collector and supplied container on subscribe.
    * The collected result will be emitted when this sequence completes.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collect.png" alt="">
    *
    * @tparam E the [[Flux]] collected container type
    * @param containerSupplier the supplier of the container instance for each Subscriber
    * @param collector         the consumer of both the container instance and the current value
    * @return a [[Mono]] sequence of the collected value on complete
    *
    */
  final def collect[E](containerSupplier: () => E, collector: (E, T) => Unit) = Mono(jFlux.collect(containerSupplier, collector: JBiConsumer[E, T]))

  /**
    * Accumulate this [[Flux]] sequence in a [[Seq]] that is emitted to the returned [[Mono]] on
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectlist.png" alt="">
    *
    * @return a [[Mono]] of all values from this [[Flux]]
    *
    *
    */
  final def collectSeq(): Mono[Seq[T]] = Mono(jFlux.collectList()).map(_.asScala)

  /**
    * Convert all this
    * [[Flux]] sequence into a hashed map where the key is extracted by the given [[Function1]] and the
    * value will be the most recent emitted item for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmap.png" alt="">
    *
    * @param keyExtractor a [[Function1]] to route items into a keyed [[Traversable]]
    * @tparam K the key extracted from each value of this Flux instance
    * @return a [[Mono]] of all last matched key-values from this [[Flux]]
    *
    */
  final def collectMap[K](keyExtractor: T => K): Mono[Map[K, T]] = Mono(jFlux.collectMap[K](keyExtractor)).map(_.asScala.toMap)

  /**
    * Convert all this [[Flux]] sequence into a hashed map where the key is extracted by the given function and the value will be
    * the most recent extracted item for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmap.png" alt="">
    *
    * @param keyExtractor   a [[Function1]] to route items into a keyed [[Traversable]]
    * @param valueExtractor a [[Function1]] to select the data to store from each item
    * @tparam K the key extracted from each value of this Flux instance
    * @tparam V the value extracted from each value of this Flux instance
    * @return a [[Mono]] of all last matched key-values from this [[Flux]]
    *
    */
  final def collectMap[K, V](keyExtractor: T => K, valueExtractor: T => V): Mono[Map[K, V]] = Mono(jFlux.collectMap[K, V](keyExtractor, valueExtractor)).map(_.asScala.toMap)

  /**
    * Convert all this [[Flux]] sequence into a supplied map where the key is extracted by the given function and the value will
    * be the most recent extracted item for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmap.png" alt="">
    *
    * @param keyExtractor   a [[Function1]] to route items into a keyed [[Traversable]]
    * @param valueExtractor a [[Function1]] to select the data to store from each item
    * @param mapSupplier    a [[mutable.Map]] factory called for each [[Subscriber]]
    * @tparam K the key extracted from each value of this Flux instance
    * @tparam V the value extracted from each value of this Flux instance
    * @return a [[Mono]] of all last matched key-values from this [[Flux]]
    *
    */
  final def collectMap[K, V](keyExtractor: T => K, valueExtractor: T => V, mapSupplier: () => mutable.Map[K, V]): Mono[Map[K, V]] = Mono(jFlux.collectMap[K, V](keyExtractor, valueExtractor, new Supplier[util.Map[K, V]] {
    override def get(): util.Map[K, V] = mapSupplier().asJava
  })).map(_.asScala.toMap)

  /**
    * Convert this [[Flux]] sequence into a hashed map where the key is extracted by the given function and the value will be
    * all the emitted item for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmultimap.png" alt="">
    *
    * @param keyExtractor a [[Function1]] to route items into a keyed [[Traversable]]
    * @tparam K the key extracted from each value of this Flux instance
    * @return a [[Mono]] of all matched key-values from this [[Flux]]
    *
    */
  final def collectMultimap[K](keyExtractor: T => K): Mono[Map[K, Traversable[T]]] = Mono(jFlux.collectMultimap[K](keyExtractor)).map(_.asScala.toMap.map {
    case (k, v) => k -> v.asScala.toSeq
  })

  /**
    * Convert this [[Flux]] sequence into a hashed map where the key is extracted by the given function and the value will be
    * all the extracted items for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmultimap.png" alt="">
    *
    * @param keyExtractor   a [[Function1]] to route items into a keyed [[Traversable]]
    * @param valueExtractor a [[Function1]] to select the data to store from each item
    * @tparam K the key extracted from each value of this Flux instance
    * @tparam V the value extracted from each value of this Flux instance
    * @return a [[Mono]] of all matched key-values from this [[Flux]]
    *
    */
  final def collectMultimap[K, V](keyExtractor: T => K, valueExtractor: T => V): Mono[Map[K, Traversable[V]]] = Mono(jFlux.collectMultimap[K, V](keyExtractor, valueExtractor)).map(_.asScala.toMap.map {
    case (k, v) => k -> v.asScala.toSeq
  })

  /**
    * Convert this [[Flux]] sequence into a supplied map where the key is extracted by the given function and the value will
    * be all the extracted items for this key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectmultimap.png" alt="">
    *
    * @param keyExtractor   a [[Function1]] to route items into a keyed [[Traversable]]
    * @param valueExtractor a [[Function1]] to select the data to store from each item
    * @param mapSupplier    a [[Map]] factory called for each [[Subscriber]]
    * @tparam K the key extracted from each value of this Flux instance
    * @tparam V the value extracted from each value of this Flux instance
    * @return a [[Mono]] of all matched key-values from this [[Flux]]
    *
    */
  final def collectMultimap[K, V](keyExtractor: T => K, valueExtractor: T => V, mapSupplier: () => mutable.Map[K, util.Collection[V]]): Mono[Map[K, Traversable[V]]] = Mono(jFlux.collectMultimap[K, V](keyExtractor, valueExtractor,
    new Supplier[util.Map[K, util.Collection[V]]] {
      override def get(): util.Map[K, util.Collection[V]] = {
        mapSupplier().asJava
      }
    })).map(_.asScala.toMap.mapValues(vs => vs.asScala.toSeq))

  /**
    * Accumulate and sort this [[Flux]] sequence in a [[Seq]] that is emitted to the returned [[Mono]] on
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectsortedlist.png" alt="">
    *
    * @return a [[Mono]] of all sorted values from this [[Flux]]
    *
    */
  final def collectSortedSeq(): Mono[Seq[T]] = Mono(jFlux.collectSortedList()).map(_.asScala)

  /**
    * Accumulate and sort using the given comparator this
    * [[Flux]] sequence in a [[Seq]] that is emitted to the returned [[Mono]] on
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/collectsortedlist.png" alt="">
    *
    * @param ordering a [[Ordering]] to sort the items of this sequences
    * @return a [[Mono]] of all sorted values from this [[Flux]]
    *
    */
  final def collectSortedSeq(ordering: Ordering[T]): Mono[Seq[T]] = Mono(jFlux.collectSortedList(new Comparator[T] {
    override def compare(o1: T, o2: T): Int = ordering.compare(o1, o2)
  })).map(_.asScala)

  /**
    * Defer the transformation of this [[Flux]] in order to generate a target [[Flux]] for each
    * new [[Subscriber]].
    *
    * `flux.compose(Mono::from).subscribe()`
    *
    * @param transformer the [[Function1]] to map this [[Flux]] into a target [[Publisher]]
    *                    instance for each new subscriber
    * @tparam V the item type in the returned [[Publisher]]
    * @return a new [[Flux]]
    * @see [[Flux.transform]] for immmediate transformation of [[Flux]]
    * @see [[Flux.as]] for a loose conversion to an arbitrary type
    */
  final def compose[V](transformer: Flux[T] => Publisher[V]) = Flux(jFlux.compose[V](transformer))

  /**
    * Bind dynamic sequences given this input sequence like [[Flux.flatMap]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    * Errors will immediately short circuit current concat backlog.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png" alt="">
    *
    * @param mapper the function to transform this sequence of T into concatenated sequences of V
    * @tparam V the produced concatenated type
    * @return a concatenated [[Flux]]
    */
  final def concatMap[V](mapper: T => Publisher[_ <: V]) = Flux(jFlux.concatMap[V](mapper))

  /**
    * Bind dynamic sequences given this input sequence like [[Flux.flatMap]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    * Errors will immediately short circuit current concat backlog.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png" alt="">
    *
    * @param mapper   the function to transform this sequence of T into concatenated sequences of V
    * @param prefetch the inner source produced demand
    * @tparam V the produced concatenated type
    * @return a concatenated [[Flux]]
    */
  final def concatMap[V](mapper: T => Publisher[_ <: V], prefetch: Int) = Flux(jFlux.concatMap[V](mapper, prefetch))

  /**
    * Bind dynamic sequences given this input sequence like [[Flux.flatMap]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    *
    * Errors will be delayed after the current concat backlog.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png" alt="">
    *
    * @param mapper the function to transform this sequence of T into concatenated sequences of V
    * @tparam V the produced concatenated type
    * @return a concatenated [[Flux]]
    *
    */
  //  TODO: How to test?
  final def concatMapDelayError[V](mapper: T => Publisher[_ <: V]) = Flux(jFlux.concatMapDelayError[V](mapper))

  /**
    * Bind dynamic sequences given this input sequence like [[Flux.flatMap]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    *
    * Errors will be delayed after all concated sources terminate.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png" alt="">
    *
    * @param mapper   the function to transform this sequence of T into concatenated sequences of V
    * @param prefetch the inner source produced demand
    * @tparam V the produced concatenated type
    * @return a concatenated [[Flux]]
    *
    */
  //  TODO: How to test?
  final def concatMapDelayError[V](mapper: T => Publisher[_ <: V], prefetch: Int) = Flux(jFlux.concatMapDelayError[V](mapper, prefetch))

  /**
    * Bind dynamic sequences given this input sequence like [[Flux.flatMap]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    *
    * Errors will be delayed after the current concat backlog if delayUntilEnd is
    * false or after all sources if delayUntilEnd is true.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png" alt="">
    *
    * @param mapper        the function to transform this sequence of T into concatenated sequences of V
    * @param delayUntilEnd delay error until all sources have been consumed instead of
    *                      after the current source
    * @param prefetch      the inner source produced demand
    * @tparam V the produced concatenated type
    * @return a concatenated [[Flux]]
    *
    */
  final def concatMapDelayError[V](mapper: T => Publisher[_ <: V], delayUntilEnd: Boolean, prefetch: Int) = Flux(jFlux.concatMapDelayError[V](mapper, delayUntilEnd, prefetch))

  /**
    * Bind [[Iterable]] sequences given this input sequence like [[Flux.flatMapIterable]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    * <p>
    * Errors will be delayed after the current concat backlog.
    * <p>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png"
    * alt="">
    *
    * @param mapper the function to transform this sequence of T into concatenated sequences of R
    * @tparam R the produced concatenated type
    * @return a concatenated [[Flux]]
    */
  final def concatMapIterable[R](mapper: T => Iterable[_ <: R]): Flux[R] = Flux(jFlux.concatMapIterable(new Function[T, JIterable[R]] {
    override def apply(t: T): JIterable[R] = mapper(t)
  }))

  /**
    * Bind [[Iterable]] sequences given this input sequence like [[Flux.flatMapIterable]], but preserve
    * ordering and concatenate emissions instead of merging (no interleave).
    * <p>
    * Errors will be delayed after the current concat backlog.
    * <p>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concatmap.png"
    * alt="">
    *
    * @param mapper   the function to transform this sequence of T into concatenated sequences of R
    * @param prefetch the inner source produced demand
    * @tparam R the produced concatenated type
    * @return a concatenated [[Flux]]
    */
  final def concatMapIterable[R](mapper: T => Iterable[_ <: R], prefetch: Int): Flux[R] = Flux(jFlux.concatMapIterable(new Function[T, JIterable[R]] {
    override def apply(t: T): JIterable[R] = mapper(t)
  }, prefetch))

  /**
    * Concatenate emissions of this [[Flux]] with the provided [[Publisher]] (no interleave).
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/concat.png"
    * alt="">
    *
    * @param other the { @link Publisher} sequence to concat after this [[Flux]]
    * @return a concatenated [[Flux]]
    */
  final def concatWith(other: Publisher[_ <: T]) = Flux(jFlux.concatWith(other))

  /**
    * Counts the number of values in this [[Flux]].
    * The count will be emitted when onComplete is observed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/count.png" alt="">
    *
    * @return a new [[Mono]] of [[Long]] count
    */
  def count(): Mono[Long] = Mono[Long](jFlux.count().map(new Function[JLong, Long] {
    override def apply(t: JLong) = Long2long(t)
  }))

  /**
    * Provide a default unique value if this sequence is completed without any data
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[Flux]]
    */
  final def defaultIfEmpty(defaultV: T) = new Flux[T](jFlux.defaultIfEmpty(defaultV))

  /**
    * Delay each of this [[Flux]] elements ([[Subscriber.onNext]] signals)
    * by a given duration.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delayonnext.png" alt="">
    *
    * @param delay duration to delay each [[Subscriber.onNext]] signal
    * @return a delayed [[Flux]]
    * @see #delaySubscription(Duration) delaySubscription to introduce a delay at the beginning of the sequence only
    */
  final def delayElements(delay: Duration) = Flux(jFlux.delayElements(delay))

  /**
    * Delay each of this [[Flux]] elements ([[Subscriber.onNext]] signals)
    * by a given duration, on a given [[Scheduler]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delayonnext.png" alt="">
    *
    * @param delay duration to delay each [[Subscriber.onNext]] signal
    * @param timer the [[Scheduler]] to use for delaying each signal
    * @return a delayed [[Flux]]
    * @see #delaySubscription(Duration) delaySubscription to introduce a delay at the beginning of the sequence only
    */
  final def delayElements(delay: Duration, timer: Scheduler) = Flux(jFlux.delayElements(delay, timer))

  /**
    * Delay each of this [[Flux]] elements ([[Subscriber.onNext]] signals)
    * by a given duration in milliseconds.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delayonnext.png" alt="">
    *
    * @param delay period to delay each [[Subscriber.onNext]] signal, in milliseconds
    * @return a delayed [[Flux]]
    */
  final def delayElementsMillis(delay: Long) = Flux(jFlux.delayElementsMillis(delay))

  /**
    * Delay each of this [[Flux]] elements ([[Subscriber.onNext]] signals)
    * by a given duration in milliseconds.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delayonnext.png" alt="">
    *
    * @param delay period to delay each [[Subscriber.onNext]] signal, in milliseconds
    * @param timer the timed scheduler to use for delaying each signal
    * @return a delayed [[Flux]]
    */
  final def delayElementsMillis(delay: Long, timer: TimedScheduler) = Flux(jFlux.delayElementsMillis(delay, timer))

  /**
    * Delay the [[Flux.subscribe subscription]] to this [[Flux]] source until the given
    * period elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delaysubscription.png" alt="">
    *
    * @param delay duration before subscribing this [[Flux]]
    * @return a delayed [[Flux]]
    *
    */
  final def delaySubscription(delay: Duration) = Flux(jFlux.delaySubscription(delay))

  /**
    * Delay the subscription to the main source until another Publisher
    * signals a value or completes.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delaysubscriptionp.png" alt="">
    *
    * @param subscriptionDelay a
    *                          [[Publisher]] to signal by next or complete this [[Flux.subscribe]]
    * @tparam U the other source type
    * @return a delayed [[Flux]]
    *
    */
  final def delaySubscription[U](subscriptionDelay: Publisher[U]) = Flux(jFlux.delaySubscription(subscriptionDelay))

  /**
    * Delay the [[Flux.subscribe subscription]] to this [[Flux]] source until the given
    * period elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delaysubscription.png" alt="">
    *
    * @param delay period in milliseconds before subscribing this [[Flux]]
    * @return a delayed [[Flux]]
    *
    */
  final def delaySubscriptionMillis(delay: Long) = Flux(jFlux.delaySubscriptionMillis(delay))

  /**
    * Delay the [[Flux.subscribe subscription]] to this [[Flux]] source until the given
    * period elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/delaysubscription.png" alt="">
    *
    * @param delay period in milliseconds before subscribing this [[Flux]]
    * @param timer the [[TimedScheduler]] to run on
    * @return a delayed [[Flux]]
    *
    */
  final def delaySubscriptionMillis(delay: Long, timer: TimedScheduler) = Flux(jFlux.delaySubscriptionMillis(delay, timer))

  /**
    * A "phantom-operator" working only if this
    * [[Flux]] is a emits onNext, onError or onComplete [[reactor.core.publisher.Signal]]. The relative [[Subscriber]]
    * callback will be invoked, error [[reactor.core.publisher.Signal]] will trigger onError and complete [[reactor.core.publisher.Signal]] will trigger
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/dematerialize.png" alt="">
    *
    * @tparam X the dematerialized type
    * @return a dematerialized [[Flux]]
    */
  final def dematerialize[X](): Flux[X] = Flux(jFlux.dematerialize[X]())

  /**
    * For each [[Subscriber]], tracks this [[Flux]] values that have been seen and
    * filters out duplicates.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/distinct.png" alt="">
    *
    * @return a filtering [[Flux]] with unique values
    */
  final def distinct() = Flux(jFlux.distinct())

  /**
    * For each [[Subscriber]], tracks this [[Flux]] values that have been seen and
    * filters out duplicates given the extracted key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/distinctk.png" alt="">
    *
    * @param keySelector function to compute comparison key for each element
    * @tparam V the type of the key extracted from each value in this sequence
    * @return a filtering [[Flux]] with values having distinct keys
    */
  final def distinct[V](keySelector: T => V) = Flux(jFlux.distinct[V](keySelector))

  /**
    * Filters out subsequent and repeated elements.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/distinctuntilchanged.png" alt="">
    *
    * @return a filtering [[Flux]] with conflated repeated elements
    */
  final def distinctUntilChanged() = Flux(jFlux.distinctUntilChanged())

  /**
    * Filters out subsequent and repeated elements provided a matching extracted key.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/distinctuntilchangedk.png" alt="">
    *
    * @param keySelector function to compute comparison key for each element
    * @tparam V the type of the key extracted from each value in this sequence
    * @return a filtering [[Flux]] with conflated repeated elements given a comparison key
    */
  final def distinctUntilChanged[V](keySelector: T => V) = Flux(jFlux.distinctUntilChanged[V](keySelector))

  /**
    * Triggered after the [[Flux]] terminates, either by completing downstream successfully or with an error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doafterterminate.png" alt="">
    * <p>
    *
    * @param afterTerminate the callback to call after [[Subscriber.onComplete]] or [[Subscriber.onError]]
    * @return an observed  [[Flux]]
    */
  final def doAfterTerminate(afterTerminate: () => Unit) = Flux(jFlux.doAfterTerminate(afterTerminate))

  /**
    * Triggered when the [[Flux]] is cancelled.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/dooncancel.png" alt="">
    * <p>
    *
    * @param onCancel the callback to call on [[Subscription.cancel]]
    * @return an observed  [[Flux]]
    */
  final def doOnCancel(onCancel: () => Unit) = Flux(jFlux.doOnCancel(onCancel))

  /**
    * Triggered when the [[Flux]] completes successfully.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/dooncomplete.png" alt="">
    * <p>
    *
    * @param onComplete the callback to call on [[Subscriber#onComplete]]
    * @return an observed  [[Flux]]
    */
  final def doOnComplete(onComplete: () => Unit) = Flux(jFlux.doOnComplete(onComplete))

  /**
    * Triggers side-effects when the [[Flux]] emits an item, fails with an error
    * or completes successfully. All these events are represented as a [[Signal]]
    * that is passed to the side-effect callback. Note that this is an advanced operator,
    * typically used for monitoring of a Flux.
    *
    * @param signalConsumer the mandatory callback to call on
    *                       [[Subscriber.onNext]], [[Subscriber.onError]] and
    *                       [[Subscriber#onComplete]]
    * @return an observed [[Flux]]
    * @see [[Flux.doOnNext]]
    * @see [[Flux.doOnError]]
    * @see [[Flux.doOnComplete]]
    * @see [[Flux.materialize]]
    * @see [[Signal]]
    */
  final def doOnEach(signalConsumer: Signal[T] => Unit) = Flux(jFlux.doOnEach(signalConsumer))

  /**
    * Triggered when the [[Flux]] completes with an error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonerror.png" alt="">
    * <p>
    *
    * @param onError the callback to call on [[Subscriber.onError]]
    * @return an observed  [[Flux]]
    */
  final def doOnError(onError: Throwable => Unit) = Flux(jFlux.doOnError(onError))

  /**
    * Triggered when the [[Flux]] completes with an error matching the given exception type.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonerrorw.png" alt="">
    *
    * @param exceptionType the type of exceptions to handle
    * @param onError       the error handler for each error
    * @tparam E type of the error to handle
    * @return an observed  [[Flux]]
    *
    */
  final def doOnError[E <: Throwable](exceptionType: Class[E], onError: E => Unit) = Flux(jFlux.doOnError[E](exceptionType, onError))

  /**
    * Triggered when the [[Flux]] completes with an error matching the given exception.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonerrorw.png" alt="">
    *
    * @param predicate the matcher for exceptions to handle
    * @param onError   the error handler for each error
    * @return an observed  [[Flux]]
    *
    */
  final def doOnError(predicate: Throwable => Boolean, onError: Throwable => Unit) = Flux(jFlux.doOnError(predicate, onError))

  /**
    * Triggered when the [[Flux]] emits an item.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonnext.png" alt="">
    * <p>
    *
    * @param onNext the callback to call on [[Subscriber.onNext]]
    * @return an observed  [[Flux]]
    */
  final def doOnNext(onNext: T => Unit) = Flux(jFlux.doOnNext(onNext))

  /**
    * Attach a Long customer to this [[Flux]] that will observe any request to this [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonrequest.png" alt="">
    *
    * @param consumer the consumer to invoke on each request
    * @return an observed  [[Flux]]
    */
  final def doOnRequest(consumer: Long => Unit): Flux[T] = Flux(jFlux.doOnRequest(consumer))

  /**
    * Triggered when the [[Flux]] is subscribed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonsubscribe.png" alt="">
    * <p>
    *
    * @param onSubscribe the callback to call on [[org.reactivestreams.Subscriber.onSubscribe]]
    * @return an observed  [[Flux]]
    */
  final def doOnSubscribe(onSubscribe: Subscription => Unit): Flux[T] = Flux(jFlux.doOnSubscribe(onSubscribe))

  /**
    * Triggered when the [[Flux]] terminates, either by completing successfully or with an error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonterminate.png" alt="">
    * <p>
    *
    * @param onTerminate the callback to call on [[Subscriber.onComplete]] or [[Subscriber.onError]]
    * @return an observed  [[Flux]]
    */
  final def doOnTerminate(onTerminate: () => Unit) = Flux(jFlux.doOnTerminate(onTerminate))

  final def doFinally(onFinally: SignalType => Unit) = Flux(jFlux.doFinally(onFinally))

  /**
    * Map this [[Flux]] sequence into [[Tuple2]] of T1 [[Long]] timemillis and T2
    * `T` associated data. The timemillis corresponds to the elapsed time between the subscribe and the first
    * next signal OR between two next signals.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/elapsed.png" alt="">
    *
    * @return a transforming [[Flux]] that emits tuples of time elapsed in milliseconds and matching data
    */
  final def elapsed(): Flux[(Long, T)] = Flux(jFlux.elapsed()).map(tupleTwo2ScalaTuple2[JLong, T]).map {
    case (k, v) => (Long2long(k), v)
  }

  /**
    * Map this [[Flux]] sequence into [[Tuple2]] of T1
    * [[Long]] timemillis and T2 `T` associated data. The timemillis
    * corresponds to the elapsed time between the subscribe and the first next signal OR
    * between two next signals.
    * <p>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/elapsed.png"
    * alt="">
    *
    * @param scheduler the [[TimedScheduler]] to read time from
    * @return a transforming [[Flux]] that emits tuples of time elapsed in
    *         milliseconds and matching data
    */
  final def elapsed(scheduler: TimedScheduler): Flux[(Long, T)] = Flux(jFlux.elapsed(scheduler)).map(tupleTwo2ScalaTuple2[JLong, T]).map {
    case (k, v) => (Long2long(k), v)
  }

  /**
    * Emit only the element at the given index position or [[IndexOutOfBoundsException]] if the sequence is shorter.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/elementat.png" alt="">
    *
    * @param index index of an item
    * @return a [[Mono]] of the item at a specified index
    */
  final def elementAt(index: Int) = Mono(jFlux.elementAt(index))

  /**
    * Emit only the element at the given index position or signals a
    * default value if specified if the sequence is shorter.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/elementatd.png" alt="">
    *
    * @param index        index of an item
    * @param defaultValue supply a default value if not found
    * @return a [[Mono]] of the item at a specified index or a default value
    */
  final def elementAt(index: Int, defaultValue: T) = Mono(jFlux.elementAt(index, defaultValue))

  /**
    * Evaluate each accepted value against the given predicate T => Boolean. If the predicate test succeeds, the value is
    * passed into the new [[Flux]]. If the predicate test fails, the value is ignored and a request of 1 is
    * emitted.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/filter.png" alt="">
    *
    * @param p the [[Function1]] predicate to test values against
    * @return a new [[Flux]] containing only values that pass the predicate test
    */
  final def filter(p: T => Boolean) = Flux(jFlux.filter(p))

  /**
    * Emit from the fastest first sequence between this publisher and the given publisher
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/firstemitting.png" alt="">
    * <p>
    *
    * @param other the [[Publisher]] to race with
    * @return the fastest sequence
    * @see [[Flux.firstEmitting]]
    */
  final def firstEmittingWith(other: Publisher[_ <: T]) = Flux(jFlux.firstEmittingWith(other))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the emissions from those by
    * merging them into a single [[Flux]], so that they may interleave.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmap.png" alt="">
    * <p>
    *
    * @param mapper the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @tparam R the merged output sequence type
    * @return a new [[Flux]]
    */
  //  TODO: how to test if the result may not be sequence
  final def flatMap[R](mapper: T => Publisher[_ <: R]) = Flux(jFlux.flatMap[R](mapper))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the emissions from those by
    * merging them into a single [[Flux]], so that they may interleave. The concurrency argument allows to
    * control how many merged [[Publisher]] can happen in parallel.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapc.png" alt="">
    *
    * @param mapper      the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param concurrency the maximum in-flight elements from this [[Flux]] sequence
    * @tparam V the merged output sequence type
    * @return a new [[Flux]]
    *
    */
  //  TODO: How to test if the result may not be sequence
  final def flatMap[V](mapper: T => Publisher[_ <: V], concurrency: Int) = Flux(jFlux.flatMap[V](mapper, concurrency))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the emissions from those by
    * merging them into a single [[Flux]], so that they may interleave. The concurrency argument allows to
    * control how many merged [[Publisher]] can happen in parallel. The prefetch argument allows to give an
    * arbitrary prefetch size to the merged [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapc.png" alt="">
    *
    * @param mapper      the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param concurrency the maximum in-flight elements from this [[Flux]] sequence
    * @param prefetch    the maximum in-flight elements from each inner [[Publisher]] sequence
    * @tparam V the merged output sequence type
    * @return a merged [[Flux]]
    *
    */
  //  TODO: How to test if the result may not be sequence
  final def flatMap[V](mapper: T => Publisher[_ <: V], concurrency: Int, prefetch: Int) = Flux(jFlux.flatMap[V](mapper, concurrency, prefetch))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the emissions from those by
    * merging them into a single [[Flux]], so that they may interleave. The concurrency argument allows to
    * control how many merged [[Publisher]] can happen in parallel. The prefetch argument allows to give an
    * arbitrary prefetch size to the merged [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapc.png" alt="">
    *
    * @param mapper      the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param delayError  should any error be delayed after current merge backlog
    * @param concurrency the maximum in-flight elements from this [[Flux]] sequence
    * @param prefetch    the maximum in-flight elements from each inner [[Publisher]] sequence
    * @tparam V the merged output sequence type
    * @return a merged [[Flux]]
    *
    */
  //  TODO: How to test if the result may not be sequence
  final def flatMap[V](mapper: T => Publisher[_ <: V], delayError: Boolean, concurrency: Int, prefetch: Int) = Flux(jFlux.flatMap[V](mapper, delayError, concurrency, prefetch))

  /**
    * Transform the signals emitted by this [[Flux]] into Publishers, then flatten the emissions from those by
    * merging them into a single [[Flux]], so that they may interleave.
    * OnError will be transformed into completion signal after its mapping callback has been applied.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmaps.png" alt="">
    * <p>
    *
    * @param mapperOnNext     the [[Function1]] to call on next data and returning a sequence to merge
    * @param mapperOnError    the [[Function]] to call on error signal and returning a sequence to merge
    * @param mapperOnComplete the [[Function1]] to call on complete signal and returning a sequence to merge
    * @tparam R the output [[Publisher]] type target
    * @return a new [[Flux]]
    */
  final def flatMap[R](mapperOnNext: T => Publisher[_ <: R],
                       mapperOnError: Throwable => Publisher[_ <: R],
                       mapperOnComplete: () => Publisher[_ <: R]) = Flux(jFlux.flatMap[R](mapperOnNext, mapperOnError, mapperOnComplete))

  /**
    * Transform the items emitted by this [[Flux]] into [[Iterable]], then flatten the elements from those by
    * merging them into a single [[Flux]]. The prefetch argument allows to give an
    * arbitrary prefetch size to the merged [[Iterable]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper the [[Function1]] to transform input sequence into N sequences [[Iterable]]
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapIterable[R](mapper: T => Iterable[_ <: R]): Flux[R] = Flux(jFlux.flatMapIterable(new Function[T, JIterable[R]] {
    override def apply(t: T): JIterable[R] = mapper(t)
  }))

  /**
    * Transform the items emitted by this [[Flux]] into [[Iterable]], then flatten the emissions from those by
    * merging them into a single [[Flux]]. The prefetch argument allows to give an
    * arbitrary prefetch size to the merged [[Iterable]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper   the [[Function1]] to transform input sequence into N sequences [[Iterable]]
    * @param prefetch the maximum in-flight elements from each inner [[Iterable]] sequence
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapIterable[R](mapper: T => Iterable[_ <: R], prefetch: Int) = Flux(jFlux.flatMapIterable(new Function[T, JIterable[R]] {
    override def apply(t: T): JIterable[R] = mapper(t)
  }, prefetch))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the
    * emissions from those by merging them into a single [[Flux]], in order.
    * Unlike concatMap, transformed inner Publishers are subscribed to eagerly. Unlike
    * flatMap, their emitted elements are merged respecting the order of the original sequence.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapSequential[R](mapper: T => Publisher[_ <: R]) = Flux(jFlux.flatMapSequential[R](mapper))

  /**
    * Transform the items emitted by this [[Flux]] Flux} into Publishers, then flatten the
    * emissions from those by merging them into a single [[Flux]], in order.
    * Unlike concatMap, transformed inner Publishers are subscribed to eagerly. Unlike
    * flatMap, their emitted elements are merged respecting the order of the original
    * sequence. The concurrency argument allows to control how many merged
    * [[Publisher]] can happen in parallel.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper         the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param maxConcurrency the maximum in-flight elements from this [[Flux]] sequence
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapSequential[R](mapper: T => Publisher[_ <: R], maxConcurrency: Int) = Flux(jFlux.flatMapSequential[R](mapper, maxConcurrency))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the
    * emissions from those by merging them into a single [[Flux]], in order.
    * Unlike concatMap, transformed inner Publishers are subscribed to eagerly. Unlike
    * flatMap, their emitted elements are merged respecting the order of the original
    * sequence. The concurrency argument allows to control how many merged [[Publisher]]
    * can happen in parallel. The prefetch argument allows to give an arbitrary prefetch
    * size to the merged [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper         the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param maxConcurrency the maximum in-flight elements from this [[Flux]] sequence
    * @param prefetch       the maximum in-flight elements from each inner [[Publisher]] sequence
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapSequential[R](mapper: T => Publisher[_ <: R], maxConcurrency: Int, prefetch: Int) = Flux(jFlux.flatMapSequential[R](mapper, maxConcurrency, prefetch))

  /**
    * Transform the items emitted by this [[Flux]] into Publishers, then flatten the
    * emissions from those by merging them into a single [[Flux]], in order.
    * Unlike concatMap, transformed inner Publishers are subscribed to eagerly. Unlike
    * flatMap, their emitted elements are merged respecting the order of the original
    * sequence. The concurrency argument allows to control how many merged [[Publisher]]
    * can happen in parallel. The prefetch argument allows to give an arbitrary prefetch
    * size to the merged [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmapsequential.png" alt="">
    *
    * @param mapper         the [[Function1]] to transform input sequence into N sequences [[Publisher]]
    * @param delayError     should any error be delayed after current merge backlog
    * @param maxConcurrency the maximum in-flight elements from this [[Flux]] sequence
    * @param prefetch       the maximum in-flight elements from each inner [[Publisher]] sequence
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    */
  final def flatMapSequential[R](mapper: T => Publisher[_ <: R], delayError: Boolean, maxConcurrency: Int, prefetch: Int) = Flux(jFlux.flatMapSequential[R](mapper, delayError, maxConcurrency, prefetch))

  /**
    * The prefetch configuration of the [[Flux]]
    *
    * @return the prefetch configuration of the [[Flux]], -1L if unspecified
    */
  def getPrefetch: Long = jFlux.getPrefetch

  /**
    * Re-route this sequence into dynamically created [[Flux]] for each unique key evaluated by the given
    * key mapper.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/groupby.png" alt="">
    *
    * @param keyMapper the key mapping [[Function1]] that evaluates an incoming data and returns a key.
    * @tparam K the key type extracted from each value of this sequence
    * @return a [[Flux]] of [[GroupedFlux]] grouped sequences
    */
  final def groupBy[K](keyMapper: T => K): Flux[GroupedFlux[K, T]] = {
    val jFluxOfGroupedFlux: JFlux[JGroupedFlux[K, T]] = jFlux.groupBy(keyMapper)
    Flux(jFluxOfGroupedFlux.map((jGroupFlux: JGroupedFlux[K, T]) => GroupedFlux(jGroupFlux)))
  }

  /**
    * Re-route this sequence into dynamically created [[Flux]] for each unique key evaluated by the given
    * key mapper.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/groupby.png" alt="">
    *
    * @param keyMapper the key mapping [[Function1]] that evaluates an incoming data and returns a key.
    * @param prefetch  the number of values to prefetch from the source
    * @tparam K the key type extracted from each value of this sequence
    * @return a [[Flux]] of [[GroupedFlux]] grouped sequences
    */
  final def groupBy[K](keyMapper: T => K, prefetch: Int): Flux[GroupedFlux[K, T]] = {
    val jFluxOfGroupedFlux: JFlux[JGroupedFlux[K, T]] = jFlux.groupBy(keyMapper)
    Flux(jFluxOfGroupedFlux).map(GroupedFlux(_))
  }

  /**
    * Re-route this sequence into dynamically created [[Flux]] for each unique key evaluated by the given
    * key mapper. It will use the given value mapper to extract the element to route.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/groupby.png" alt="">
    *
    * @param keyMapper   the key mapping function that evaluates an incoming data and returns a key.
    * @param valueMapper the value mapping function that evaluates which data to extract for re-routing.
    * @tparam K the key type extracted from each value of this sequence
    * @tparam V the value type extracted from each value of this sequence
    * @return a [[Flux]] of [[GroupedFlux]] grouped sequences
    *
    */
  final def groupBy[K, V](keyMapper: T => K, valueMapper: T => V): Flux[GroupedFlux[K, V]] = {
    val jFluxOfGroupedFlux: JFlux[JGroupedFlux[K, V]] = jFlux.groupBy(keyMapper, valueMapper)
    Flux(jFluxOfGroupedFlux).map(GroupedFlux(_))
  }

  /**
    * Re-route this sequence into dynamically created [[Flux]] for each unique key evaluated by the given
    * key mapper. It will use the given value mapper to extract the element to route.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/groupby.png" alt="">
    *
    * @param keyMapper   the key mapping function that evaluates an incoming data and returns a key.
    * @param valueMapper the value mapping function that evaluates which data to extract for re-routing.
    * @param prefetch    the number of values to prefetch from the source
    * @tparam K the key type extracted from each value of this sequence
    * @tparam V the value type extracted from each value of this sequence
    * @return a [[Flux]] of [[GroupedFlux]] grouped sequences
    *
    */
  final def groupBy[K, V](keyMapper: T => K, valueMapper: T => V, prefetch: Int): Flux[GroupedFlux[K, V]] = {
    val jFluxOfGroupedFlux: JFlux[JGroupedFlux[K, V]] = jFlux.groupBy(keyMapper, valueMapper, prefetch)
    Flux(jFluxOfGroupedFlux).map(GroupedFlux(_))
  }

  /**
    * Returns a [[Flux]] that correlates two Publishers when they overlap in time
    * and groups the results.
    * <p>
    * There are no guarantees in what order the items get combined when multiple items from
    * one or both source Publishers overlap.
    * <p> Unlike [[Flux.join]], items from the right Publisher will be streamed
    * into the right resultSelector argument [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/groupjoin.png" alt="">
    *
    * @param other          the other Publisher to correlate items from the source Publisher with
    * @param leftEnd        a function that returns a Publisher whose emissions indicate the
    *                       duration of the values of the source Publisher
    * @param rightEnd       a function that returns a Publisher whose emissions indicate the
    *                       duration of the values of the `right` Publisher
    * @param resultSelector a function that takes an item emitted by each Publisher and returns the
    *                       value to be emitted by the resulting Publisher
    * @tparam TRight    the type of the right Publisher
    * @tparam TLeftEnd  this [[Flux]] timeout type
    * @tparam TRightEnd the right Publisher timeout type
    * @tparam R         the combined result type
    * @return a joining [[Flux]]
    */
  //  TODO: How to test this?
  final def groupJoin[TRight, TLeftEnd, TRightEnd, R](other: Publisher[_ <: TRight],
                                                      leftEnd: T => Publisher[TLeftEnd],
                                                      rightEnd: TRight => Publisher[TRightEnd],
                                                      resultSelector: (T, Flux[TRight]) => R) =
  Flux(jFlux.groupJoin[TRight, TLeftEnd, TRightEnd, R](other, leftEnd, rightEnd,
    new BiFunction[T, JFlux[TRight], R] {
      override def apply(t: T, u: JFlux[TRight]): R = resultSelector(t, Flux(u))
    }
  ))

  /**
    * Handle the items emitted by this [[Flux]] by calling a biconsumer with the
    * output sink for each onNext. At most one [[SynchronousSink.next]]
    * call must be performed and/or 0 or 1 [[SynchronousSink.error]] or
    * [[SynchronousSink.complete]].
    *
    * @param handler the handling `BiConsumer`
    * @tparam R the transformed type
    * @return a transformed [[Flux]]
    */
  final def handle[R](handler: (T, SynchronousSink[R]) => Unit): Flux[R] = Flux(jFlux.handle[R](handler))

  /**
    * Emit a single boolean true if any of the values of this [[Flux]] sequence match
    * the  constant.
    * <p>
    * The implementation uses short-circuit logic and completes with true if
    * the constant matches a value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/haselement.png" alt="">
    *
    * @param value constant compared to incoming signals
    * @return a new [[Mono]] with <code>true</code> if any value satisfies a predicate and <code>false</code>
    *         otherwise
    *
    */
  final def hasElement(value: T): Mono[Boolean] = Mono(jFlux.hasElement(value)).map(Boolean2boolean)

  /**
    * Emit a single boolean true if this [[Flux]] sequence has at least one element.
    * <p>
    * The implementation uses short-circuit logic and completes with true on onNext.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/haselements.png" alt="">
    *
    * @return a new [[Mono]] with <code>true</code> if any value is emitted and <code>false</code>
    *         otherwise
    */
  final def hasElements(): Mono[Boolean] = Mono(jFlux.hasElements).map(Boolean2boolean)

  /**
    * Hides the identities of this [[Flux]] and its [[Subscription]]
    * as well.
    *
    * @return a new [[Flux]] defeating any [[Publisher]] / [[Subscription]] feature-detection
    */
  //  TODO: How to test???
  final def hide() = Flux(jFlux.hide())

  /**
    * Ignores onNext signals (dropping them) and only reacts on termination.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/ignoreelements.png" alt="">
    * <p>
    *
    * @return a new completable [[Mono]].
    */
  final def ignoreElements() = Mono(jFlux.ignoreElements())

  /**
    * Returns a [[Flux]] that correlates two Publishers when they overlap in time
    * and groups the results.
    * <p>
    * There are no guarantees in what order the items get combined when multiple items from
    * one or both source Publishers overlap.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/join.png" alt="">
    *
    * @param other          the other Publisher to correlate items from the source Publisher with
    * @param leftEnd        a function that returns a Publisher whose emissions indicate the
    *                       duration of the values of the source Publisher
    * @param rightEnd       a function that returns a Publisher whose emissions indicate the
    *                       duration of the values of the { @code right} Publisher
    * @param resultSelector a function that takes an item emitted by each Publisher and returns the
    *                       value to be emitted by the resulting Publisher
    * @tparam TRight    the type of the right Publisher
    * @tparam TLeftEnd  this [[Flux]] timeout type
    * @tparam TRightEnd the right Publisher timeout type
    * @tparam R         the combined result type
    * @return a joining [[Flux]]
    */
  //  TODO: How to test this?
  final def join[TRight, TLeftEnd, TRightEnd, R](other: Publisher[_ <: TRight],
                                                 leftEnd: T => Publisher[TLeftEnd],
                                                 rightEnd: TRight => Publisher[TRightEnd],
                                                 resultSelector: (T, TRight) => R): Flux[R] = Flux(jFlux.join[TRight, TLeftEnd, TRightEnd, R](other, leftEnd, rightEnd, resultSelector))

  /**
    * Signal the last element observed before complete signal or emit
    * [[NoSuchElementException]] error if the source was empty.
    * For a passive version use [[Flux.takeLast]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/last.png" alt="">
    *
    * @return a limited [[Flux]]
    */
  final def last() = Mono(jFlux.last())

  /**
    * Signal the last element observed before complete signal or emit
    * the defaultValue if empty.
    * For a passive version use [[Flux.takeLast]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/last.png" alt="">
    *
    * @param defaultValue a single fallback item if this [[Flux]] is empty
    * @return a limited [[Flux]]
    */
  final def last(defaultValue: T) = Mono(jFlux.last(defaultValue))

  /**
    * Ensure that backpressure signals from downstream subscribers are capped at the
    * provided `prefetchRate` when propagated upstream, effectively rate limiting
    * the upstream [[Publisher]].
    * <p>
    * Typically used for scenarios where consumer(s) request a large amount of data
    * (eg. `Long.MaxValue`) but the data source behaves better or can be optimized
    * with smaller requests (eg. database paging, etc...). All data is still processed.
    * <p>
    * Equivalent to `flux.publishOn(Schedulers.immediate(), prefetchRate).subscribe()`
    *
    * @param prefetchRate the limit to apply to downstream's backpressure
    * @return a [[Flux]] limiting downstream's backpressure
    * @see [[[Flux.publishOn]]
    */
  //  TODO: How to test this?
  final def limitRate(prefetchRate: Int) = Flux(jFlux.limitRate(prefetchRate))

  /**
    * Observe all Reactive Streams signals and use [[Logger]] support to handle trace implementation. Default will
    * use [[Level.INFO]] and java.util.logging. If SLF4J is available, it will be used instead.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/log.png" alt="">
    * <p>
    * The default log category will be "reactor.*", a generated operator suffix will
    * complete, e.g. "reactor.Flux.Map".
    *
    * @return a new unaltered [[Flux]]
    */
  //  TODO: How to test?
  final def log() = Flux(jFlux.log())

  /**
    * Observe all Reactive Streams signals and use [[Logger]] support to handle trace implementation. Default will
    * use [[Level.INFO]] and java.util.logging. If SLF4J is available, it will be used instead.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/log.png" alt="">
    * <p>
    *
    * @param category to be mapped into logger configuration (e.g. org.springframework
    *                 .reactor). If category ends with "." like "reactor.", a generated operator
    *                 suffix will complete, e.g. "reactor.Flux.Map".
    * @return a new unaltered [[Flux]]
    */
  //  TODO: How to test?
  final def log(category: String) = Flux(jFlux.log(category))

  /**
    * Observe Reactive Streams signals matching the passed filter `options` and
    * use [[Logger]] support to
    * handle trace
    * implementation. Default will
    * use the passed [[Level]] and java.util.logging. If SLF4J is available, it will be used instead.
    *
    * Options allow fine grained filtering of the traced signal, for instance to only capture onNext and onError:
    * <pre>
    *     flux.log("category", Level.INFO, SignalType.ON_NEXT, SignalType.ON_ERROR)
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/log.png" alt="">
    * <p>
    *
    * @param category to be mapped into logger configuration (e.g. org.springframework
    *                 .reactor). If category ends with "." like "reactor.", a generated operator
    *                 suffix will complete, e.g. "reactor.Flux.Map".
    * @param level    the [[Level]] to enforce for this tracing Flux (only FINEST, FINE,
    *                 INFO, WARNING and SEVERE are taken into account)
    * @param options  a vararg [[SignalType]] option to filter log messages
    * @return a new unaltered [[Flux]]
    */
  //  TODO: How to test?
  final def log(category: String, level: Level, options: SignalType*) = Flux(jFlux.log(category, level, options: _*))

  /**
    * Observe Reactive Streams signals matching the passed filter `options` and use
    * [[Logger]] support to handle trace implementation. Default will use the passed
    * [[Level]] and java.util.logging. If SLF4J is available, it will be used
    * instead.
    * <p>
    * Options allow fine grained filtering of the traced signal, for instance to only
    * capture onNext and onError:
    * <pre>
    *     flux.log("category", Level.INFO, SignalType.ON_NEXT, SignalType.ON_ERROR)
    *
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/log.png"
    * alt="">
    *
    * @param category         to be mapped into logger configuration (e.g. org.springframework
    *                         .reactor). If category ends with "." like "reactor.", a generated operator
    *                         suffix will complete, e.g. "reactor.Flux.Map".
    * @param level            the [[Level]] to enforce for this tracing Flux (only FINEST, FINE,
    *                         INFO, WARNING and SEVERE are taken into account)
    * @param showOperatorLine capture the current stack to display operator
    *                         class/line number.
    * @param options          a vararg [[SignalType]] option to filter log messages
    * @return a new unaltered [[Flux]]
    */
  final def log(category: String,
                level: Level,
                showOperatorLine: Boolean,
                options: SignalType*) = Flux(jFlux.log(category, level, showOperatorLine, options: _*))

  /**
    * Transform the items emitted by this [[Flux]] by applying a function to each item.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/map.png" alt="">
    * <p>
    *
    * @param mapper the transforming [[Function1]]
    * @tparam V the transformed type
    * @return a transformed [[Flux]]
    */
  override final def map[V](mapper: (T) => V) = new Flux[V](jFlux.map(mapper))

  /**
    * Transform the error emitted by this [[Flux]] by applying a function.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/maperror.png"
    * alt="">
    * <p>
    *
    * @param mapper the error transforming [[Function1]]
    * @return a transformed [[Flux]]
    */
  final def mapError(mapper: Throwable => _ <: Throwable) = Flux(jFlux.mapError(mapper))

  /**
    * Transform the error emitted by this [[Flux]] by applying a function if the
    * error matches the given type, otherwise let the error flow.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/maperror.png" alt="">
    * <p>
    *
    * @param type   the class of the exception type to react to
    * @param mapper the error transforming [[Function1]]
    * @tparam E the error type
    * @return a transformed [[Flux]]
    */
  final def mapError[E <: Throwable](`type`: Class[E], mapper: E => _ <: Throwable): Flux[T] = Flux(jFlux.mapError(`type`, mapper))

  /**
    * Transform the error emitted by this [[Flux]] by applying a function if the
    * error matches the given predicate, otherwise let the error flow.
    * <p>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/maperror.png"
    * alt="">
    *
    * @param predicate the error predicate
    * @param mapper    the error transforming [[Function1]]
    * @return a transformed [[Flux]]
    */
  final def mapError(predicate: Throwable => Boolean, mapper: Throwable => _ <: Throwable) = Flux(jFlux.mapError(predicate, mapper))

  /**
    * Transform the incoming onNext, onError and onComplete signals into [[Signal]].
    * Since the error is materialized as a [[Signal]], the propagation will be stopped and onComplete will be
    * emitted. Complete signal will first emit a [[Signal.complete]] and then effectively complete the flux.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/materialize.png" alt="">
    *
    * @return a [[Flux]] of materialized [[Signal]]
    */
  final def materialize() = Flux(jFlux.materialize())

  /**
    * Merge emissions of this [[Flux]] with the provided [[Publisher]], so that they may interleave.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/merge.png" alt="">
    * <p>
    *
    * @param other the [[Publisher]] to merge with
    * @return a new [[Flux]]
    */
  final def mergeWith(other: Publisher[_ <: T]) = Flux(jFlux.mergeWith(other))

  /**
    * Emit only the first item emitted by this [[Flux]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/next.png" alt="">
    * <p>
    *
    * @return a new [[Mono]]
    */
  final def next(): Mono[T] = Mono(jFlux.next())

  /**
    * Evaluate each accepted value against the given [[Class]] type. If the
    * predicate test succeeds, the value is
    * passed into the new [[Flux]]. If the predicate test fails, the value is ignored and a request of 1 is
    * emitted.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/filter.png" alt="">
    *
    * @param clazz the [[Class]] type to test values against
    * @return a new [[Flux]] reduced to items converted to the matched type
    */
  final def ofType[U](clazz: Class[U]) = Flux(jFlux.ofType(clazz))

  /**
    * Request an unbounded demand and push the returned [[Flux]], or park the observed elements if not enough
    * demand is requested downstream. Errors will be delayed until the buffer gets consumed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurebuffer.png" alt="">
    *
    * @return a buffering [[Flux]]
    *
    */
  //  TODO: How to test?
  final def onBackpressureBuffer() = Flux(jFlux.onBackpressureBuffer())

  /**
    * Request an unbounded demand and push the returned [[Flux]], or park the observed elements if not enough
    * demand is requested downstream. Errors will be immediately emitted on overflow
    * regardless of the pending buffer.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurebuffer.png" alt="">
    *
    * @param maxSize maximum buffer backlog size before immediate error
    * @return a buffering [[Flux]]
    *
    */
  //  TODO: How to test?
  final def onBackpressureBuffer(maxSize: Int) = Flux(jFlux.onBackpressureBuffer(maxSize))

  /**
    * Request an unbounded demand and push the returned [[Flux]], or park the observed elements if not enough
    * demand is requested downstream. Overflow error will be delayed after the current
    * backlog is consumed. However the `onOverflow` will be immediately invoked.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurebuffer.png" alt="">
    *
    * @param maxSize    maximum buffer backlog size before overflow callback is called
    * @param onOverflow callback to invoke on overflow
    * @return a buffering [[Flux]]
    *
    */
  //  TODO: How to test?
  final def onBackpressureBuffer(maxSize: Int, onOverflow: T => Unit): Flux[T] = Flux(jFlux.onBackpressureBuffer(maxSize, onOverflow))

  /**
    * Request an unbounded demand and push the returned [[Flux]], or park the observed
    * elements if not enough demand is requested downstream, within a `maxSize`
    * limit. Over that limit, the overflow strategy is applied (see [[BufferOverflowStrategy]]).
    * <p>
    * Note that for the [[BufferOverflowStrategy.ERROR ERROR]] strategy, the overflow
    * error will be delayed after the current backlog is consumed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurebuffer.png" alt="">
    *
    * @param maxSize                maximum buffer backlog size before overflow strategy is applied
    * @param bufferOverflowStrategy strategy to apply to overflowing elements
    * @return a buffering [[Flux]]
    */
  //  TODO: How to test?
  final def onBackpressureBuffer(maxSize: Int, bufferOverflowStrategy: BufferOverflowStrategy) = Flux(jFlux.onBackpressureBuffer(maxSize, bufferOverflowStrategy))

  /**
    * Request an unbounded demand and push the returned [[Flux]], or park the observed
    * elements if not enough demand is requested downstream, within a `maxSize`
    * limit. Over that limit, the overflow strategy is applied (see [[BufferOverflowStrategy]]).
    * <p>
    * A {@link Consumer} is immediately invoked when there is an overflow, receiving the
    * value that was discarded because of the overflow (which can be different from the
    * latest element emitted by the source in case of a
    * [[BufferOverflowStrategy.DROP_LATEST DROP_LATEST]] strategy).
    *
    * <p>
    * Note that for the [[BufferOverflowStrategy.ERROR ERROR]] strategy, the overflow
    * error will be delayed after the current backlog is consumed. The consumer is still
    * invoked immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurebuffer.png" alt="">
    *
    * @param maxSize                maximum buffer backlog size before overflow callback is called
    * @param onBufferOverflow       callback to invoke on overflow
    * @param bufferOverflowStrategy strategy to apply to overflowing elements
    * @return a buffering [[Flux]]
    */
  //  TODO: How to test?
  final def onBackpressureBuffer(maxSize: Int, onBufferOverflow: T => Unit, bufferOverflowStrategy: BufferOverflowStrategy) = Flux(jFlux.onBackpressureBuffer(maxSize, onBufferOverflow, bufferOverflowStrategy))

  /**
    * Request an unbounded demand and push the returned [[Flux]], or drop the observed elements if not enough
    * demand is requested downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressuredrop.png" alt="">
    *
    * @return a dropping [[Flux]]
    *
    */
  //  TODO: How to test?
  final def onBackpressureDrop(): Flux[T] = Flux(jFlux.onBackpressureDrop())

  /**
    * Request an unbounded demand and push the returned [[Flux]], or drop and notify dropping `consumer`
    * with the observed elements if not enough demand is requested downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressuredropc.png" alt="">
    *
    * @param onDropped the Consumer called when an value gets dropped due to lack of downstream requests
    * @return a dropping [[Flux]]
    *
    */
  //  TODO: Test this
  final def onBackpressureDrop(onDropped: T => Unit) = Flux(jFlux.onBackpressureDrop(onDropped))

  /**
    * Request an unbounded demand and push the returned
    * [[Flux]], or emit onError fom [[reactor.core.Exceptions.failWithOverflow]] if not enough demand is requested
    * downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressureerror.png" alt="">
    *
    * @return an erroring [[Flux]] on backpressure
    *
    */
  /// TODO: test this please
  final def onBackpressureError() = Flux(jFlux.onBackpressureError())

  /**
    * Request an unbounded demand and push the returned [[Flux]], or only keep the most recent observed item
    * if not enough demand is requested downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onbackpressurelatest.png" alt="">
    *
    * @return a dropping [[Flux]] that will only keep a reference to the last observed item
    *
    */
  /// TODO: test this please
  final def onBackpressureLatest() = Flux(jFlux.onBackpressureLatest())

  /**
    * Subscribe to a returned fallback publisher when any error occurs.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorresumewith.png" alt="">
    * <p>
    *
    * @param fallback the [[Function1]] mapping the error to a new [[Publisher]] sequence
    * @return a new [[Flux]]
    */
  final def onErrorResumeWith(fallback: Throwable => _ <: Publisher[_ <: T]) = Flux(jFlux.onErrorResumeWith(fallback))

  /**
    * Subscribe to a returned fallback publisher when an error matching the given type
    * occurs.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorresumewith.png"
    * alt="">
    *
    * @param type     the error type to match
    * @param fallback the [[Function1]] mapping the error to a new [[Publisher]]
    *                 sequence
    * @tparam E the error type
    * @return a new [[Flux]]
    */
  final def onErrorResumeWith[E <: Throwable](`type`: Class[E], fallback: E => _ <: Publisher[_ <: T]) = Flux(jFlux.onErrorResumeWith[E](`type`, fallback))

  /**
    * Subscribe to a returned fallback publisher when an error matching the given type
    * occurs.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorresumewith.png"
    * alt="">
    *
    * @param predicate the error predicate to match
    * @param fallback  the [[Function1]] mapping the error to a new [[Publisher]]
    *                  sequence
    * @return a new [[Flux]]
    */
  final def onErrorResumeWith(predicate: Throwable => Boolean, fallback: Throwable => _ <: Publisher[_ <: T]) = Flux(jFlux.onErrorResumeWith(predicate, fallback))

  /**
    * Fallback to the given value if an error is observed on this [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorreturn.png" alt="">
    * <p>
    *
    * @param fallbackValue alternate value on fallback
    * @return a new [[Flux]]
    */
  final def onErrorReturn(fallbackValue: T) = Flux(jFlux.onErrorReturn(fallbackValue))

  /**
    * Fallback to the given value if an error of a given type is observed on this
    * [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorreturn.png" alt="">
    *
    * @param type          the error type to match
    * @param fallbackValue alternate value on fallback
    * @tparam E the error type
    * @return a new [[Flux]]
    */
  final def onErrorReturn[E <: Throwable](`type`: Class[E], fallbackValue: T) = Flux(jFlux.onErrorReturn(`type`, fallbackValue))

  /**
    * Fallback to the given value if an error matching the given predicate is
    * observed on this
    * [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/onerrorreturn.png" alt="">
    *
    * @param predicate     the error predicate to match
    * @param fallbackValue alternate value on fallback
    * @return a new [[Flux]]
    */
  final def onErrorReturn(predicate: Throwable => Boolean, fallbackValue: T) = Flux(jFlux.onErrorReturn(predicate, fallbackValue))

  /**
    * Detaches the both the child [[Subscriber]] and the [[Subscription]] on
    * termination or cancellation.
    * <p>This should help with odd retention scenarios when running
    * with non-reactor [[Subscriber]].
    *
    * @return a detachable [[Flux]]
    */
  //TODO: How to test?
  final def onTerminateDetach() = Flux(jFlux.onTerminateDetach())

  /**
    * Prepare to consume this [[Flux]] on number of 'rails' matching number of CPU
    * in round-robin fashion.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/parallel.png" alt="">
    *
    * @return a new [[ParallelFlux]] instance
    */
  final def parallel() = ParallelFlux(jFlux.parallel())

  /**
    * Prepare to consume this [[Flux]] on parallelism number of 'rails'
    * in round-robin fashion.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/parallel.png" alt="">
    *
    * @param parallelism the number of parallel rails
    * @return a new [[ParallelFlux]] instance
    */
  final def parallel(parallelism: Int): ParallelFlux[T] = ParallelFlux(jFlux.parallel(parallelism))

  /**
    * Prepare to consume this [[Flux]] on parallelism number of 'rails'
    * in round-robin fashion and use custom prefetch amount and queue
    * for dealing with the source [[Flux]]'s values.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/parallel.png" alt="">
    *
    * @param parallelism the number of parallel rails
    * @param prefetch    the number of values to prefetch from the source
    * @return a new [[ParallelFlux]] instance
    */
  final def parallel(parallelism: Int, prefetch: Int) = ParallelFlux(jFlux.parallel(parallelism, prefetch))

  /**
    * Prepare a [[ConnectableFlux]] which shares this [[Flux]] sequence and dispatches values to
    * subscribers in a backpressure-aware manner. Prefetch will default to [[reactor.util.concurrent.QueueSupplier.SMALL_BUFFER_SIZE]].
    * This will effectively turn any type of sequence into a hot sequence.
    * <p>
    * Backpressure will be coordinated on [[Subscription.request]] and if any [[Subscriber]] is missing
    * demand (requested = 0), multicast will pause pushing/pulling.
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publish.png" alt="">
    *
    * @return a new [[ConnectableFlux]]
    */
  final def publish() = ConnectableFlux(jFlux.publish())

  /**
    * Prepare a [[ConnectableFlux]] which shares this [[Flux]] sequence and dispatches values to
    * subscribers in a backpressure-aware manner. This will effectively turn any type of sequence into a hot sequence.
    * <p>
    * Backpressure will be coordinated on [[Subscription.request]] and if any [[Subscriber]] is missing
    * demand (requested = 0), multicast will pause pushing/pulling.
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publish.png" alt="">
    *
    * @param prefetch bounded requested demand
    * @return a new [[ConnectableFlux]]
    */
  final def publish(prefetch: Int) = ConnectableFlux(jFlux.publish(prefetch))

  /**
    * Shares a sequence for the duration of a function that may transform it and
    * consume it as many times as necessary without causing multiple subscriptions
    * to the upstream.
    *
    * @param transform the transformation function
    * @tparam R the output value type
    * @return a new [[Flux]]
    */
  final def publish[R](transform: Flux[T] => _ <: Publisher[_ <: R]) = Flux(jFlux.publish[R](transform))

  /**
    * Shares a sequence for the duration of a function that may transform it and
    * consume it as many times as necessary without causing multiple subscriptions
    * to the upstream.
    *
    * @param transform the transformation function
    * @param prefetch  the request size
    * @tparam R the output value type
    * @return a new [[Flux]]
    */
  final def publish[R](transform: Flux[T] => _ <: Publisher[_ <: R], prefetch: Int) = Flux(jFlux.publish[R](transform, prefetch))

  /**
    * Prepare a [[Mono]] which shares this [[Flux]] sequence and dispatches the first observed item to
    * subscribers in a backpressure-aware manner.
    * This will effectively turn any type of sequence into a hot sequence when the first [[Subscriber]] subscribes.
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publishnext.png" alt="">
    *
    * @return a new [[Mono]]
    */
  final def publishNext() = Mono(jFlux.publishNext())

  /**
    * Run onNext, onComplete and onError on a supplied [[Scheduler]]
    * [[reactor.core.scheduler.Scheduler.Worker]].
    *
    * <p>
    * Typically used for fast publisher, slow consumer(s) scenarios.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publishon.png" alt="">
    * <p>
    * `flux.publishOn(Schedulers.single()).subscribe()`
    *
    * @param scheduler a checked [[reactor.core.scheduler.Scheduler.Worker]] factory
    * @return a [[Flux]] producing asynchronously
    */
  //  TODO: How to test
  final def publishOn(scheduler: Scheduler) = Flux(jFlux.publishOn(scheduler))

  /**
    * Run onNext, onComplete and onError on a supplied [[Scheduler]]
    * [[reactor.core.scheduler.Scheduler.Worker]].
    *
    * <p>
    * Typically used for fast publisher, slow consumer(s) scenarios.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publishon.png" alt="">
    * <p>
    * `flux.publishOn(Schedulers.single()).subscribe()`
    *
    * @param scheduler a checked [[reactor.core.scheduler.Scheduler.Worker]] factory
    * @param prefetch  the asynchronous boundary capacity
    * @return a [[Flux]] producing asynchronously
    */
  //  TODO: how to test this?
  final def publishOn(scheduler: Scheduler, prefetch: Int): Flux[T] = Flux(jFlux.publishOn(scheduler, prefetch))

  /**
    * Run onNext, onComplete and onError on a supplied [[Scheduler]]
    * [[reactor.core.scheduler.Scheduler.Worker]].
    *
    * <p>
    * Typically used for fast publisher, slow consumer(s) scenarios.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/publishon.png" alt="">
    * <p>
    * `flux.publishOn(Schedulers.single()).subscribe()`
    *
    * @param scheduler  a checked { @link reactor.core.scheduler.Scheduler.Worker} factory
    * @param delayError should the buffer be consumed before forwarding any error
    * @param prefetch   the asynchronous boundary capacity
    * @return a [[Flux]] producing asynchronously
    */
  //  TODO: how to test this?
  final def publishOn(scheduler: Scheduler, delayError: Boolean, prefetch: Int) = Flux(jFlux.publishOn(scheduler, delayError, prefetch))

  /**
    * Aggregate the values from this [[Flux]] sequence into an object of the same type than the
    * emitted items. The left/right `BiFunction` arguments are the N-1 and N item, ignoring sequence
    * with 0 or 1 element only.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/aggregate.png" alt="">
    *
    * @param aggregator the aggregating `BiFunction`
    * @return a reduced [[Flux]]
    *
    *
    */
  final def reduce(aggregator: (T, T) => T) = Mono(jFlux.reduce(aggregator))

  /**
    * Accumulate the values from this [[Flux]] sequence into an object matching an initial value type.
    * The arguments are the N-1 or `initial` value and N current item .
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/reduce.png" alt="">
    *
    * @param accumulator the reducing `BiFunction`
    * @param initial     the initial left argument to pass to the reducing `BiFunction`
    * @tparam A the type of the initial and reduced object
    * @return a reduced [[Flux]]
    *
    */
  final def reduce[A](initial: A, accumulator: (A, T) => A): Mono[A] = Mono(jFlux.reduce[A](initial, accumulator))

  /**
    * Accumulate the values from this [[Flux]] sequence into an object matching an initial value type.
    * The arguments are the N-1 or `initial` value and N current item .
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/reduce.png" alt="">
    *
    * @param accumulator the reducing `BiFunction`
    * @param initial     the initial left argument supplied on subscription to the reducing `BiFunction`
    * @tparam A the type of the initial and reduced object
    * @return a reduced [[Flux]]
    *
    */
  final def reduceWith[A](initial: () => A, accumulator: (A, T) => A) = Mono(jFlux.reduceWith[A](initial, accumulator))

  /**
    * Repeatedly subscribe to the source completion of the previous subscription.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeat.png" alt="">
    *
    * @return an indefinitely repeated [[Flux]] on onComplete
    */
  //  TODO: How to test?
  final def repeat() = Flux(jFlux.repeat())

  /**
    * Repeatedly subscribe to the source if the predicate returns true after completion of the previous subscription.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeatb.png" alt="">
    *
    * @param predicate the boolean to evaluate on onComplete.
    * @return an eventually repeated [[Flux]] on onComplete
    *
    */
  final def repeat(predicate: () => Boolean) = Flux(jFlux.repeat(predicate))

  /**
    * Repeatedly subscribe to the source if the predicate returns true after completion of the previous subscription.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeatn.png" alt="">
    *
    * @param numRepeat the number of times to re-subscribe on onComplete
    * @return an eventually repeated [[Flux]] on onComplete up to number of repeat specified
    *
    */
  final def repeat(numRepeat: Long) = Flux(jFlux.repeat(numRepeat))

  /**
    * Repeatedly subscribe to the source if the predicate returns true after completion of the previous
    * subscription. A specified maximum of repeat will limit the number of re-subscribe.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeatnb.png" alt="">
    *
    * @param numRepeat the number of times to re-subscribe on complete
    * @param predicate the boolean to evaluate on onComplete
    * @return an eventually repeated [[Flux]] on onComplete up to number of repeat specified OR matching
    *         predicate
    *
    */
  final def repeat(numRepeat: Long, predicate: () => Boolean) = Flux(jFlux.repeat(numRepeat, predicate))

  /**
    * Repeatedly subscribe to this [[Flux]] when a companion sequence signals a number of emitted elements in
    * response to the flux completion signal.
    * <p>If the companion sequence signals when this [[Flux]] is active, the repeat
    * attempt is suppressed and any terminal signal will terminate this [[Flux]] with the same signal immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeatwhen.png" alt="">
    *
    * @param whenFactory the [[Function1]] providing a [[Flux]] signalling an exclusive number of
    *                    emitted elements on onComplete and returning a [[Publisher]] companion.
    * @return an eventually repeated [[Flux]] on onComplete when the companion [[Publisher]] produces an
    *         onNext signal
    *
    */
  //  TODO: How to test?
  final def repeatWhen(whenFactory: Flux[Long] => _ <: Publisher[_]): Flux[T] = Flux(jFlux.repeatWhen(new Function[JFlux[JLong], Publisher[_]] {
    override def apply(t: JFlux[JLong]): Publisher[_] = whenFactory(Flux(t).map(Long2long))
  }))

  /**
    * Turn this [[Flux]] into a hot source and cache last emitted signals for further [[Subscriber]]. Will
    * retain an unbounded amount of onNext signals. Completion and Error will also be
    * replayed.
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png"
    * alt="">
    *
    * @return a replaying [[ConnectableFlux]]
    */
  //  TODO: How to test?
  final def replay() = ConnectableFlux(jFlux.replay())

  /**
    * Turn this [[Flux]] into a connectable hot source and cache last emitted
    * signals for further [[Subscriber]].
    * Will retain up to the given history size onNext signals. Completion and Error will also be
    * replayed.
    *
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png" alt="">
    *
    * @param history number of events retained in history excluding complete and
    *                error
    * @return a replaying [[ConnectableFlux]]
    *
    */
  final def replay(history: Int) = ConnectableFlux(jFlux.replay(history))

  /**
    * Turn this [[Flux]] into a connectable hot source and cache last emitted signals
    * for further [[Subscriber]]. Will retain each onNext up to the given per-item
    * expiry
    * timeout. Completion and Error will also be replayed.
    * <p>
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png"
    * alt="">
    *
    * @param ttl Per-item timeout duration
    * @return a replaying [[ConnectableFlux]]
    */
  final def replay(ttl: Duration) = ConnectableFlux(jFlux.replay(ttl))

  /**
    * Turn this [[Flux]] into a connectable hot source and cache last emitted signals
    * for further [[Subscriber]]. Will retain up to the given history size onNext
    * signals and given a per-item ttl. Completion and Error will also be
    * replayed.
    * <p>
    * <p>
    * <img src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png"
    * alt="">
    *
    * @param history number of events retained in history excluding complete and error
    * @param ttl     Per-item timeout duration
    * @return a replaying [[ConnectableFlux]]
    */
  final def replay(history: Int, ttl: Duration) = ConnectableFlux(jFlux.replay(history, ttl))

  /**
    * Turn this [[Flux]] into a connectable hot source and cache last emitted signals
    * for further [[Subscriber]]. Will retain up to the given history size onNext
    * signals. Completion and Error will also be replayed.
    * <p>
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png"
    * alt="">
    *
    * @param ttl   Per-item timeout duration in milliseconds
    * @param timer [[TimedScheduler]] to read current time from
    * @return a replaying [[ConnectableFlux]]
    */
  final def replayMillis(ttl: Long, timer: TimedScheduler) = ConnectableFlux(jFlux.replayMillis(ttl, timer))

  /**
    * Turn this [[Flux]] into a connectable hot source and cache last emitted signals
    * for further [[Subscriber]]. Will retain up to the given history size onNext
    * signals. Completion and Error will also be replayed.
    * <p>
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/replay.png"
    * alt="">
    *
    * @param history number of events retained in history excluding complete and error
    * @param ttl     Per-item timeout duration in milliseconds
    * @param timer   [[TimedScheduler]] to read current time from
    * @return a replaying [[ConnectableFlux]]
    */
  final def replayMillis(history: Int, ttl: Long, timer: TimedScheduler) = ConnectableFlux(jFlux.replayMillis(history, ttl, timer))

  /**
    * Re-subscribes to this [[Flux]] sequence if it signals any error
    * either indefinitely.
    * <p>
    * The times == Long.MAX_VALUE is treated as infinite retry.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/retry.png" alt="">
    *
    * @return a re-subscribing [[Flux]] on onError
    */
  final def retry() = Flux(jFlux.retry())

  /**
    * Re-subscribes to this [[Flux]] sequence if it signals any error
    * either indefinitely or a fixed number of times.
    * <p>
    * The times == Long.MAX_VALUE is treated as infinite retry.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/retryn.png" alt="">
    *
    * @param numRetries the number of times to tolerate an error
    * @return a re-subscribing [[Flux]] on onError up to the specified number of retries.
    *
    */
  final def retry(numRetries: Long) = Flux(jFlux.retry(numRetries))

  /**
    * Re-subscribes to this [[Flux]] sequence if it signals any error
    * and the given `Predicate` matches otherwise push the error downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/retryb.png" alt="">
    *
    * @param retryMatcher the predicate to evaluate if retry should occur based on a given error signal
    * @return a re-subscribing [[Flux]] on onError if the predicates matches.
    */
  final def retry(retryMatcher: Throwable => Boolean) = Flux(jFlux.retry(retryMatcher))

  /**
    * Re-subscribes to this [[Flux]] sequence up to the specified number of retries if it signals any
    * error and the given `Predicate` matches otherwise push the error downstream.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/retrynb.png" alt="">
    *
    * @param numRetries   the number of times to tolerate an error
    * @param retryMatcher the predicate to evaluate if retry should occur based on a given error signal
    * @return a re-subscribing [[Flux]] on onError up to the specified number of retries and if the predicate
    *         matches.
    *
    */
  final def retry(numRetries: Long, retryMatcher: Throwable => Boolean) = Flux(jFlux.retry(numRetries, retryMatcher))

  /**
    * Retries this [[Flux]] when a companion sequence signals
    * an item in response to this [[Flux]] error signal
    * <p>If the companion sequence signals when the [[Flux]] is active, the retry
    * attempt is suppressed and any terminal signal will terminate the [[Flux]] source with the same signal
    * immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/retrywhen.png" alt="">
    *
    * @param whenFactory the
    *                    [[Function1]] providing a [[Flux]] signalling any error from the source sequence and returning a { @link Publisher} companion.
    * @return a re-subscribing [[Flux]] on onError when the companion [[Publisher]] produces an
    *         onNext signal
    */
  final def retryWhen(whenFactory: Flux[Throwable] => Publisher[_]) = Flux(jFlux.retryWhen(whenFactory))

  /**
    * Emit latest value for every given period of time.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/sampletimespan.png" alt="">
    *
    * @param timespan the duration to emit the latest observed item
    * @return a sampled [[Flux]] by last item over a period of time
    */
  final def sample(timespan: Duration) = Flux(jFlux.sample(timespan))

  /**
    * Sample this [[Flux]] and emit its latest value whenever the sampler [[Publisher]]
    * signals a value.
    * <p>
    * Termination of either [[Publisher]] will result in termination for the [[Subscriber]]
    * as well.
    * <p>
    * Both [[Publisher]] will run in unbounded mode because the backpressure
    * would interfere with the sampling precision.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/sample.png" alt="">
    *
    * @param sampler the sampler [[Publisher]]
    * @tparam U the type of the sampler sequence
    * @return a sampled [[Flux]] by last item observed when the sampler [[Publisher]] signals
    */
  final def sample[U](sampler: Publisher[U]) = Flux(jFlux.sample[U](sampler))

  /**
    * Take a value from this [[Flux]] then use the duration provided to skip other values.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/samplefirsttimespan.png" alt="">
    *
    * @param timespan the duration to exclude others values from this sequence
    * @return a sampled [[Flux]] by first item over a period of time
    */
  final def sampleFirst(timespan: Duration) = Flux(jFlux.sampleFirst(timespan))

  /**
    * Take a value from this [[Flux]] then use the duration provided by a
    * generated Publisher to skip other values until that sampler [[Publisher]] signals.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/samplefirst.png" alt="">
    *
    * @param samplerFactory select a [[Publisher]] companion to signal onNext or onComplete to stop excluding
    *                                        others values from this sequence
    * @tparam U the companion reified type
    * @return a sampled [[Flux]] by last item observed when the sampler signals
    */
  final def sampleFirst[U](samplerFactory: T => Publisher[U]) = Flux(jFlux.sampleFirst[U](samplerFactory))

  /**
    * Take a value from this [[Flux]] then use the duration provided to skip other values.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/samplefirsttimespan.png" alt="">
    *
    * @param timespan the period in milliseconds to exclude others values from this sequence
    * @return a sampled [[Flux]] by first item over a period of time
    */
  final def sampleFirstMillis(timespan: Long) = Flux(jFlux.sampleFirstMillis(timespan))

  /**
    * Emit latest value for every given period of ti,e.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/sampletimespan.png" alt="">
    *
    * @param timespan the period in second to emit the latest observed item
    * @return a sampled [[Flux]] by last item over a period of time
    */
  final def sampleMillis(timespan: Long) = Flux(jFlux.sampleMillis(timespan))

  /**
    * Emit the last value from this [[Flux]] only if there were no new values emitted
    * during the time window provided by a publisher for that particular last value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/sampletimeout.png" alt="">
    *
    * @param throttlerFactory select a [[Publisher]] companion to signal onNext or onComplete to stop checking
    *                                          others values from this sequence and emit the selecting item
    * @tparam U the companion reified type
    * @return a sampled [[Flux]] by last single item observed before a companion [[Publisher]] emits
    */
  final def sampleTimeout[U](throttlerFactory: T => Publisher[U]) = Flux(jFlux.sampleTimeout(throttlerFactory))

  /**
    * Emit the last value from this [[Flux]] only if there were no newer values emitted
    * during the time window provided by a publisher for that particular last value.
    * <p>The provided `maxConcurrency` will keep a bounded maximum of concurrent timeouts and drop any new
    * items until at least one timeout terminates.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/sampletimeoutm.png" alt="">
    *
    * @param throttlerFactory select a [[Publisher]] companion to signal onNext or onComplete to stop checking
    *                                          others values from this sequence and emit the selecting item
    * @param maxConcurrency the maximum number of concurrent timeouts
    * @tparam U the throttling type
    * @return a sampled [[Flux]] by last single item observed before a companion [[Publisher]] emits
    */
  final def sampleTimeout[U](throttlerFactory: T => Publisher[U], maxConcurrency: Int) = Flux(jFlux.sampleTimeout(throttlerFactory, maxConcurrency))

  /**
    * Accumulate this [[Flux]] values with an accumulator `BiFunction` and
    * returns the intermediate results of this function.
    * <p>
    * Unlike [[Flux.scan(Object, BiFunction)]], this operator doesn't take an initial value
    * but treats the first [[Flux]] value as initial value.
    * <br>
    * The accumulation works as follows:
    * <pre><code>
    * result[0] = accumulator(source[0], source[1])
    * result[1] = accumulator(result[0], source[2])
    * result[2] = accumulator(result[1], source[3])
    * ...
    * </code></pre>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/accumulate.png" alt="">
    *
    * @param accumulator the accumulating `BiFunction`
    * @return an accumulating [[Flux]]
    *
    */

  final def scan(accumulator: (T, T) => T) = Flux(jFlux.scan(accumulator))

  /**
    * Aggregate this [[Flux]] values with the help of an accumulator `BiFunction`
    * and emits the intermediate results.
    * <p>
    * The accumulation works as follows:
    * <pre><code>
    * result[0] = initialValue;
    * result[1] = accumulator(result[0], source[0])
    * result[2] = accumulator(result[1], source[1])
    * result[3] = accumulator(result[2], source[2])
    * ...
    * </code></pre>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/scan.png" alt="">
    *
    * @param initial     the initial argument to pass to the reduce function
    * @param accumulator the accumulating `BiFunction`
    * @tparam A the accumulated type
    * @return an accumulating [[Flux]] starting with initial state
    *
    */
  final def scan[A](initial: A, accumulator: (A, T) => A) = Flux(jFlux.scan(initial, accumulator))

  /**
    * Aggregate this [[Flux]] values with the help of an accumulator `BiFunction`
    * and emits the intermediate results.
    * <p>
    * The accumulation works as follows:
    * <pre><code>
    * result[0] = initialValue;
    * result[1] = accumulator(result[0], source[0])
    * result[2] = accumulator(result[1], source[1])
    * result[3] = accumulator(result[2], source[2])
    * ...
    * </code></pre>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/scan.png" alt="">
    *
    * @param initial     the initial supplier to init the first value to pass to the reduce
    *                    function
    * @param accumulator the accumulating `BiFunction`
    * @tparam A the accumulated type
    * @return an accumulating [[Flux]] starting with initial state
    *
    */
  final def scanWith[A](initial: () => A, accumulator: (A, T) => A) = Flux(jFlux.scanWith(initial, accumulator))

  /**
    * Returns a new [[Flux]] that multicasts (shares) the original [[Flux]].
    * As long as
    * there is at least one [[Subscriber]] this [[Flux]] will be subscribed and
    * emitting data.
    * When all subscribers have cancelled it will cancel the source
    * [[Flux]].
    * <p>This is an alias for [[Flux.publish]].[[ConnectableFlux.refCount]].
    *
    * @return a [[Flux]] that upon first subcribe causes the source [[Flux]]
    *         to subscribe once only, late subscribers might therefore miss items.
    */
  final def share() = Flux(jFlux.share())

  /**
    * Expect and emit a single item from this [[Flux]] source or signal
    * [[NoSuchElementException]] (or a default generated value) for empty source,
    * [[IndexOutOfBoundsException]] for a multi-item source.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/single.png" alt="">
    *
    * @return a [[Mono]] with the eventual single item or an error signal
    */
  final def single() = Mono(jFlux.single())

  /**
    *
    * Expect and emit a single item from this [[Flux]] source or signal
    * [[NoSuchElementException]] (or a default value) for empty source,
    * [[IndexOutOfBoundsException]] for a multi-item source.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/singleordefault.png" alt="">
    *
    * @param defaultValue a single fallback item if this { @link Flux} is empty
    * @return a [[Mono]] with the eventual single item or a supplied default value
    */
  final def single(defaultValue: T) = Mono(jFlux.single(defaultValue))

  /**
    * Expect and emit a zero or single item from this [[Flux]] source or
    * [[NoSuchElementException]] for a multi-item source.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/singleorempty.png" alt="">
    *
    * @return a [[Mono]] with the eventual single item or no item
    */
  final def singleOrEmpty() = Mono(jFlux.singleOrEmpty())

  /**
    * Skip next the specified number of elements from this [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skip.png" alt="">
    *
    * @param skipped the number of times to drop
    * @return a dropping [[Flux]] until the specified skipped number of elements
    */
  final def skip(skipped: Long) = Flux(jFlux.skip(skipped))

  /**
    * Skip elements from this [[Flux]] for the given time period.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skiptime.png" alt="">
    *
    * @param timespan the time window to exclude next signals
    * @return a dropping [[Flux]] until the end of the given timespan
    */
  final def skip(timespan: Duration) = Flux(jFlux.skip(timespan))

  /**
    * Skip the last specified number of elements from this [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skiplast.png" alt="">
    *
    * @param n the number of elements to ignore before completion
    * @return a dropping [[Flux]] for the specified skipped number of elements before termination
    *
    */
  final def skipLast(n: Int) = Flux(jFlux.skipLast(n))

  /**
    * Skip elements from this [[Flux]] for the given time period.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skiptime.png" alt="">
    *
    * @param timespan the time window to exclude next signals
    * @return a dropping [[Flux]] until the end of the given timespan
    */
  final def skipMillis(timespan: Long) = Flux(jFlux.skipMillis(timespan))

  /**
    * Skip elements from this [[Flux]] for the given time period.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skiptime.png" alt="">
    *
    * @param timespan the time window to exclude next signals
    * @param timer    the [[TimedScheduler]] to run on
    * @return a dropping [[Flux]] until the end of the given timespan
    */
  final def skipMillis(timespan: Long, timer: TimedScheduler) = Flux(jFlux.skipMillis(timespan, timer))

  /**
    * Skips values from this [[Flux]] until a `Predicate` returns true for the
    * value. Will include the matched value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skipuntil.png" alt="">
    *
    * @param untilPredicate the `Predicate` evaluating to true to stop skipping.
    * @return a dropping [[Flux]] until the `Predicate` matches
    */
  final def skipUntil(untilPredicate: T => Boolean) = Flux(jFlux.skipUntil(untilPredicate))

  /**
    * Skip values from this [[Flux]] until a specified [[Publisher]] signals
    * an onNext or onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skipuntil.png" alt="">
    *
    * @param other the [[Publisher]] companion to coordinate with to stop skipping
    * @return a dropping [[Flux]] until the other [[Publisher]] emits
    *
    */
  final def skipUntilOther(other: Publisher[_]) = Flux(jFlux.skipUntilOther(other))

  /**
    * Skips values from this [[Flux]] while a `Predicate` returns true for the value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/skipwhile.png" alt="">
    *
    * @param skipPredicate the `Predicate` evaluating to true to keep skipping.
    * @return a dropping [[Flux]] while the `Predicate` matches
    */
  final def skipWhile(skipPredicate: T => Boolean) = Flux(jFlux.skipWhile(skipPredicate))

  /**
    * Returns a [[Flux]] that sorts the events emitted by source [[Flux]].
    * Each item emitted by the [[Flux]] must implement [[Comparable]] with
    * respect to all
    * other items in the sequence.
    *
    * <p>Note that calling `sort` with long, non-terminating or infinite sources
    * might cause [[OutOfMemoryError]]. Use sequence splitting like
    * [[Flux.window]] to sort batches in that case.
    *
    * @throws ClassCastException
    * if any item emitted by the [[Flux]] does not implement
    *                                    [[Comparable]] with respect to
    *                                    all other items emitted by the [[Flux]]
    * @return a sorting [[Flux]]
    */
  final def sort() = Flux(jFlux.sort())

  /**
    * Returns a [[Flux]] that sorts the events emitted by source [[Flux]]
    * given the [[Ordering]] function.
    *
    * <p>Note that calling `sorted` with long, non-terminating or infinite sources
    * might cause [[OutOfMemoryError]]
    *
    * @param sortFunction
    * a function that compares two items emitted by this [[Flux]]
    *                                                            that indicates their sort order
    * @return a sorting [[Flux]]
    */
  final def sort(sortFunction: Ordering[T]) = Flux(jFlux.sort(sortFunction))

  /**
    * Prepend the given [[Iterable]] before this [[Flux]] sequence.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/startwithi.png" alt="">
    *
    * @param iterable the sequence of values to start the sequence with
    * @return a prefixed [[Flux]] with given [[Iterable]]
    */
  final def startWith(iterable: Iterable[_ <: T]) = Flux(jFlux.startWith(iterable))

  /**
    * Prepend the given values before this [[Flux]] sequence.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/startwithv.png" alt="">
    *
    * @param values the array of values to start with
    * @return a prefixed [[Flux]] with given values
    */
  final def startWith(values: T*) = Flux(jFlux.startWith(values:_*))

  /**
    * Prepend the given [[Publisher]] sequence before this [[Flux]] sequence.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/startwith.png" alt="">
    *
    * @param publisher the Publisher whose values to prepend
    * @return a prefixed [[Flux]] with given [[Publisher]] sequence
    */
  final def startWith(publisher: Publisher[_ <: T]) = Flux(jFlux.startWith(publisher))

  /**
    * Start the chain and request unbounded demand.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/unbounded.png" alt="">
    * <p>
    *
    * @return a [[Disposable]] task to execute to dispose and cancel the underlying [[Subscription]]
    **/
  final def subscribe(): Disposable = jFlux.subscribe()

  /**
    * Subscribe a `consumer` to this [[Flux]] that will consume all the
    * sequence. It will request an unbounded demand.
    * <p>
    * For a passive version that observe and forward incoming data see [[Flux.doOnNext]].
    * <p>For a version that gives you more control over backpressure and the request, see
    * [[Flux.subscribe]] with a [[reactor.core.publisher.BaseSubscriber]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribe.png" alt="">
    *
    * @param consumer the consumer to invoke on each value
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit): Disposable = jFlux.subscribe(consumer)

  /**
    * Subscribe a `consumer` to this [[Flux]] that will consume all the
    * sequence.
    * <p>If prefetch is `!= Long.MAX_VALUE`, the [[Subscriber]] will use it as
    * a prefetch strategy: first request N, then when 25% of N is left to be received on
    * onNext, request N x 0.75.
    * <p>For a passive version that observe and forward incoming data see [[Flux.doOnNext]].
    * <p>For a version that gives you more control over backpressure and the request, see
    * [[Flux.subscribe]] with a [[reactor.core.publisher.BaseSubscriber]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribe.png"
    * alt="">
    *
    * @param consumer the consumer to invoke on each value
    * @param prefetch the the prefetch amount, positive
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, prefetch: Int) = jFlux.subscribe(consumer, prefetch)

  /**
    * Subscribe `consumer` to this [[Flux]] that will consume all the
    * sequence.  It will request unbounded demand `Long.MAX_VALUE`.
    * For a passive version that observe and forward incoming data see
    * [[Flux.doOnNext]] and [[Flux.doOnError]].
    * <p>For a version that gives you more control over backpressure and the request, see
    * [[Flux.subscribe]] with a [[reactor.core.publisher.BaseSubscriber]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribeerror.png" alt="">
    *
    * @param consumer      the consumer to invoke on each next signal
    * @param errorConsumer the consumer to invoke on error signal
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit): Disposable = jFlux.subscribe(consumer, errorConsumer)

  /**
    * Subscribe `consumer` to this [[Flux]] that will consume all the
    * sequence.  It will request unbounded demand `Long.MAX_VALUE`.
    * For a passive version that observe and forward incoming data see [[Flux.doOnNext]],
    * [[Flux.doOnError]] and [[Flux.doOnComplete]].
    * <p>For a version that gives you more control over backpressure and the request, see
    * [[Flux.subscribe]] with a [[reactor.core.publisher.BaseSubscriber]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribecomplete.png" alt="">
    *
    * @param consumer         the consumer to invoke on each value
    * @param errorConsumer    the consumer to invoke on error signal
    * @param completeConsumer the consumer to invoke on complete signal
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: () => Unit): Disposable = jFlux.subscribe(consumer, errorConsumer, completeConsumer)

  /**
    * Subscribe `consumer` to this [[Flux]] that will consume all the
    * sequence.  It will let the provided [[Subscription subscriptionConsumer]]
    * request the adequate amount of data, or request unbounded demand
    * `Long.MAX_VALUE` if no such consumer is provided.
    * <p>
    * For a passive version that observe and forward incoming data see [[Flux.doOnNext]],
    * [[Flux.doOnError]], [[Flux.doOnComplete]]
    * and [[Flux.doOnSubscribe]].
    * <p>For a version that gives you more control over backpressure and the request, see
    * [[Flux.subscribe]] with a [[reactor.core.publisher.BaseSubscriber]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribecomplete.png" alt="">
    *
    * @param consumer             the consumer to invoke on each value
    * @param errorConsumer        the consumer to invoke on error signal
    * @param completeConsumer     the consumer to invoke on complete signal
    * @param subscriptionConsumer the consumer to invoke on subscribe signal, to be used
    *                             for the initial [[Subscription.request request]], or null for max request
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: () => Unit, subscriptionConsumer: Subscription => Unit): Disposable = jFlux.subscribe(consumer, errorConsumer, completeConsumer, subscriptionConsumer)

  /**
    * Run subscribe, onSubscribe and request on a supplied
    * [[Subscriber]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/subscribeon.png" alt="">
    * <p>
    * Typically used for slow publisher e.g., blocking IO, fast consumer(s) scenarios.
    *
    * <p>
    * `flux.subscribeOn(Schedulers.single()).subscribe()`
    *
    * @param scheduler a checked [[reactor.core.scheduler.Scheduler.Worker]] factory
    * @return a [[Flux]] requesting asynchronously
    */
  final def subscribeOn(scheduler: Scheduler) = Flux(jFlux.subscribeOn(scheduler))

  /**
    *
    * A chaining [[Publisher.subscribe]] alternative to inline composition type conversion to a hot
    * emitter (e.g. [[reactor.core.publisher.FluxProcessor]] or [[reactor.core.publisher.MonoProcessor]]).
    *
    * `flux.subscribeWith(WorkQueueProcessor.create()).subscribe()`
    *
    * <p>If you need more control over backpressure and the request, use a [[reactor.core.publisher.BaseSubscriber]].
    *
    * @param subscriber the [[Subscriber]] to subscribe and return
    * @tparam E the reified type from the input/output subscriber
    * @return the passed [[Subscriber]]
    */
  final def subscribeWith[E <: Subscriber[T]](subscriber: E) = jFlux.subscribeWith[E](subscriber)

  /**
    * Provide an alternative if this sequence is completed without any data
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchifempty.png" alt="">
    * <p>
    *
    * @param alternate the alternate publisher if this sequence is empty
    * @return an alternating [[Flux]] on source onComplete without elements
    */
  final def switchIfEmpty(alternate: Publisher[_ <: T]) = Flux(jFlux.switchIfEmpty(alternate))

  /**
    * Switch to a new [[Publisher]] generated via a `Function` whenever this [[Flux]] produces an item.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchmap.png" alt="">
    *
    * @param fn the transformation function
    * @tparam V the type of the return value of the transformation function
    * @return an alternating [[Flux]] on source onNext
    *
    */
  final def switchMap[V](fn: T => Publisher[_ <: V]) = Flux(jFlux.switchMap[V](fn))

  /**
    * Switch to a new [[Publisher]] generated via a `Function` whenever this [[Flux]] produces an item.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchmap.png" alt="">
    *
    * @param fn       the transformation function
    * @param prefetch the produced demand for inner sources
    * @tparam V the type of the return value of the transformation function
    * @return an alternating [[Flux]] on source onNext
    *
    */
  final def switchMap[V](fn: T => Publisher[_ <: V], prefetch: Int) = Flux(jFlux.switchMap[V](fn, prefetch))

  /**
    * Subscribe to the given fallback [[Publisher]] if an error matching the given
    * type is observed on this [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchonerror.png" alt="">
    * <p>
    *
    * @param type     the error type to match to fallback
    * @param fallback the alternate [[Publisher]]
    * @tparam E the error type
    * @return an alternating [[Flux]] on source onError
    */
  final def switchOnError[E <: Throwable](`type`: Class[E], fallback: Publisher[_ <: T]) = Flux(jFlux.switchOnError[E](`type`, fallback))

  /**
    * Subscribe to the given fallback [[Publisher]] if an error matching the given
    * predicate is observed on this [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchonerror.png" alt="">
    * <p>
    *
    * @param predicate the predicate to match an error
    * @param fallback  the alternate [[Publisher]]
    * @return an alternating [[Flux]] on source onError
    */
  final def switchOnError(predicate: Throwable => Boolean, fallback: Publisher[_ <: T]) = Flux(jFlux.switchOnError(predicate, fallback))

  /**
    * Subscribe to the given fallback [[Publisher]] if an error is observed on this [[Flux]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/switchonerror.png" alt="">
    * <p>
    *
    * @param fallback the alternate [[Publisher]]
    * @return an alternating [[Flux]] on source onError
    */
  final def switchOnError(fallback: Publisher[_ <: T]) = Flux(jFlux.switchOnError(fallback))

  /**
    * Take only the first N values from this [[Flux]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/take.png" alt="">
    * <p>
    * If N is zero, the [[Subscriber]] gets completed if this [[Flux]] completes, signals an error or
    * signals its first value (which is not not relayed though).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/take0.png" alt="">
    *
    * @param n the number of items to emit from this [[Flux]]
    * @return a size limited [[Flux]]
    */
  final def take(n: Long) = Flux[T](jFlux.take(n))

  /**
    * Relay values from this [[Flux]] until the given time period elapses.
    * <p>
    * If the time period is zero, the [[Subscriber]] gets completed if this [[Flux]] completes, signals an
    * error or
    * signals its first value (which is not not relayed though).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/taketime.png" alt="">
    *
    * @param timespan the time window of items to emit from this [[Flux]]
    * @return a time limited [[Flux]]
    */
  final def take(timespan: Duration) = Flux(jFlux.take(timespan))

  /**
    * Emit the last N values this [[Flux]] emitted before its completion.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/takelast.png" alt="">
    *
    * @param n the number of items from this [[Flux]] to retain and emit on onComplete
    * @return a terminating [[Flux]] sub-sequence
    *
    */
  final def takeLast(n: Int) = Flux(jFlux.takeLast(n))

  /**
    * Relay values from this [[Flux]] until the given time period elapses.
    * <p>
    * If the time period is zero, the [[Subscriber]] gets completed if this [[Flux]] completes, signals an
    * error or
    * signals its first value (which is not not relayed though).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/taketime.png" alt="">
    *
    * @param timespan the time window of items to emit from this { @link Flux}
    * @return a time limited [[Flux]]
    */
  final def takeMillis(timespan: Long) = Flux(jFlux.takeMillis(timespan))

  /**
    * Relay values from this [[Flux]] until the given time period elapses.
    * <p>
    * If the time period is zero, the [[Subscriber]] gets completed if this [[Flux]] completes, signals an
    * error or
    * signals its first value (which is not not relayed though).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/taketime.png" alt="">
    *
    * @param timespan the time window of items to emit from this [[Flux]]
    * @param timer the [[TimedScheduler]] to run on
    * @return a time limited [[Flux]]
    */
  final def takeMillis(timespan: Long, timer: TimedScheduler) = Flux(jFlux.takeMillis(timespan, timer))

  /**
    * Relay values from this [[Flux]] until the given `Predicate` matches.
    * Unlike [[Flux.takeWhile]], this will include the matched data.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/takeuntilp.png" alt="">
    *
    * @param predicate the `Predicate` to signal when to stop replaying signal
    *                              from this [[Flux]]
    * @return an eventually limited [[Flux]]
    *
    */
  final def takeUntil(predicate: T => Boolean) = Flux(jFlux.takeUntil(predicate))

  /**
    * Transform this [[Flux]] in order to generate a target [[Flux]]. Unlike [[Flux.compose]], the
    * provided function is executed as part of assembly.
    *
    * @example {{{
    *   val applySchedulers = flux => flux.subscribeOn(Schedulers.elastic()).publishOn(Schedulers.parallel());
    *   flux.transform(applySchedulers).map(v => v * v).subscribe()
    *          }}}
    * @param transformer the [[Function1]] to immediately map this [[Flux]] into a target [[Flux]]
    *                    instance.
    * @tparam V the item type in the returned [[Flux]]
    * @return a new [[Flux]]
    * @see [[Flux.compose]] for deferred composition of [[Flux]] for each [[Subscriber]]
    * @see [[Flux.as]] for a loose conversion to an arbitrary type
    */
  final def transform[V](transformer: Flux[T] => Publisher[V]) = Flux(jFlux.transform[V](transformer))

  final def asJava(): JFlux[T] = jFlux
}

object Flux {

  private[publisher] def apply[T](jFlux: JFlux[T]): Flux[T] = new Flux[T](jFlux)

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, sources: Publisher[_ <: T]*): Flux[V] = Flux(JFlux.combineLatest(combinator, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, prefetch: Int, sources: Publisher[_ <: T]*) = Flux(JFlux.combineLatest(combinator, prefetch, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, V](source1: Publisher[_ <: T1],
                               source2: Publisher[_ <: T2],
                               combinator: (T1, T2) => V) = Flux(JFlux.combineLatest[T1, T2, V](source1, source2, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, V](source1: Publisher[_ <: T1],
                                   source2: Publisher[_ <: T2],
                                   source3: Publisher[_ <: T3],
                                   combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, V](source1, source2, source3, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, V](source1: Publisher[_ <: T1],
                                       source2: Publisher[_ <: T2],
                                       source3: Publisher[_ <: T3],
                                       source4: Publisher[_ <: T4],
                                       combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, V](source1, source2, source3, source4, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, V](source1: Publisher[_ <: T1],
                                           source2: Publisher[_ <: T2],
                                           source3: Publisher[_ <: T3],
                                           source4: Publisher[_ <: T4],
                                           source5: Publisher[_ <: T5],
                                           combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, V](source1, source2, source3, source4, source5, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param source6    The sixth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, T6, V](source1: Publisher[_ <: T1],
                                               source2: Publisher[_ <: T2],
                                               source3: Publisher[_ <: T3],
                                               source4: Publisher[_ <: T4],
                                               source5: Publisher[_ <: T5],
                                               source6: Publisher[_ <: T6],
                                               combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, T6, V](source1, source2, source3, source4, source5, source6, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], prefetch: Int, combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, prefetch, combinator))

  /**
    * Concat all sources pulled from the supplied
    * [[Iterator]] on [[Publisher.subscribe]] from the passed [[Iterable]] until [[Iterator.hasNext]]
    * returns false. A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Iterable[Publisher[T]]) = Flux(JFlux.concat(sources))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]]): Flux[T] = Flux(JFlux.concat(sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concat(sources, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The array of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Publisher[T]*) = Flux(JFlux.concat(sources: _*))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]]) = Flux(JFlux.concatDelayError[T](sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concatDelayError(sources, prefetch))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    *
    * Errors will be delayed after the current concat backlog if delayUntilEnd is
    * false or after all sources if delayUntilEnd is true.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources       The [[Publisher]] of [[Publisher]] to concat
    * @param delayUntilEnd delay error until all sources have been consumed instead of
    *                      after the current source
    * @param prefetch      the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], delayUntilEnd: Boolean, prefetch: Int) = Flux(JFlux.concatDelayError(sources, delayUntilEnd, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * Any error will be delayed until all sources have been concatenated.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concatDelayError[T](sources: Publisher[T]*) = Flux(JFlux.concatDelayError(sources: _*))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    * <p>
    * Handles backpressure by buffering all signals if the downstream can't keep up.
    *
    * <pre><code>
    * Flux.String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * });
    * <code></pre>
    *
    * @tparam T the value type
    * @param emitter the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
  def create[T](emitter: FluxSink[T] => Unit) = Flux(JFlux.create[T](emitter))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    *
    * <pre><code>
    * Flux.&lt;String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * }, FluxSink.OverflowStrategy.LATEST);
    * <code></pre>
    *
    * @tparam T the value type
    * @param backpressure the backpressure mode, see { @link OverflowStrategy} for the
    *                     available backpressure modes
    * @param emitter      the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
  //  TODO: How to test backpressure?
  def create[T](emitter: FluxSink[T] => Unit, backpressure: OverflowStrategy) = Flux(JFlux.create[T](emitter, backpressure))

  /**
    * Supply a [[Publisher]] everytime subscribe is called on the returned flux. The passed [[scala.Function1[Unit,Publisher[T]]]]
    * will be invoked and it's up to the developer to choose to return a new instance of a [[Publisher]] or reuse
    * one effectively behaving like [[Flux.from]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defer.png" alt="">
    *
    * @param supplier the [[Publisher]] Supplier to call on subscribe
    * @tparam T the type of values passing through the [[Flux]]
    * @return a deferred [[Flux]]
    */
  def defer[T](supplier: () => Publisher[T]): Flux[T] = {
    Flux(JFlux.defer(supplier))
  }

  /**
    * Create a [[Flux]] that completes without emitting any item.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/empty.png" alt="">
    * <p>
    *
    * @tparam T the reified type of the target [[Subscriber]]
    * @return an empty [[Flux]]
    */
  def empty[T](): Flux[T] = Flux(JFlux.empty[T]())

  /**
    * Create a [[Flux]] that completes with the specified error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/error.png" alt="">
    * <p>
    *
    * @param error the error to signal to each [[Subscriber]]
    * @tparam T the reified type of the target [[Subscriber]]
    * @return a new failed  [[Flux]]
    */
  def error[T](error: Throwable): Flux[T] = Flux(JFlux.error[T](error))

  /**
    * Build a [[Flux]] that will only emit an error signal to any new subscriber.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/errorrequest.png" alt="">
    *
    * @param throwable     the error to signal to each [[Subscriber]]
    * @param whenRequested if true, will onError on the first request instead of subscribe().
    * @tparam O the output type
    * @return a new failed [[Flux]]
    */
  def error[O](throwable: Throwable, whenRequested: Boolean): Flux[O] = Flux(JFlux.error(throwable, whenRequested))

  /**
    * Select the fastest source who emitted first onNext or onComplete or onError
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/firstemitting.png" alt="">
    * <p> <p>
    *
    * @param sources The competing source publishers
    * @tparam I The source type of the data sequence
    * @return a new [[Flux}]] eventually subscribed to one of the sources or empty
    */
  def firstEmitting[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.firstEmitting(sources: _*))

  /**
    * Select the fastest source who won the "ambiguous" race and emitted first onNext or onComplete or onError
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/firstemitting.png" alt="">
    * <p> <p>
    *
    * @param sources The competing source publishers
    * @tparam I The source type of the data sequence
    * @return a new [[Flux}]] eventually subscribed to one of the sources or empty
    */
  def firstEmitting[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.firstEmitting[I](sources))

  /**
    * Expose the specified [[Publisher]] with the [[Flux]] API.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/from.png" alt="">
    * <p>
    *
    * @param source the source to decorate
    * @tparam T the source sequence type
    * @return a new [[Flux]]
    */
  def from[T](source: Publisher[_ <: T]): Flux[T] = {
    Flux(JFlux.from(source))
  }

  /**
    * Create a [[Flux]] that emits the items contained in the provided [[scala.Array]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/fromarray.png" alt="">
    * <p>
    *
    * @param array the array to read data from
    * @tparam T the [[Publisher]] type to stream
    * @return a new [[Flux]]
    */
  def fromArray[T <: AnyRef](array: Array[T]): Flux[T] = {
    Flux(JFlux.fromArray[T](array))
  }

  /**
    * Create a [[Flux]] that emits the items contained in the provided [[Iterable]].
    * A new iterator will be created for each subscriber.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/fromiterable.png" alt="">
    * <p>
    *
    * @param it the [[Iterable]] to read data from
    * @tparam T the [[Iterable]] type to stream
    * @return a new [[Flux]]
    */
  def fromIterable[T](it: Iterable[T]) = Flux(JFlux.fromIterable(it))

  /**
    * Create a [[Flux]] that emits the items contained in the provided [[Stream]].
    * A new iterator will be created for each subscriber.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/fromstream.png" alt="">
    * <p>
    *
    * @param s the [[Stream]] to read data from
    * @tparam T the [[Stream]] type to flux
    * @return a new [[Flux]]
    */
  def fromStream[T](s: Stream[T]): Flux[T] = Flux.fromIterable(s)

  /**
    * Generate signals one-by-one via a consumer callback.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/generate.png" alt="">
    * <p>
    *
    * @tparam T the value type emitted
    * @param generator the consumer called with the SynchronousSink
    *                  API instance
    * @return a Reactive [[Flux]] publisher ready to be subscribed
    */
  def generate[T](generator: SynchronousSink[T] => Unit) = Flux(JFlux.generate[T](generator))

  /**
    * Generate signals one-by-one via a function callback.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/generate.png" alt="">
    * <p>
    *
    * @tparam T the value type emitted
    * @tparam S the custom state per subscriber
    * @param stateSupplier called for each incoming Supplier to provide the initial state for the generator bifunction
    * @param generator     the bifunction called with the current state, the SynchronousSink API instance and is
    *                      expected to return a (new) state.
    * @return a Reactive [[Flux]] publisher ready to be subscribed
    */
  def generate[T, S](stateSupplier: Option[Callable[S]], generator: (S, SynchronousSink[T]) => S) = Flux(JFlux.generate[T, S](stateSupplier.orNull, generator))

  /**
    * Generate signals one-by-one via a function callback.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/generate.png" alt="">
    * <p>
    *
    * @tparam T the value type emitted
    * @tparam S the custom state per subscriber
    * @param stateSupplier called for each incoming Supplier to provide the initial state for the generator bifunction
    * @param generator     the bifunction called with the current state, the SynchronousSink API instance and is
    *                      expected to return a (new) state.
    * @param stateConsumer called after the generator has terminated or the downstream cancelled, receiving the last
    *                      state to be handled (i.e., release resources or do other cleanup).
    * @return a Reactive [[Flux]] publisher ready to be subscribed
    */
  def generate[T, S](stateSupplier: Option[Callable[S]], generator: (S, SynchronousSink[T]) => S, stateConsumer: Option[S] => Unit) = Flux(
    JFlux.generate[T, S](stateSupplier.orNull: Callable[S], generator, new Consumer[S] {
      override def accept(t: S): Unit = stateConsumer(Option(t))
    }))

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every period on
    * the global timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/interval.png" alt="">
    * <p>
    *
    * @param period The duration to wait before the next increment
    * @return a new timed [[Flux]]
    */
  def interval(period: Duration): Flux[Long] = Flux(JFlux.interval(period)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every N period of time unit on
    * a global timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/intervald.png" alt="">
    *
    * @param delay  the delay to wait before emitting 0l
    * @param period the period before each following increment
    * @return a new timed [[Flux]]
    */
  def interval(delay: Duration, period: Duration): Flux[Long] = Flux(JFlux.interval(delay, period)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every N milliseconds on
    * the given timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/interval.png" alt="">
    * <p>
    *
    * @param period The number of milliseconds to wait before the next increment
    * @return a new timed [[Flux]]
    */
  def intervalMillis(period: Long): Flux[Long] = Flux(JFlux.intervalMillis(period)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every N milliseconds on
    * the given timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/interval.png" alt="">
    * <p>
    *
    * @param period The duration in milliseconds to wait before the next increment
    * @param timer  a [[TimedScheduler]] instance
    * @return a new timed [[Flux]]
    */
  def intervalMillis(period: Long, timer: TimedScheduler): Flux[Long] = Flux(JFlux.intervalMillis(period, timer)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every N period of time unit on
    * a global timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/intervald.png" alt="">
    *
    * @param delay  the delay in milliseconds to wait before emitting 0l
    * @param period the period in milliseconds before each following increment
    * @return a new timed [[Flux]]
    */
  def intervalMillis(delay: Long, period: Long): Flux[Long] = Flux(JFlux.intervalMillis(delay, period)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits an ever incrementing long starting with 0 every N period of time unit on
    * the given timer. If demand is not produced in time, an onError will be signalled. The [[Flux]] will never
    * complete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/intervald.png" alt="">
    *
    * @param delay  the timespan in milliseconds to wait before emitting 0l
    * @param period the period in milliseconds before each following increment
    * @param timer  the [[TimedScheduler]] to schedule on
    * @return a new timed [[Flux]]
    */
  def intervalMillis(delay: Long, period: Long, timer: TimedScheduler): Flux[Long] = Flux(JFlux.intervalMillis(delay, period, timer)).map(Long2long)

  /**
    * Create a new [[Flux]] that emits the specified items and then complete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/justn.png" alt="">
    * <p>
    *
    * @param firstData the first data object to emit
    * @param data      the consecutive data objects to emit
    * @tparam T the emitted data type
    * @return a new [[Flux]]
    */
  def just[T](firstData: T, data: T*): Flux[T] = {
    Flux(JFlux.just(Seq(firstData) ++ data: _*))
  }

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source a [[Publisher]] of [[Publisher]] sequence to merge
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  //  TODO: How to test merge(...)?
  def merge[T](source: Publisher[Publisher[_ <: T]]): Flux[T] = Flux(JFlux.merge[T](source))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source      a [[Publisher]] of [[Publisher]] sequence to merge
    * @param concurrency the request produced to the main source thus limiting concurrent merge backlog
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def merge[T](source: Publisher[Publisher[_ <: T]], concurrency: Int): Flux[T] = Flux(JFlux.merge[T](source, concurrency))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source      a [[Publisher]] of [[Publisher]] sequence to merge
    * @param concurrency the request produced to the main source thus limiting concurrent merge backlog
    * @param prefetch    the inner source request size
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def merge[T](source: Publisher[Publisher[_ <: T]], concurrency: Int, prefetch: Int): Flux[T] = Flux(JFlux.merge[T](source, concurrency, prefetch))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] into an interleaved merged sequence.
    * [[Iterable.iterator()]] will be called for each [[Publisher.subscribe]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources the [[scala.Iterable]] to lazily iterate on [[Publisher.subscribe]]
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.merge[I](sources))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](sources))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources  the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @param prefetch the inner source request size
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](prefetch: Int, sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](prefetch, sources: _*))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @param prefetch   the inner source request size
    * @param delayError should any error be delayed after current merge backlog
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](prefetch: Int, delayError: Boolean, sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](prefetch, delayError, sources: _*))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into
    * an ordered merged sequence. Unlike concat, the inner publishers are subscribed to
    * eagerly. Unlike merge, their emitted values are merged into the final sequence in
    * subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources a [[Publisher]] of [[Publisher]] sequence to merge
    * @tparam T the merged type
    * @return a merged  [[Flux]]
    */
  def mergeSequential[T](sources: Publisher[Publisher[T]]): Flux[T] = Flux(JFlux.mergeSequential[T](sources))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into
    * an ordered merged sequence. Unlike concat, the inner publishers are subscribed to
    * eagerly. Unlike merge, their emitted values are merged into the final sequence in
    * subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources        a [[Publisher]] of [[Publisher]] sequence to merge
    * @param delayError     should any error be delayed after current merge backlog
    * @param prefetch       the inner source request size
    * @param maxConcurrency the request produced to the main source thus limiting concurrent merge backlog
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[T](sources: Publisher[Publisher[T]], delayError: Boolean, maxConcurrency: Int, prefetch: Int): Flux[T] = {
    Flux(JFlux.mergeSequential[T](sources, delayError, maxConcurrency, prefetch))
  }

  /**
    * Merge a number of [[Publisher]] sequences into an ordered merged sequence.
    * Unlike concat, the inner publishers are subscribed to eagerly. Unlike merge, their
    * emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources a number of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.mergeSequential(sources: _*))

  /**
    * Merge a number of [[Publisher]] sequences into an ordered merged sequence.
    * Unlike concat, the inner publishers are subscribed to eagerly. Unlike merge, their
    * emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param delayError should any error be delayed after current merge backlog
    * @param prefetch   the inner source request size
    * @param sources    a number of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](prefetch: Int, delayError: Boolean, sources: Publisher[_ <: I]*): Flux[I] =
    Flux(JFlux.mergeSequential(prefetch, delayError, sources: _*))

  /**
    * Merge [[Publisher]] sequences from an [[Iterable]] into an ordered merged
    * sequence. Unlike concat, the inner publishers are subscribed to eagerly. Unlike
    * merge, their emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources an [[Iterable]] of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.mergeSequential[I](sources))

  /**
    * Merge [[Publisher]] sequences from an [[Iterable]] into an ordered merged
    * sequence. Unlike concat, the inner publishers are subscribed to eagerly. Unlike
    * merge, their emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources        an [[Iterable]] of [[Publisher]] sequences to merge
    * @param delayError     should any error be delayed after current merge backlog
    * @param maxConcurrency the request produced to the main source thus limiting concurrent merge backlog
    * @param prefetch       the inner source request size
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Iterable[Publisher[_ <: I]], delayError: Boolean, maxConcurrency: Int, prefetch: Int): Flux[I] =
    Flux(JFlux.mergeSequential[I](sources, delayError, maxConcurrency, prefetch))

  /**
    * Create a [[Flux]] that will never signal any data, error or completion signal.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/never.png" alt="">
    * <p>
    *
    * @tparam T the [[Subscriber]] type target
    * @return a never completing [[Flux]]
    */
  def never[T]() = Flux(JFlux.never[T]())

  /**
    * Build a [[Flux]] that will only emit a sequence of incrementing integer from `start` to `start + count` then complete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/range.png" alt="">
    *
    * @param start the first integer to be emit
    * @param count the number ot times to emit an increment including the first value
    * @return a ranged [[Flux]]
    */
  def range(start: Int, count: Int) = Flux(JFlux.range(start, count))

  /**
    * Build a [[reactor.core.publisher.FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]]. The
    * [[Flux]] will complete once both the publishers source and the last switched to [[Publisher]] have completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/switchonnext.png"
    * alt="">
    *
    * @param mergedPublishers The { @link Publisher} of switching [[Publisher]] to subscribe to.
    * @tparam T the produced type
    * @return a [[reactor.core.publisher.FluxProcessor]] accepting publishers and producing T
    */
  //  TODO: How to test these switchOnNext?
  def switchOnNext[T](mergedPublishers: Publisher[Publisher[_ <: T]]) = Flux(JFlux.switchOnNext[T](mergedPublishers))

  /**
    * Build a [[reactor.core.publisher.FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]]. The
    * [[Flux]] will complete once both the publishers source and the last switched to [[Publisher]] have completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/switchonnext.png"
    * alt="">
    *
    * @param mergedPublishers The { @link Publisher} of switching { @link Publisher} to subscribe to.
    * @param prefetch         the inner source request size
    * @tparam T the produced type
    * @return a [[reactor.core.publisher.FluxProcessor]] accepting publishers and producing T
    */
  def switchOnNext[T](mergedPublishers: Publisher[Publisher[_ <: T]], prefetch: Int) = Flux(JFlux.switchOnNext[T](mergedPublishers, prefetch))

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the values from a
    * Publisher derived from the same resource and makes sure the resource is released if the sequence terminates or
    * the Subscriber cancels.
    * <p>
    * Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup Consumer
    * may override the terminal even.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a [[java.util.concurrent.Callable]] that is called on subscribe
    * @param sourceSupplier   a [[Publisher]] factory derived from the supplied resource
    * @param resourceCleanup  invoked on completion
    * @tparam T emitted type
    * @tparam D resource type
    * @return new [[Flux]]
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit): Flux[T] =
    Flux(JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup))

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the values from a
    * Publisher derived from the same resource and makes sure the resource is released if the sequence terminates or
    * the Subscriber cancels.
    * <p>
    * <ul> <li>Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup
    * Consumer may override the terminal even.</li> <li>Non-eager cleanup will drop any exception.</li> </ul>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a [[java.util.concurrent.Callable]] that is called on subscribe
    * @param sourceSupplier   a [[Publisher]] factory derived from the supplied resource
    * @param resourceCleanup  invoked on completion
    * @param eager            true to clean before terminating downstream subscribers
    * @tparam T emitted type
    * @tparam D resource type
    * @return new Stream
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit, eager: Boolean): Flux[T] =
    Flux(JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup, eager))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam O  The produced output after transformation by the combinator
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, O](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], combinator: (T1, T2) => O) =
    Flux(JFlux.zip[T1, T2, O](source1, source2, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2]): Flux[(T1, T2)] =
    Flux(JFlux.zip[T1, T2](source1, source2))
      .map(tupleTwo2ScalaTuple2)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3]): Flux[(T1, T2, T3)] =
    Flux(JFlux.zip[T1, T2, T3](source1, source2, source3))
      .map(tupleThree2ScalaTuple3)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4]): Flux[(T1, T2, T3, T4)] =
    Flux(JFlux.zip[T1, T2, T3, T4](source1, source2, source3, source4))
      .map(tupleFour2ScalaTuple4)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @param source5 The fifth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4, T5](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5]): Flux[(T1, T2, T3, T4, T5)] =
    Flux(JFlux.zip[T1, T2, T3, T4, T5](source1, source2, source3, source4, source5))
      .map(tupleFive2ScalaTuple5)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @param source5 The fifth upstream [[Publisher]] to subscribe to.
    * @param source6 The sixth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam T6 type of the value from source6
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4, T5, T6](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5], source6: Publisher[_ <: T6]): Flux[(T1, T2, T3, T4, T5, T6)] =
    Flux(JFlux.zip[T1, T2, T3, T4, T5, T6](source1, source2, source3, source4, source5, source6))
      .map(tupleSix2ScalaTuple6)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    *
    * The [[Iterable.iterator]] will be called on each [[Publisher.subscribe]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    *
    * @param sources    the [[Iterable]] to iterate on [[Publisher.subscribe]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[O](sources: Iterable[_ <: Publisher[_]], combinator: Array[_] => O) =
    Flux(JFlux.zip[O](sources, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    *
    * The [[Iterable.iterator]] will be called on each [[Publisher.subscribe]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    *
    * @param sources    the [[Iterable]] to iterate on [[Publisher.subscribe]]
    * @param prefetch   the inner source request size
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[O](sources: Iterable[_ <: Publisher[_]], prefetch: Int, combinator: Array[_] => O) =
    Flux(JFlux.zip[O](sources, prefetch, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I the type of the input sources
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[I, O](combinator: Array[AnyRef] => O, sources: Publisher[_ <: I]*) =
    Flux(JFlux.zip[I, O](combinator, sources: _*))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @param prefetch   individual source request size
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I the type of the input sources
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[I, O](combinator: Array[AnyRef] => O, prefetch: Int, sources: Publisher[_ <: I]*) =
    Flux(JFlux.zip[I, O](combinator, prefetch, sources: _*))
}
