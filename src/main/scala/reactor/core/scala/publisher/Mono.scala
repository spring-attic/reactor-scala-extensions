/*
* This file is part of Scala wrapper for reactor-core.
*
* Scala wrapper for reactor-core is free software: you can redistribute
* it and/or modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation, either version 3 of the
* License, or any later version.
*
* Foobar is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Scala wrapper for reactor-core.
* If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
*/

package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean, Iterable => JIterable, Long => JLong}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Callable, CompletableFuture}
import java.util.function.{BiConsumer, BiFunction, BiPredicate, Consumer, Function, Predicate, Supplier}
import java.util.logging.Level

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.Disposable
import reactor.core.publisher.{MonoProcessor, MonoSink, Signal, SignalType, SynchronousSink, Flux => JFlux, Mono => JMono}
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.Scheduler
import reactor.util.function._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
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
  * more than 1 emission. Its alternative enforcing [[Mono]] input is [[Mono.then then]].
  *
  * <p>`Mono[Unit]` should be used for [[Publisher]] that just completes without any value.
  *
  * <p>It is intended to be used in implementations and return types, input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>Note that using state in the `scala.Function` / lambdas used within Mono operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the type of the single value of this class
  * @see Flux
  */
class Mono[T] private(private val jMono: JMono[T]) extends Publisher[T] with MapablePublisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jMono.subscribe(s)

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
  final def as[P](transformer: (Mono[T] => P)): P = transformer(this)

  /**
    * Combine the result from this mono and another into a [[scala.Tuple2]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/and.png" alt="">
    * <p>
    *
    * @param other the [[Mono]] to combine with
    * @tparam T2 the element type of the other Mono instance
    * @return a new combined Mono
    * @see [[Mono.when]]
    */
  final def and[T2](other: Mono[_ <: T2]): Mono[(T, T2)] = {
    Mono[(T, T2)](
      jMono.and[T2](other.jMono)
        .map((t: Tuple2[T, T2]) => tupleTwo2ScalaTuple2(t))
    )
  }

  /**
    * An alias for [[Mono.and]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/and.png" alt="">
    * <p>
    *
    * @param other the [[Mono]] to combine with
    * @tparam T2 the element type of the other Mono instance
    * @return a new combined Mono
    * @see [[Mono.when]]
    */
  final def ++[T2](other: Mono[_ <: T2]): Mono[(T, T2)] = {
    and(other)
  }

  /**
    * Combine the result from this mono and another into an arbitrary `O` object,
    * as defined by the provided `combinator` function.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/and.png" alt="">
    * <p>
    *
    * @param other      the [[Mono]] to combine with
    * @param combinator a [[scala.Function2]] combinator function when both sources
    *                   complete
    * @tparam T2 the element type of the other Mono instance
    * @tparam O  the element type of the combination
    * @return a new combined Mono
    * @see [[Mono.when]]
    */
  final def and[T2, O](other: Mono[T2], combinator: (T, T2) => O): Mono[O] = {
    Mono[O](jMono.and(other.jMono, combinator))
  }

  /**
    * Wait for the result from this mono, use it to create a second mono via the
    * provided `rightGenerator` function and combine both results into a [[scala.Tuple2]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/and.png" alt="">
    * <p>
    *
    * @param rightGenerator the [[scala.Function1]] to generate a `Mono` to combine with
    * @tparam T2 the element type of the other Mono instance
    * @return a new combined Mono
    */
  final def and[T2](rightGenerator: (T => Mono[T2])): Mono[(T, T2)] = {
    Mono[(T, T2)](
      jMono.and[T2](rightGenerator).map((t: Tuple2[T, T2]) => tupleTwo2ScalaTuple2(t))
    )
  }

  /**
    * Wait for the result from this mono, use it to create a second mono via the
    * provided `rightGenerator` function and combine both results into an arbitrary
    * `O` object, as defined by the provided `combinator` function.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/and.png" alt="">
    * <p>
    *
    * @param rightGenerator the [[scala.Function1]] to generate a `Mono` to combine with
    * @param combinator     a [[scala.Function2]] combinator function when both sources complete
    * @tparam T2 the element type of the other Mono instance
    * @tparam O  the element type of the combination
    * @return a new combined Mono
    */

  final def and[T2, O](rightGenerator: (T => Mono[T2]), combinator: (T, T2) => O): Mono[O] = {
    Mono[O](
      jMono.and[T2, O](rightGenerator, combinator)
    )
  }

  /**
    * Intercepts the onSubscribe call and makes sure calls to Subscription methods
    * only happen after the child Subscriber has returned from its onSubscribe method.
    *
    * <p>This helps with child Subscribers that don't expect a recursive call from
    * onSubscribe into their onNext because, for example, they request immediately from
    * their onSubscribe but don't finish their preparation before that and onNext
    * runs into a half-prepared state. This can happen with non Reactor based
    * Subscribers.
    *
    * @return non reentrant onSubscribe [[Mono]]
    */
  final def awaitOnSubscribe(): Mono[T] = new Mono[T](
    jMono.awaitOnSubscribe()
  )

  /**
    * Block until a next signal is received, will return null if onComplete, T if onNext, throw a
    * `Exceptions.DownstreamException` if checked error or origin RuntimeException if unchecked.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/block.png" alt="">
    * <p>
    *
    * @return T the result
    */
  final def block(): T = jMono.block()

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
    * @param timeout maximum time period to wait for before raising a [[RuntimeException]]
    * @return T the result
    */
  final def block(timeout: Duration): T = jMono.block(timeout)

  /**
    * Cast the current [[Mono]] produced type into a target produced type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/cast1.png" alt="">
    *
    * @tparam E the { @link Mono} output type
    * @param clazz the target type to cast to
    * @return a casted [[Mono]]
    */
  final def cast[E](clazz: Class[E]) = new Mono[E](
    jMono.cast(clazz)
  )

  /**
    * Turn this [[Mono]] into a hot source and cache last emitted signals for further [[Subscriber]].
    * Completion and Error will also be replayed.
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/cache1.png"
    * alt="">
    *
    * @return a replaying [[Mono]]
    */
  final def cache(): Mono[T] = Mono[T](
    jMono.cache()
  )

  /**
    * Prepare this [[Mono]] so that subscribers will cancel from it on a
    * specified
    * [[reactor.core.scheduler.Scheduler]].
    *
    * @param scheduler the [[reactor.core.scheduler.Scheduler]] to signal cancel  on
    * @return a scheduled cancel [[Mono]]
    */
  final def cancelOn(scheduler: Scheduler): Mono[T] = Mono[T](
    jMono.cancelOn(scheduler)
  )

  /**
    * Defer the given transformation to this [[Mono]] in order to generate a
    * target [[Mono]] type. A transformation will occur for each
    * [[org.reactivestreams.Subscriber]].
    *
    * `flux.compose(Mono::from).subscribe()`
    *
    * @param transformer the function to immediately map this [[Mono]] into a target [[Mono]]
    *                    instance.
    * @tparam V the item type in the returned [[org.reactivestreams.Publisher]]
    * @return a new [[Mono]]
    * @see [[Mono.as]] for a loose conversion to an arbitrary type
    */
  final def compose[V](transformer: (Mono[T] => Publisher[V])): Mono[V] = {
    val transformerFunction = new Function[JMono[T], Publisher[V]] {
      override def apply(t: JMono[T]): Publisher[V] = transformer(Mono.this)
    }
    Mono[V](
      jMono.compose(transformerFunction)
    )
  }

  /**
    * Concatenate emissions of this [[Mono]] with the provided [[Publisher]]
    * (no interleave).
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat1.png" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[Flux]]
    * @return a concatenated [[Flux]]
    */
  final def concatWith(other: Publisher[T]): Flux[T] = Flux(jMono.concatWith(other))

  /**
    * Provide a default unique value if this mono is completed without any data
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[Mono]]
    * @see [[Flux.defaultIfEmpty]]
    */
  final def defaultIfEmpty(defaultV: T): Mono[T] = new Mono[T](
    jMono.defaultIfEmpty(defaultV)
  )

  /**
    * Delay the [[Mono.subscribe subscription]] to this [[Mono]] source until the given
    * period elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/delaysubscription1.png" alt="">
    *
    * @param delay duration before subscribing this [[Mono]]
    * @return a delayed [[Mono]]
    *
    */
  final def delaySubscription(delay: Duration): Mono[T] = Mono(jMono.delaySubscription(delay))

  /**
    * Delay the [[Mono.subscribe subscription]] to this [[Mono]] source until the given
    * [[Duration]] elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/delaysubscription1.png" alt="">
    *
    * @param delay [[Duration]] before subscribing this [[Mono]]
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return a delayed [[Mono]]
    *
    */
  final def delaySubscription(delay: Duration, timer: Scheduler) = Mono(jMono.delaySubscription(delay, timer))

  /**
    * Delay the subscription to this [[Mono]] until another [[Publisher]]
    * signals a value or completes.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/delaysubscriptionp1.png" alt="">
    *
    * @param subscriptionDelay a
    *                          [[Publisher]] to signal by next or complete this [[Mono.subscribe]]
    * @tparam U the other source type
    * @return a delayed [[Mono]]
    *
    */
  final def delaySubscription[U](subscriptionDelay: Publisher[U]): Mono[T] = new Mono[T](
    jMono.delaySubscription(subscriptionDelay)
  )

  /**
    * A "phantom-operator" working only if this
    * [[Mono]] is a emits onNext, onError or onComplete [[reactor.core.publisher.Signal]]. The relative [[org.reactivestreams.Subscriber]]
    * callback will be invoked, error [[reactor.core.publisher.Signal]] will trigger onError and complete [[reactor.core.publisher.Signal]] will trigger
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/dematerialize1.png" alt="">
    *
    * @tparam X the dematerialized type
    * @return a dematerialized [[Mono]]
    */
  final def dematerialize[X](): Mono[X] = new Mono[X](
    jMono.dematerialize[X]()
  )

  /**
    * Triggered after the [[Mono]] terminates, either by completing downstream successfully or with an error.
    * The arguments will be null depending on success, success with data and error:
    * <ul>
    * <li>null, null : completed without data</li>
    * <li>T, null : completed with data</li>
    * <li>null, Throwable : failed with/without data</li>
    * </ul>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doafterterminate1.png" alt="">
    * <p>
    *
    * @param afterTerminate the callback to call after [[org.reactivestreams.Subscriber.onNext]], [[org.reactivestreams.Subscriber.onComplete]] without preceding [[org.reactivestreams.Subscriber.onNext]] or [[org.reactivestreams.Subscriber.onError]]
    * @return a new [[Mono]]
    */
  final def doAfterTerminate(afterTerminate: (_ >: T, Throwable) => Unit): Mono[T] = {
    val afterTerminalFunction = new BiConsumer[T, Throwable] {
      override def accept(t: T, u: Throwable): Unit = afterTerminate(t, u)
    }
    new Mono[T](
      jMono.doAfterTerminate(afterTerminalFunction)
    )
  }

  final def doFinally(onFinally: (SignalType => Unit)): Mono[T] = {
    val onFinallyFunction = new Consumer[SignalType] {
      override def accept(t: SignalType): Unit = onFinally(t)
    }
    new Mono[T](
      jMono.doFinally(onFinallyFunction)
    )
  }

  /**
    * Triggered when the [[Mono]] is cancelled.
    *
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/dooncancel.png" alt="">
    * <p>
    *
    * @param onCancel the callback to call on [[org.reactivestreams.Subscriber.cancel]]
    * @return a new [[Mono]]
    */
  final def doOnCancel(onCancel: () => Unit): Mono[T] = {
    val onCancelFunction = new Runnable {
      override def run(): Unit = onCancel()
    }
    new Mono[T](
      jMono.doOnCancel(onCancelFunction)
    )
  }

  final def doOnNext(onNext: (T => Unit)): Mono[T] = {
    val onNextFunction = new Consumer[T] {
      override def accept(t: T): Unit = onNext(t)
    }
    new Mono[T](
      jMono.doOnNext(onNextFunction)
    )
  }

  /**
    * Triggered when the [[Mono]] completes successfully.
    *
    * <ul>
    * <li>null : completed without data</li>
    * <li>T: completed with data</li>
    * </ul>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonsuccess.png" alt="">
    * <p>
    *
    * @param onSuccess the callback to call on, argument is null if the [[Mono]]
    *                  completes without data
    *                  [[org.reactivestreams.Subscriber.onNext]] or [[org.reactivestreams.Subscriber.onComplete]] without preceding [[org.reactivestreams.Subscriber.onNext]]
    * @return a new [[Mono]]
    */
  final def doOnSuccess(onSuccess: (T => Unit)): Mono[T] = {
    val onSuccessFunction = new Consumer[T] {
      override def accept(t: T): Unit = onSuccess(t)
    }
    new Mono[T](
      jMono.doOnSuccess(onSuccessFunction)
    )
  }

  /**
    * Triggered when the [[Mono]] completes with an error.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonerror1.png" alt="">
    * <p>
    *
    * @param onError the error callback to call on [[org.reactivestreams.Subscriber.onError]]
    * @return a new [[Mono]]
    */
  final def doOnError(onError: (Throwable => Unit)): Mono[T] = new Mono[T](
    jMono.doOnError(onError)
  )

  /**
    * Triggered when the [[Mono]] completes with an error matching the given exception type.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonerrorw.png" alt="">
    *
    * @param exceptionType the type of exceptions to handle
    * @param onError       the error handler for each error
    * @tparam E type of the error to handle
    * @return an observed  [[Mono]]
    *
    */
  final def doOnError[E <: Throwable](exceptionType: Class[E], onError: (E => Unit)): Mono[T] = new Mono[T](
    jMono.doOnError(exceptionType, onError: Consumer[E])
  )

  /**
    * Triggered when the [[Mono]] completes with an error matching the given exception.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonerrorw.png" alt="">
    *
    * @param predicate the matcher for exceptions to handle
    * @param onError   the error handler for each error
    * @return an observed  [[Mono]]
    *
    */
  final def doOnError(predicate: (Throwable => Boolean), onError: (Throwable => Unit)): Mono[T] = new Mono[T](
    jMono.doOnError(predicate: Predicate[Throwable], onError: Consumer[Throwable])
  )

  /**
    * Attach a `Long consumer` to this [[Mono]] that will observe any request to this [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonrequest1.png" alt="">
    *
    * @param consumer the consumer to invoke on each request
    * @return an observed  [[Mono]]
    */
  final def doOnRequest(consumer: Long => Unit) = new Mono[T](
    jMono.doOnRequest(consumer)
  )

  /**
    * Triggered when the [[Mono]] is subscribed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonsubscribe.png" alt="">
    * <p>
    *
    * @param onSubscribe the callback to call on [[Subscriber#onSubscribe]]
    * @return a new [[Mono]]
    */
  final def doOnSubscribe(onSubscribe: Subscription => Unit) = new Mono[T](
    jMono.doOnSubscribe(onSubscribe)
  )

  /**
    * Triggered when the [[Mono]] terminates, either by completing successfully or with an error.
    *
    * <ul>
    * <li>null, null : completing without data</li>
    * <li>T, null : completing with data</li>
    * <li>null, Throwable : failing with/without data</li>
    * </ul>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonterminate1.png" alt="">
    * <p>
    *
    * @param onTerminate the callback to call [[Subscriber.onNext]], [[Subscriber.onComplete]] without preceding [[Subscriber.onNext]] or [[Subscriber.onError]]
    * @return a new [[Mono]]
    */
  final def doOnTerminate(onTerminate: (T, Throwable) => Unit) = Mono(jMono.doOnTerminate(new BiConsumer[T, Throwable] {
    override def accept(t: T, u: Throwable): Unit = onTerminate(t, u)
  }))

  val javaTupleLongAndT2ScalaTupleLongAndT = new Function[Tuple2[JLong, T], (Long, T)] {
    override def apply(t: Tuple2[JLong, T]): (Long, T) = (Long2long(t.getT1), t.getT2)
  }

  /**
    * Map this [[Mono]] sequence into [[scala.Tuple2]] of T1 [[Long]] timemillis and T2
    * `T` associated data. The timemillis corresponds to the elapsed time between the subscribe and the first
    * next signal.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/elapsed1.png" alt="">
    *
    * @return a transforming [[Mono]]that emits a tuple of time elapsed in milliseconds and matching data
    */
  final def elapsed() = Mono[(Long, T)](jMono.elapsed().map(javaTupleLongAndT2ScalaTupleLongAndT))

  /**
    * Map this [[Mono]] sequence into [[scala.Tuple2]] of T1 [[Long]] timemillis and T2
    * `T` associated data. The timemillis corresponds to the elapsed time between the subscribe and the first
    * next signal.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/elapsed1.png" alt="">
    *
    * @param scheduler the [[Scheduler]] to read time from
    * @return a transforming [[Mono]] that emits a tuple of time elapsed in milliseconds and matching data
    */
  final def elapsed(scheduler: Scheduler): Mono[(Long, T)] = Mono(jMono.elapsed(scheduler).map(javaTupleLongAndT2ScalaTupleLongAndT))

  /**
    * Test the result if any of this [[Mono]] and replay it if predicate returns true.
    * Otherwise complete without value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/filter1.png" alt="">
    * <p>
    *
    * @param tester the predicate to evaluate
    * @return a filtered [[Mono]]
    */
  final def filter(tester: T => Boolean) = Mono[T](jMono.filter(tester))

  /**
    * If this [[Mono]] is valued, test the value asynchronously using a generated
    * [[Publisher[Boolean]]] test. The value from the Mono is replayed if the
    * first item emitted by the test is `true`. It is dropped if the test is
    * either empty or its first emitted value is false``.
    * <p>
    * Note that only the first value of the test publisher is considered, and unless it
    * is a [[Mono]], test will be cancelled after receiving that first value.
    *
    * @param asyncPredicate the function generating a [[Publisher]] of [[Boolean]]
    *                                                         to filter the Mono with
    * @return a filtered [[Mono]]
    */
  final def filterWhen(asyncPredicate: T => _ <: Publisher[Boolean] with MapablePublisher[Boolean]): Mono[T] = {
    val asyncPredicateFunction = new Function[T, Publisher[JBoolean]] {
      override def apply(t: T): Publisher[JBoolean] = asyncPredicate(t).map(Boolean2boolean(_))
    }
    Mono(jMono.filterWhen(asyncPredicateFunction))
  }

  /**
    * Transform the item emitted by this [[Mono]] into a Publisher, then forward
    * its emissions into the returned [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmap1.png" alt="">
    * <p>
    *
    * @param mapper the
    *               [[Function1]] to produce a sequence of R from the the eventual passed [[Subscriber.onNext]]
    * @tparam R the merged sequence type
    * @return a new [[Flux]] as the sequence is not guaranteed to be single at most
    */
  final def flatMapMany[R](mapper: T => Publisher[R]): Flux[R] = Flux(jMono.flatMapMany(mapper))

  /**
    * Transform the signals emitted by this [[Mono]] into a Publisher, then forward
    * its emissions into the returned [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmaps1.png" alt="">
    * <p>
    *
    * @param mapperOnNext     the [[Function1]] to call on next data and returning a sequence to merge
    * @param mapperOnError    the[[Function1]] to call on error signal and returning a sequence to merge
    * @param mapperOnComplete the [[Function1]] to call on complete signal and returning a sequence to merge
    * @tparam R the type of the produced inner sequence
    * @return a new [[Flux]] as the sequence is not guaranteed to be single at most
    * @see [[Flux.flatMap]]
    */
  final def flatMapMany[R](mapperOnNext: T => Publisher[R],
                           mapperOnError: Throwable => Publisher[R],
                           mapperOnComplete: () => Publisher[R]) =
    Flux(jMono.flatMapMany(mapperOnNext, mapperOnError, mapperOnComplete))

  /**
    * Transform the item emitted by this [[Mono]] into [[Iterable]], , then forward
    * its elements into the returned [[Flux]]. The prefetch argument allows to
    * give an
    * arbitrary prefetch size to the inner [[Iterable]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmap.png" alt="">
    *
    * @param mapper the [[Function1]] to transform input item into a sequence [[Iterable]]
    * @tparam R the merged output sequence type
    * @return a merged [[Flux]]
    *
    */
  final def flatMapIterable[R](mapper: T => Iterable[R]): Flux[R] = Flux(
    jMono.flatMapIterable(mapper.andThen(it => it.asJava))
  )

  /**
    * Convert this [[Mono]] to a [[Flux]]
    *
    * @return a [[Flux]] variant of this [[Mono]]
    */
  final def flux(): Flux[T] = Flux(jMono.flux())

  /**
    * Emit a single boolean true if this [[Mono]] has an element.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/haselement.png" alt="">
    *
    * @return a new [[Mono]] with <code>true</code> if a value is emitted and <code>false</code>
    *                       otherwise
    */
  final def hasElement = Mono[Boolean](
    jMono.hasElement.map[Boolean](scalaFunction2JavaFunction((jb: JBoolean) => boolean2Boolean(jb.booleanValue())))
  )

  /**
    * Handle the items emitted by this [[Mono]] by calling a biconsumer with the
    * output sink for each onNext. At most one [[SynchronousSink.next]]
    * call must be performed and/or 0 or 1 [[SynchronousSink.error]] or
    * [[SynchronousSink.complete]].
    *
    * @param handler the handling `BiConsumer`
    * @tparam R the transformed type
    * @return a transformed [[Mono]]
    */
  final def handle[R](handler: (T, SynchronousSink[R]) => Unit) = Mono[R](
    jMono.handle(handler)
  )

  /**
    * Hides the identity of this [[Mono]] instance.
    *
    * <p>The main purpose of this operator is to prevent certain identity-based
    * optimizations from happening, mostly for diagnostic purposes.
    *
    * @return a new [[Mono]] instance
    */
  //TODO: How to test this?
  final def hide = Mono[T](jMono.hide())

  /**
    * Ignores onNext signal (dropping it) and only reacts on termination.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignoreelement.png" alt="">
    * <p>
    *
    * @return a new completable [[Mono]].
    */
  final def ignoreElement = Mono[T](jMono.ignoreElement())

  //  TODO: How to test all these .log(...) variants?
  final def log: Mono[T] = {
    new Mono[T](jMono.log())
  }

  final def log(category: String): Mono[T] = {
    new Mono[T](jMono.log(category))
  }

  final def log(category: String, level: Level, options: SignalType*): Mono[T] = {
    new Mono[T](jMono.log(category, level, options: _*))
  }

  final def log(category: String, level: Level, showOperatorLine: Boolean, options: SignalType*): Mono[T] = {
    new Mono[T](jMono.log(category, level, showOperatorLine, options: _*))
  }

  final def map[R](mapper: T => R): Mono[R] = {
    Mono(jMono.map(mapper))
  }

  /**
    * Transform the error emitted by this [[Mono]] by applying a function.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/maperror.png" alt="">
    * <p>
    *
    * @param mapper the error transforming [[Function1]]
    * @return a transformed [[Mono]]
    */
  final def onErrorMap(mapper: Throwable => Throwable): Mono[T] = Mono[T](jMono.onErrorMap(mapper))

  /**
    * Transform the error emitted by this [[Mono]] by applying a function if the
    * error matches the given type, otherwise let the error flow.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/maperror.png" alt="">
    * <p>
    *
    * @param type   the type to match
    * @param mapper the error transforming [[Function1]]
    * @tparam E the error type
    * @return a transformed [[Mono]]
    */
  final def onErrorMap[E <: Throwable](`type`: Class[E], mapper: E => Throwable): Mono[T] = Mono[T](jMono.onErrorMap(`type`, mapper))

  /**
    * Transform the error emitted by this [[Mono]] by applying a function if the
    * error matches the given predicate, otherwise let the error flow.
    * <p>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/maperror.png"
    * alt="">
    *
    * @param predicate the error predicate
    * @param mapper    the error transforming [[Function1]]
    * @return a transformed [[Mono]]
    */
  final def onErrorMap(predicate: Throwable => Boolean, mapper: Throwable => Throwable): Mono[T] = Mono[T](jMono.onErrorMap(predicate, mapper))

  /**
    * Transform the incoming onNext, onError and onComplete signals into [[Signal]].
    * Since the error is materialized as a `Signal`, the propagation will be stopped and onComplete will be
    * emitted. Complete signal will first emit a `Signal.complete()` and then effectively complete the flux.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/materialize1.png" alt="">
    *
    * @return a [[Mono]] of materialized [[Signal]]
    */
  final def materialize() = new Mono[Signal[T]](jMono.materialize())

  /**
    * Merge emissions of this [[Mono]] with the provided [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/merge1.png" alt="">
    * <p>
    *
    * @param other the other [[Publisher]] to merge with
    * @return a new [[Flux]] as the sequence is not guaranteed to be at most 1
    */
  final def mergeWith(other: Publisher[_ <: T]) = Flux(jMono.mergeWith(other))

  final def or(other: Mono[_ <: T]): Mono[T] = {
    new Mono[T](jMono.or(other.jMono))
  }

  final def ofType[U](clazz: Class[U]): Mono[U] = {
    new Mono[U](jMono.ofType(clazz))
  }

  /**
    * Subscribe to a returned fallback publisher when any error occurs.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwise.png" alt="">
    * <p>
    *
    * @param fallback the function to map an alternative { @link Mono}
    * @return an alternating [[Mono]] on source onError
    * @see [[Flux.onErrorResume]]
    */
  final def onErrorResume(fallback: Throwable => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).jMono
    }
    Mono[T](jMono.onErrorResume(fallbackFunction))
  }

  /**
    * Subscribe to a returned fallback publisher when an error matching the given type
    * occurs.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwise.png"
    * alt="">
    *
    * @param type     the error type to match
    * @param fallback the [[Function1]] mapping the error to a new [[Mono]]
    *                             sequence
    * @tparam E the error type
    * @return a new [[Mono]]
    * @see [[Flux.onErrorResume]]
    */
  final def onErrorResume[E <: Throwable](`type`: Class[E], fallback: E => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction = new Function[E, JMono[_ <: T]] {
      override def apply(t: E): JMono[_ <: T] = fallback(t).jMono
    }
    Mono[T](jMono.onErrorResume(`type`, fallbackFunction))
  }

  /**
    * Subscribe to a returned fallback publisher when an error matching the given predicate
    * occurs.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwise.png"
    * alt="">
    *
    * @param predicate the error predicate to match
    * @param fallback  the [[Function1]] mapping the error to a new [[Mono]]
    *                              sequence
    * @return a new [[Mono]]
    * @see Flux#onErrorResume
    */
  final def onErrorResume(predicate: Throwable => Boolean, fallback: Throwable => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).jMono
    }
    Mono[T](jMono.onErrorResume(predicate, fallbackFunction))
  }

  /**
    * Provide an alternative [[Mono]] if this mono is completed without data
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwiseempty.png" alt="">
    * <p>
    *
    * @param alternate the alternate mono if this mono is empty
    * @return an alternating [[Mono]] on source onComplete without elements
    * @see [[Flux.switchIfEmpty]]
    */
  final def switchIfEmpty(alternate: Mono[_ <: T]): Mono[T] = Mono[T](jMono.switchIfEmpty(alternate.jMono))

  /**
    * Simply emit a captured fallback value when any error is observed on this [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwisereturn.png" alt="">
    * <p>
    *
    * @param fallback the value to emit if an error occurs
    * @return a new falling back [[Mono]]
    */
  final def onErrorReturn(fallback: T): Mono[T] = Mono[T](jMono.onErrorReturn(fallback))

  /**
    * Simply emit a captured fallback value when an error of the specified type is
    * observed on this [[Mono]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwisereturn.png" alt="">
    *
    * @param type          the error type to match
    * @param fallbackValue the value to emit if a matching error occurs
    * @tparam E the error type
    * @return a new falling back [[Mono]]
    */
  final def onErrorReturn[E <: Throwable](`type`: Class[E], fallbackValue: T) = Mono[T](jMono.onErrorReturn(`type`, fallbackValue))

  /**
    * Simply emit a captured fallback value when an error matching the given predicate is
    * observed on this [[Mono]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwisereturn.png" alt="">
    *
    * @param predicate     the error predicate to match
    * @param fallbackValue the value to emit if a matching error occurs
    * @return a new [[Mono]]
    */
  final def onErrorReturn(predicate: Throwable => Boolean, fallbackValue: T) = Mono[T](jMono.onErrorReturn(predicate, fallbackValue))

  //  TODO: How to test this?
  final def onTerminateDetach(): Mono[T] = {
    new Mono[T](jMono.onTerminateDetach())
  }

  final def publish[R](transform: Mono[T] => Mono[R]): Mono[R] = {
    val transformFunction: Function[JMono[T], JMono[R]] = new Function[JMono[T], JMono[R]] {
      override def apply(t: JMono[T]): JMono[R] = transform(Mono.this).jMono
    }
    new Mono[R](jMono.publish(transformFunction))
  }

  //TODO: How to test this?
  final def publishOn(scheduler: Scheduler): Mono[T] = {
    new Mono[T](jMono.publishOn(scheduler))
  }

  /**
    * Repeatedly subscribe to the source completion of the previous subscription.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeat.png" alt="">
    *
    * @return an indefinitively repeated [[Flux]] on onComplete
    */
  final def repeat() = Flux(jMono.repeat())

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
  final def repeat(predicate: () => Boolean) = Flux(jMono.repeat(predicate))

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
  final def repeat(numRepeat: Long) = Flux(jMono.repeat(numRepeat))

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
    *                                        predicate
    *
    */
  final def repeat(numRepeat: Long, predicate: () => Boolean) = Flux(jMono.repeat(numRepeat, predicate))

  private implicit def fluxLong2PublisherAnyToJFluxJLong2PublisherAny(mapper: (Flux[Long] => Publisher[_])): Function[JFlux[JLong], Publisher[_]] = {
    new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = mapper(t)
    }
  }

  /**
    * Repeatedly subscribe to this [[Mono]] when a companion sequence signals a number of emitted elements in
    * response to the flux completion signal.
    * <p>If the companion sequence signals when this [[Mono]] is active, the repeat
    * attempt is suppressed and any terminal signal will terminate this [[Flux]] with
    * the same signal immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/repeatwhen.png" alt="">
    *
    * @param whenFactory the [[Function1]] providing a [[Flux]] signalling an exclusive number of
    *                                emitted elements on onComplete and returning a [[Publisher]] companion.
    * @return an eventually repeated [[Flux]] on onComplete when the companion [[Publisher]] produces an
    *                                        onNext signal
    *
    */
  //  TODO: How to test this?
  final def repeatWhen(whenFactory: Flux[Long] => _ <: Publisher[_]) = Flux(jMono.repeatWhen(whenFactory))

  //  TODO: How to test this?
  final def repeatWhenEmpty(repeatFactory: Flux[Long] => Publisher[_]): Mono[T] = {
    new Mono[T](jMono.repeatWhenEmpty(repeatFactory))
  }

  //  TODO: How to test this?
  final def repeatWhenEmpty(maxRepeat: Int, repeatFactory: Flux[Long] => Publisher[_]): Mono[T] = {
    new Mono[T](jMono.repeatWhenEmpty(maxRepeat, repeatFactory))
  }

  //  TODO: How to test these retry(...)
  final def retry(): Mono[T] = {
    new Mono[T](jMono.retry())
  }

  final def retry(numRetries: Long): Mono[T] = {
    new Mono[T](jMono.retry(numRetries))
  }

  final def retry(retryMatcher: Throwable => Boolean): Mono[T] = {
    new Mono[T](jMono.retry(retryMatcher))
  }

  final def retry(numRetries: Long, retryMatcher: Throwable => Boolean): Mono[T] = {
    new Mono[T](jMono.retry(numRetries, retryMatcher))
  }

  final def retryWhen(whenFactory: Flux[Throwable] => Publisher[_]): Mono[T] = {
    new Mono[T](jMono.retryWhen(whenFactory))
  }

  final def subscribe(): MonoProcessor[T] = jMono.subscribe()

  final def subscribe(consumer: T => Unit): Disposable = {
    jMono.subscribe(consumer)
  }

  /**
    * Subscribe [[scala.Function1[T,Unit] Consumer]] to this [[Mono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[Mono.doOnSuccess]] and
    * [[Mono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/subscribeerror1.png" alt="">
    *
    * @param consumer      the consumer to invoke on each next signal
    * @param errorConsumer the consumer to invoke on error signal
    * @return a new [[Runnable]] to dispose the [[org.reactivestreams.Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit): Disposable = jMono.subscribe(consumer, errorConsumer)

  /**
    * Subscribe `consumer` to this [[Mono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[Mono.doOnSuccess]] and
    * [[Mono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribecomplete1.png" alt="">
    *
    * @param consumer         the consumer to invoke on each value
    * @param errorConsumer    the consumer to invoke on error signal
    * @param completeConsumer the consumer to invoke on complete signal
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit): Disposable = jMono.subscribe(consumer, errorConsumer, completeConsumer)

  /**
    * Subscribe [[Consumer]] to this [[Mono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[Mono.doOnSuccess]] and
    * [[Mono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribecomplete1.png" alt="">
    *
    * @param consumer             the consumer to invoke on each value
    * @param errorConsumer        the consumer to invoke on error signal
    * @param completeConsumer     the consumer to invoke on complete signal
    * @param subscriptionConsumer the consumer to invoke on subscribe signal, to be used
    *                             for the initial [[Subscription.request request]], or null for max request
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit, subscriptionConsumer: Subscription => Unit): Disposable = jMono.subscribe(consumer, errorConsumer, completeConsumer, subscriptionConsumer)

  /**
    * Run the requests to this Publisher [[Mono]] on a given worker assigned by the supplied [[Scheduler]].
    * <p>
    * `mono.subscribeOn(Schedulers.parallel()).subscribe())`
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribeon1.png" alt="">
    * <p>
    *
    * @param scheduler a checked [[reactor.core.scheduler.Scheduler.Worker]] factory
    * @return an asynchronously requesting [[Mono]]
    */
  //  TODO: How to test this?
  final def subscribeOn(scheduler: Scheduler): Mono[T] = Mono[T](jMono.subscribeOn(scheduler))

  /**
    * Subscribe the [[Mono]] with the givne [[Subscriber]] and return it.
    *
    * @param subscriber the [[Subscriber]] to subscribe
    * @param < E> the reified type of the { @link Subscriber} for chaining
    * @return the passed { @link Subscriber} after subscribing it to this { @link Mono}
    */
  //  TODO: How to test this?
  final def subscribeWith[E <: Subscriber[_ >: T]](subscriber: E): E = jMono.subscribeWith(subscriber)

  implicit def jMonoVoid2jMonoUnit(jMonoVoid: JMono[Void]): JMono[Unit] = jMonoVoid.map((_: Void) => ())

  /**
    * Return a `Mono[Unit]` which only replays complete and error signals
    * from this [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen.png" alt="">
    * <p>
    *
    * @return a [[Mono]] igoring its payload (actively dropping)
    */
  final def `then`(): Mono[Unit] = Mono[Unit](jMono.`then`())

  /**
    * Convert the value of [[Mono]] to another [[Mono]] possibly with another value type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/then.png" alt="">
    * <p>
    *
    * @param transformer the function to dynamically bind a new [[Mono]]
    * @tparam R the result type bound
    * @return a new [[Mono]] containing the merged values
    * @apiNote in 3.1.0.M1 this method will be renamed `flatMap`. However, until
    *          then the behavior of [[Mono.flatMap]] remains the current one, so it is
    *                                       not yet possible to anticipate this migration.
    */
  final def `then`[R](transformer: T => Mono[R]): Mono[R] = Mono[R](jMono.`then`[R](transformer: Function[T, JMono[_ <: R]]))

  /**
    * Ignore element from this [[Mono]] and transform its completion signal into the
    * emission and completion signal of a provided `Mono[V]`. Error signal is
    * replayed in the resulting `Mono[V]`.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen1.png" alt="">
    *
    * @param other a [[Mono]] to emit from after termination
    * @tparam V the element type of the supplied Mono
    * @return a new [[Mono]] that emits from the supplied [[Mono]]
    */
  final def `then`[V](other: Mono[V]): Mono[V] = Mono[V](jMono.`then`(other))

  /**
    * Return a `Mono[Unit]` that waits for this [[Mono]] to complete then
    * for a supplied [[Publisher Publisher[Unit]]] to also complete. The
    * second completion signal is replayed, or any error signal that occurs instead.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen.png"
    * alt="">
    *
    * @param other a [[Publisher]] to wait for after this Mono's termination
    * @return a new [[Mono]] completing when both publishers have completed in
    *                       sequence
    */
  final def thenEmpty(other: Publisher[Unit]) = Mono[Unit]((jMono: JMono[T]).thenEmpty(other))

  /**
    * Ignore element from this mono and transform the completion signal into a
    * `Flux[V]` that will emit elements from the provided [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/ignorethens.png" alt="">
    *
    * @param other a [[Publisher]] to emit from after termination
    * @tparam V the element type of the supplied Publisher
    * @return a new [[Flux]] that emits from the supplied [[Publisher]] after
    *                       this Mono completes.
    */
  final def thenMany[V](other: Publisher[V]): Flux[V] = Flux(jMono.thenMany(other))

  /**
    * Signal a [[java.util.concurrent.TimeoutException]] in case an item doesn't arrive before the given period.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeouttime1.png" alt="">
    *
    * @param timeout the timeout before the onNext signal from this [[Mono]]
    * @return an expirable [[Mono]]}
    */
  final def timeout(timeout: Duration) = Mono(jMono.timeout(timeout))

  /**
    * Switch to a fallback [[Mono]] in case an item doesn't arrive before the given period.
    *
    * <p> If the given [[Publisher]] is null, signal a [[java.util.concurrent.TimeoutException]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeouttimefallback1.png" alt="">
    *
    * @param timeout the timeout before the onNext signal from this [[Mono]]
    * @param fallback the fallback [[Mono]] to subscribe when a timeout occurs
    * @return an expirable [[Mono]] with a fallback [[Mono]]
    */
  final def timeout(timeout: Duration, fallback: Option[Mono[_ <: T]]) = Mono[T](jMono.timeout(timeout, fallback.orNull))

  /**
    * Signal a [[java.util.concurrent.TimeoutException]] error in case an item doesn't arrive before the given period.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeouttime1.png" alt="">
    *
    * @param timeout the timeout before the onNext signal from this [[Mono]]
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return an expirable [[Mono]]
    */
  final def timeout(timeout: Duration, timer: Scheduler): Mono[T] = Mono[T](jMono.timeout(timeout, timer))

  /**
    * Switch to a fallback [[Mono]] in case an item doesn't arrive before the given period.
    *
    * <p> If the given `Publisher` is [[None]], signal a [[java.util.concurrent.TimeoutException]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeouttimefallback1.png" alt="">
    *
    * @param timeout the timeout before the onNext signal from this [[Mono]]
    * @param fallback the fallback [[Mono]] to subscribe when a timeout occurs
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return an expirable [[Mono]] with a fallback [[Mono]]
    */
  final def timeout(timeout: Duration, fallback: Option[Mono[_ <: T]], timer: Scheduler): Mono[T] = Mono[T](jMono.timeout(timeout, fallback.orNull[Mono[_ <: T]], timer))


  /**
    * Signal a [[java.util.concurrent.TimeoutException]] in case the item from this [[Mono]] has
    * not been emitted before the given [[Publisher]] emits.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeoutp1.png" alt="">
    *
    * @param firstTimeout the timeout [[Publisher]] that must not emit before the first signal from this [[Mono]]
    * @tparam U the element type of the timeout Publisher
    * @return an expirable [[Mono]] if the first item does not come before a [[Publisher]] signal
    *
    */
  final def timeout[U](firstTimeout: Publisher[U]) = Mono[T](jMono.timeout(firstTimeout))

  /**
    * Switch to a fallback [[Publisher]] in case the  item from this [[Mono]] has
    * not been emitted before the given [[Publisher]] emits. The following items will be individually timed via
    * the factory provided [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeoutfallbackp1.png" alt="">
    *
    * @param firstTimeout the timeout
    *                     [[Publisher]] that must not emit before the first signal from this [[Mono]]
    * @param fallback the fallback [[Publisher]] to subscribe when a timeout occurs
    * @tparam U the element type of the timeout Publisher
    * @return a first then per-item expirable [[Mono]] with a fallback [[Publisher]]
    *
    */
  final def timeout[U](firstTimeout: Publisher[U], fallback: Mono[_ <: T]) = Mono[T](jMono.timeout(firstTimeout, fallback))

  /**
    * Emit a [[Tuple2]] pair of T1 [[Long]] current system time in
    * millis and T2 `T` associated data for the eventual item from this [[Mono]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timestamp1.png" alt="">
    *
    * @return a timestamped [[Mono]]
    */
  //  TODO: How to test timestamp(...) with the actual timestamp?
  final def timestamp() = new Mono[(Long, T)](jMono.timestamp().map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))

  /**
    * Emit a [[Tuple2]] pair of T1 [[Long]] current system time in
    * millis and T2 `T` associated data for the eventual item from this [[Mono]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timestamp1.png" alt="">
    *
    * @param scheduler a [[Scheduler]] instance to read time from
    * @return a timestamped [[Mono]]
    */
  final def timestamp(scheduler: Scheduler): Mono[(Long, T)] = Mono[(Long, T)](jMono.timestamp(scheduler).map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))

  /**
    * Transform this [[Mono]] into a [[Future]] completing on onNext or onComplete and failing on
    * onError.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/tofuture.png" alt="">
    * <p>
    *
    * @return a [[Future]]
    */
  final def toFuture: Future[T] = {
    val promise = Promise[T]()
    jMono.toFuture.handle[Unit]((value: T, throwable: Throwable) => {
      Option(value).foreach(v => promise.complete(Try(v)))
      Option(throwable).foreach(t => promise.failure(t))
      ()
    })
    promise.future
  }

  /**
    * Transform this [[Mono]] in order to generate a target [[Mono]]. Unlike [[Mono.compose]], the
    * provided function is executed as part of assembly.
    *
    * @example {{{
    *    val applySchedulers = mono => mono.subscribeOn(Schedulers.elastic()).publishOn(Schedulers.parallel());
    *    mono.transform(applySchedulers).map(v => v * v).subscribe()
    *          }}}
    * @param transformer the [[Function1]] to immediately map this [[Mono]] into a target [[Mono]]
    *                                instance.
    * @tparam V the item type in the returned [[Mono]]
    * @return a new [[Mono]]
    * @see [[Mono.compose]] for deferred composition of [[Mono]] for each [[Subscriber]]
    * @see [[Mono.as]] for a loose conversion to an arbitrary type
    */
  final def transform[V](transformer: Mono[T] => Publisher[V]): Mono[V] = Mono[V](jMono.transform[V]((_: JMono[T]) => transformer(Mono.this)))

  /**
    * Subscribe to this Mono and another Publisher, which will be used as a trigger for
    * the emission of this Mono's element. That is to say, this Mono's element is delayed
    * until the trigger Publisher emits for the first time (or terminates empty).
    *
    * @param anyPublisher the publisher which first emission or termination will trigger
    *                     the emission of this Mono's value.
    * @return this Mono, but delayed until the given publisher emits first or terminates.
    */
  def untilOther(anyPublisher: Publisher[_]) = Mono(jMono.untilOther(anyPublisher))

  /**
    * Subscribe to this Mono and another Publisher, which will be used as a trigger for
    * the emission of this Mono's element, mapped through a provided function.
    * That is to say, this Mono's element is delayed until the trigger Publisher emits
    * for the first time (or terminates empty). Any error is delayed until all publishers
    * have triggered, and multiple errors are combined into one.
    *
    * @param anyPublisher the publisher which first emission or termination will trigger
    *                     the emission of this Mono's value.
    * @return this Mono, but delayed until the given publisher emits first or terminates.
    */
  def untilOtherDelayError(anyPublisher: Publisher[_]) = Mono(jMono.untilOtherDelayError(anyPublisher))

  final def asJava(): JMono[T] = jMono
}

object Mono {

  /**
    * This function is used as bridge to create scala-wrapper of Mono based on existing Java Mono
    *
    * @param javaMono The underlying Java Mono
    * @tparam T The value type that will be emitted by this mono
    * @return Wrapper of Java Mono
    */
  private[publisher] def apply[T](javaMono: JMono[T]) = new Mono[T](javaMono)

  def create[T](callback: MonoSink[T] => Unit): Mono[T] = {
    new Mono[T](
      JMono.create(new Consumer[MonoSink[T]] {
        override def accept(t: MonoSink[T]): Unit = callback(t)
      })
    )
  }

  def defer[T](supplier: () => Mono[T]): Mono[T] = {
    new Mono[T](
      JMono.defer(new Supplier[JMono[T]] {
        override def get(): JMono[T] = supplier().jMono
      })
    )
  }

  /**
    * Create a Mono which delays an onNext signal of `duration` of given unit and complete on the global timer.
    * If the demand cannot be produced in time, an onError will be signalled instead.
    * The delay is introduced through the [[reactor.core.scheduler.Schedulers.parallel parallel]] default Scheduler.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/delay.png" alt="">
    * <p>
    *
    * @param duration the duration of the delay
    * @return a new [[Mono]]
    */
  def delay(duration: Duration): Mono[Long] = Mono(JMono.delay(duration)).map(Long2long)

  /**
    * Create a Mono which delays an onNext signal by a given `duration and completes.
    * If the demand cannot be produced in time, an onError will be signalled instead.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/delay.png" alt="">
    * <p>
    *
    * @param duration the [[Duration]] of the delay
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return a new [[Mono]]
    */
  def delay(duration: Duration, timer: Scheduler): Mono[Long] = Mono(JMono.delay(duration, timer)).map(Long2long)

  /**
    * Create a [[Mono]] that completes without emitting any item.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/empty.png" alt="">
    * <p>
    *
    * @tparam T the reified [[Subscriber]] type
    * @return a completed [[Mono]]
    */
  def empty[T] = Mono[T](JMono.empty())

  /**
    * Create a new [[Mono]] that ignores onNext (dropping them) and only react on Completion signal.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/thens.png" alt="">
    * <p>
    *
    * @param source the [[Publisher to ignore]]
    * @tparam T the reified [[Publisher]] type
    * @return a new completable [[Mono]].
    */
  def empty[T](source: Publisher[T]) = Mono[Unit](JMono.empty(source).map(new Function[Void, Unit] {override def apply(t: Void): Unit = ()}))

  /**
    * Create a [[Mono]] that completes with the specified error immediately after onSubscribe.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/error.png" alt="">
    * <p>
    *
    * @param error the onError signal
    * @tparam T the reified [[Subscriber]] type
    * @return a failed [[Mono]]
    */
  def error[T](error: Throwable) = Mono[T](JMono.error(error))

  /**
    * Expose the specified [[Publisher]] with the [[Mono]] API, and ensure it will emit 0 or 1 item.
    * The source emitter will be cancelled on the first `onNext`.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/from1.png" alt="">
    * <p>
    *
    * @param source the { @link Publisher} source
    * @tparam T the source type
    * @return the next item emitted as a { @link Mono}
    */
  def from[T](source: Publisher[_ <: T]) = Mono[T](JMono.from(source))

  /**
    * Create a [[Mono]] producing the value for the [[Mono]] using the given supplier.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/fromcallable.png" alt="">
    * <p>
    *
    * @param supplier { @link Callable} that will produce the value
    * @tparam T type of the expected value
    * @return A [[Mono]].
    */
  def fromCallable[T](supplier: Callable[T]) = Mono[T](JMono.fromCallable(supplier))

  /**
    * Unchecked cardinality conversion of [[Publisher]] as [[Mono]], supporting
    * [[reactor.core.Fuseable]] sources.
    *
    * @param source the [[Publisher]] to wrap
    * @tparam I input upstream type
    * @return a wrapped [[Mono]]
    */
  def fromDirect[I](source: Publisher[_ <: I]) = Mono(JMono.fromDirect[I](source))

  /**
    * Create a [[Mono]] producing the value for the [[Mono]] using the given [[Future]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/fromfuture.png" alt="">
    * <p>
    *
    * @param future [[Future]] that will produce the value or null to
    *                       complete immediately
    * @param executionContext an implicit [[ExecutionContext]] to use
    * @tparam T type of the expected value
    * @return A [[Mono]].
    */
  def fromFuture[T](future: Future[T])(implicit executionContext: ExecutionContext): Mono[T] = {
    val completableFuture = new CompletableFuture[T]()
    future onComplete {
      case Success(t) => completableFuture.complete(t)
      case Failure(error) => completableFuture.completeExceptionally(error)
    }
    Mono[T](JMono.fromFuture(completableFuture))
  }

  /**
    * Create a [[Mono]] only producing a completion signal after using the given
    * runnable.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/fromrunnable.png" alt="">
    * <p>
    *
    * @param runnable [[Runnable]] that will callback the completion signal
    * @return A [[Mono]].
    */
  def fromRunnable(runnable: Runnable) = new Mono[Unit](
    JMono.fromRunnable(runnable).map(new Function[Void, Unit] {
      override def apply(t: Void): Unit = ()
    })
  )

  /**
    * Create a [[Mono]] producing the value for the [[Mono]] using the given supplier.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/fromsupplier.png" alt="">
    * <p>
    *
    * @param supplier  that will produce the value
    * @tparam T type of the expected value
    * @return A [[Mono]].
    */
  def fromSupplier[T](supplier: () => T) = new Mono[T](
    JMono.fromSupplier(new Supplier[T] {
      override def get(): T = supplier()
    })
  )

  /**
    * Create a new [[Mono]] that ignores onNext (dropping them) and only react on Completion signal.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignoreelements.png" alt="">
    * <p>
    *
    * @param source the [[Publisher to ignore]]
    * @tparam T the source type of the ignored data
    * @return a new completable [[Mono]].
    */
  def ignoreElements[T](source: Publisher[T]) = Mono[T](
    JMono.ignoreElements(source)
  )

  /**
    * Create a new [[Mono]] that emits the specified item.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/just.png" alt="">
    * <p>
    *
    * @param data the only item to onNext
    * @tparam T the type of the produced item
    * @return a [[Mono]].
    */
  def just[T](data: T) = Mono[T](JMono.just(data))

  /**
    * Create a new [[Mono]] that emits the specified item if [[Option.isDefined]] otherwise only emits
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/justorempty.png" alt="">
    * <p>
    *
    * @param data the [[Option]] item to onNext or onComplete if not present
    * @tparam T the type of the produced item
    * @return a [[Mono]].
    */
  def justOrEmpty[T](data: Option[_ <: T]) = Mono[T](
    JMono.justOrEmpty[T](data)
  )

  /**
    * Create a new [[Mono]] that emits the specified item if non null otherwise only emits
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/justorempty.png" alt="">
    * <p>
    *
    * @param data the item to onNext or onComplete if null
    * @tparam T the type of the produced item
    * @return a [[Mono]].
    */
  def justOrEmpty[T](data: T) = Mono[T](
    JMono.justOrEmpty(data)
  )

  /**
    * Return a [[Mono]] that will never signal any data, error or completion signal.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/never.png" alt="">
    * <p>
    *
    * @tparam T the [[Subscriber]] type target
    * @return a never completing [[Mono]]
    */
  def never[T] = new Mono[T](
    JMono.never[T]()
  )

  /**
    * Returns a Mono that emits a Boolean value that indicates whether two Publisher sequences are the
    * same by comparing the items emitted by each Publisher pairwise.
    *
    * @param source1
    *          the first Publisher to compare
    * @param source2
    *          the second Publisher to compare
    * @tparam T
    *          the type of items emitted by each Publisher
    * @return a Mono that emits a Boolean value that indicates whether the two sequences are the same
    */
  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T]): Mono[Boolean] = Mono[Boolean](
    JMono.sequenceEqual[T](source1, source2).map(new Function[JBoolean, Boolean] {
      override def apply(t: JBoolean) = Boolean2boolean(t)
    })
  )

  /**
    * Returns a Mono that emits a Boolean value that indicates whether two Publisher sequences are the
    * same by comparing the items emitted by each Publisher pairwise based on the results of a specified
    * equality function.
    *
    * @param source1
    *          the first Publisher to compare
    * @param source2
    *          the second Publisher to compare
    * @param isEqual
    *            a function used to compare items emitted by each Publisher
    * @tparam T
    *          the type of items emitted by each Publisher
    * @return a Mono that emits a Boolean value that indicates whether the two sequences are the same
    */
  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T], isEqual: (T, T) => Boolean): Mono[Boolean] = {
    Mono(JMono.sequenceEqual[T](source1, source2, new BiPredicate[T, T] {
      override def test(t: T, u: T): Boolean = isEqual(t, u)
    })).map(Boolean2boolean)
  }

  /**
    * Returns a Mono that emits a Boolean value that indicates whether two Publisher sequences are the
    * same by comparing the items emitted by each Publisher pairwise based on the results of a specified
    * equality function.
    *
    * @param source1
    *          the first Publisher to compare
    * @param source2
    *          the second Publisher to compare
    * @param isEqual
    *          a function used to compare items emitted by each Publisher
    * @param bufferSize
    *          the number of items to prefetch from the first and second source Publisher
    * @tparam T
    *          the type of items emitted by each Publisher
    * @return a Mono that emits a Boolean value that indicates whether the two Publisher two sequences
    *         are the same according to the specified function
    */
  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T], isEqual: (T, T) => Boolean, bufferSize: Int): Mono[Boolean] = Mono(JMono.sequenceEqual[T](source1, source2, new BiPredicate[T, T] {
    override def test(t: T, u: T): Boolean = isEqual(t, u)
  }, bufferSize)).map(Boolean2boolean)

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the value from a
    * Mono derived from the same resource and makes sure the resource is released if the
    * sequence terminates or
    * the Subscriber cancels.
    * <p>
    * <ul> <li>Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup
    * Consumer may override the terminal even.</li> <li>Non-eager cleanup will drop any exception.</li> </ul>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a function that is called on subscribe for preparing the resource
    * @param sourceSupplier a [[Mono]] factory derived from the supplied resource
    * @param resourceCleanup invoked on completion
    * @param eager           true to clean before terminating downstream subscribers
    * @tparam T emitted type
    * @tparam D resource type
    * @return new [[Mono]]
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => _ <: Mono[_ <: T], resourceCleanup: D => Unit, eager: Boolean) =
    Mono(JMono.using[T, D](resourceSupplier, new Function[D, JMono[_ <: T]] {
      override def apply(t: D): JMono[_ <: T] = sourceSupplier(t).asJava()
    }, resourceCleanup, eager))

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the value from a
    * Mono derived from the same resource and makes sure the resource is released if the
    * sequence terminates or
    * the Subscriber cancels.
    * <p>
    * Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup Consumer
    * may override the terminal even.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a function that is called on subscribe to prepare the resource
    * @param sourceSupplier a [[Mono]] factory derived from the supplied resource
    * @param resourceCleanup invoked on completion
    * @tparam T emitted type
    * @tparam D resource type
    * @return new [[Mono]]
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Mono[_ <: T], resourceCleanup: D => Unit) =
    Mono(JMono.using[T, D](resourceSupplier,  new Function[D, JMono[_ <: T]] {
      override def apply(t: D): JMono[_ <: T] = sourceSupplier(t).asJava()
    }, resourceCleanup))

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p2 The second upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @return a [[Mono]].
    */
  def when[T1, T2](p1: Mono[_ <: T1], p2: Mono[_ <: T2]): Mono[(T1, T2)] = {
    val jMono: JMono[Tuple2[T1, T2]] = JMono.when(p1.jMono, p2.jMono)

    new Mono[(T1, T2)](
      jMono.map(new Function[Tuple2[T1, T2], (T1, T2)] {
        override def apply(t: Tuple2[T1, T2]): (T1, T2) = (t.getT1, t.getT2)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1         The first upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p2         The second upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param combinator a [[scala.Function2]] combinator function when both sources
    *                   complete
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam O  output value
    * @return a [[Mono]].
    */
  def when[T1, T2, O](p1: Mono[_ <: T1], p2: Mono[_ <: T2], combinator: (T1, T2) => O): Mono[O] = {
    val jMono: JMono[O] = JMono.when(p1.jMono, p2.jMono, new BiFunction[T1, T2, O] {
      override def apply(t: T1, u: T2): O = combinator(t, u)
    })
    new Mono[O](jMono)
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p2 The second upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p3 The third upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @return a { @link Mono}.
    */
  def when[T1, T2, T3](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3]): Mono[(T1, T2, T3)] = {
    val jMono: JMono[Tuple3[T1, T2, T3]] = JMono.when(p1.jMono, p2.jMono, p3.jMono)
    new Mono[(T1, T2, T3)](
      jMono.map(new Function[Tuple3[T1, T2, T3], (T1, T2, T3)] {
        override def apply(t: Tuple3[T1, T2, T3]): (T1, T2, T3) = (t.getT1, t.getT2, t.getT3)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p2 The second upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p3 The third upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p4 The fourth upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @return a { @link Mono}.
    */
  def when[T1, T2, T3, T4](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4]): Mono[(T1, T2, T3, T4)] = {
    val jMono: JMono[Tuple4[T1, T2, T3, T4]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono)
    new Mono[(T1, T2, T3, T4)](
      jMono.map(new Function[Tuple4[T1, T2, T3, T4], (T1, T2, T3, T4)] {
        override def apply(t: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = (t.getT1, t.getT2, t.getT3, t.getT4)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p2 The second upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p3 The third upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p4 The fourth upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p5 The fifth upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @return a [[Mono]].
    */
  def when[T1, T2, T3, T4, T5](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5]): Mono[(T1, T2, T3, T4, T5)] = {
    val jMono: JMono[Tuple5[T1, T2, T3, T4, T5]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono)
    new Mono[(T1, T2, T3, T4, T5)](
      jMono.map(new Function[Tuple5[T1, T2, T3, T4, T5], (T1, T2, T3, T4, T5)] {
        override def apply(t: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = (t.getT1, t.getT2, t.getT3, t.getT4, t.getT5)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p2 The second upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p3 The third upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p4 The fourth upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p5 The fifth upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @param p6 The sixth upstream [[org.reactivestreams.Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam T6 type of the value from source6
    * @return a [[Mono]].
    */
  def when[T1, T2, T3, T4, T5, T6](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5], p6: Mono[_ <: T6]): Mono[(T1, T2, T3, T4, T5, T6)] = {
    val jMono: JMono[Tuple6[T1, T2, T3, T4, T5, T6]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono, p6.jMono)
    new Mono[(T1, T2, T3, T4, T5, T6)](
      jMono.map(new Function[Tuple6[T1, T2, T3, T4, T5, T6], (T1, T2, T3, T4, T5, T6)] {
        override def apply(t: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = (t.getT1, t.getT2, t.getT3, t.getT4, t.getT5, t.getT6)
      })
    )
  }

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
    * @return a [[Mono]].
    */
  def when(sources: Iterable[_ <: Publisher[Unit] with MapablePublisher[Unit]]): Mono[Unit] = {
    new Mono[Unit](
      JMono.when(sources.map(s => s.map((t: Unit) => None.orNull: Void)).asJava).map((t: Void) => ())
    )
  }

  /**
    * Aggregate given monos into a new a `Mono` that will be fulfilled when all of the given `Monos` have been fulfilled.
    * If any Mono terminates without value, the returned sequence will be terminated immediately and pending results cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param monos      The monos to use.
    * @param combinator the function to transform the combined array into an arbitrary
    *                   object.
    * @tparam           R the combined result
    * @return a [[Mono]].
    */
  def when[R](monos: Iterable[_ <: Mono[Any]], combinator: (Array[Any] => R)): Mono[R] = {
    val combinatorFunction: Function[_ >: Array[Object], _ <: R] = new Function[Array[Object], R] {
      override def apply(t: Array[Object]): R = {
        val v: Array[Any] = t.map { v => v: Any }
        combinator(v)
      }
    }
    val jMonos = monos.map(_.jMono.map(new Function[Any, Object] {
      override def apply(t: Any): Object = t.asInstanceOf[Object]
    })).asJava

    new Mono[R](
      JMono.when(jMonos, combinatorFunction)
    )
  }

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
    * @return a [[Mono]].
    */
  def when(sources: (Publisher[Unit] with MapablePublisher[Unit])*): Mono[Unit] = {
    new Mono[Unit](
      JMono.when(sources.map(s => s.map((T: Unit) => None.orNull: Void)).asJava).map((t: Void) => ())
    )
  }

  /**
    * Aggregate given monos into a new a `Mono` that will be fulfilled when all of the given `Monos` have been fulfilled.
    * An error will cause pending results to be cancelled and immediate error emission to the
    * returned [[Mono]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param monos      The monos to use.
    * @param combinator the function to transform the combined array into an arbitrary
    *                   object.
    * @tparam           R the combined result
    * @return a [[Mono]].
    */
  def when[R](combinator: (Array[Any] => R), monos: Mono[Any]*): Mono[R] = {
    val combinatorFunction: Function[_ >: Array[Object], _ <: R] = new Function[Array[Object], R] {
      override def apply(t: Array[Object]): R = {
        val v: Array[Any] = t.map { v => v: Any }
        combinator(v)
      }
    }
    val jMonos = monos.map(_.jMono.map(new Function[Any, Object] {
      override def apply(t: Any): Object = t.asInstanceOf[Object]
    }))

    new Mono[R](
      JMono.when(combinatorFunction, jMonos.toArray: _*)
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[Publisher]] to subscribe to.
    * @param p2 The second upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @return a [[Mono]].
    */
  def whenDelayError[T1, T2](p1: Mono[_ <: T1], p2: Mono[_ <: T2]): Mono[(T1, T2)] = {
    val jMono = JMono.whenDelayError[T1, T2](p1.jMono, p2.jMono)
    new Mono[(T1, T2)](
      jMono.map(new Function[Tuple2[T1, T2], (T1, T2)] {
        override def apply(t: Tuple2[T1, T2]): (T1, T2) = tupleTwo2ScalaTuple2(t)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[Publisher]] to subscribe to.
    * @param p2 The second upstream [[Publisher]] to subscribe to.
    * @param p3 The third upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @return a [[Mono]].
    */
  def whenDelayError[T1, T2, T3](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3]): Mono[(T1, T2, T3)] = {
    new Mono[(T1, T2, T3)](
      JMono.whenDelayError[T1, T2, T3](p1.jMono, p2.jMono, p3.jMono).map(new Function[Tuple3[T1, T2, T3], (T1, T2, T3)] {
        override def apply(t: Tuple3[T1, T2, T3]): (T1, T2, T3) = tupleThree2ScalaTuple3(t)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[Publisher]] to subscribe to.
    * @param p2 The second upstream [[Publisher]] to subscribe to.
    * @param p3 The third upstream [[Publisher]] to subscribe to.
    * @param p4 The fourth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @return a [[Mono]].
    */
  def whenDelayError[T1, T2, T3, T4](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4]): Mono[(T1, T2, T3, T4)] = {
    new Mono[(T1, T2, T3, T4)](
      JMono.whenDelayError[T1, T2, T3, T4](p1.jMono, p2.jMono, p3.jMono, p4.jMono).map(new Function[Tuple4[T1, T2, T3, T4], (T1, T2, T3, T4)] {
        override def apply(t: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = tupleFour2ScalaTuple4(t)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[Publisher]] to subscribe to.
    * @param p2 The second upstream [[Publisher]] to subscribe to.
    * @param p3 The third upstream [[Publisher]] to subscribe to.
    * @param p4 The fourth upstream [[Publisher]] to subscribe to.
    * @param p5 The fifth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @return a [[Mono]].
    */
  def whenDelayError[T1, T2, T3, T4, T5](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5]): Mono[(T1, T2, T3, T4, T5)] = {
    new Mono[(T1, T2, T3, T4, T5)](
      JMono.whenDelayError[T1, T2, T3, T4, T5](p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono).map(new Function[Tuple5[T1, T2, T3, T4, T5], (T1, T2, T3, T4, T5)] {
        override def apply(t: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = tupleFive2ScalaTuple5(t)
      })
    )
  }

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param p1 The first upstream [[Publisher]] to subscribe to.
    * @param p2 The second upstream [[Publisher]] to subscribe to.
    * @param p3 The third upstream [[Publisher]] to subscribe to.
    * @param p4 The fourth upstream [[Publisher]] to subscribe to.
    * @param p5 The fifth upstream [[Publisher]] to subscribe to.
    * @param p6 The sixth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam T6 type of the value from source6
    * @return a [[Mono]].
    */
  def whenDelayError[T1, T2, T3, T4, T5, T6](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5], p6: Mono[_ <: T6]) = Mono[(T1, T2, T3, T4, T5, T6)](
    JMono.whenDelayError[T1, T2, T3, T4, T5, T6](p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono, p6.jMono).map(new Function[Tuple6[T1, T2, T3, T4, T5, T6], (T1, T2, T3, T4, T5, T6)] {
      override def apply(t: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = tupleSix2ScalaTuple6(t)
    })
  )

  /**
    * Aggregate given void publishers into a new a `Mono` that will be
    * fulfilled when all of the given `sources` have been fulfilled. If any Publisher
    * terminates without value, the returned sequence will be terminated immediately and
    * pending results cancelled. If several Publishers error, the exceptions are combined
    * (suppressed into a combining exception).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[Mono]].
    */
  def whenDelayError(sources: Iterable[_ <: Publisher[Unit] with MapablePublisher[Unit]]) = Mono[Unit](
    JMono.whenDelayError(sources.map(s => s.map((t: Unit) => None.orNull: Void)).asJava).map((t: Void) => ())
  )

  /**
    * Aggregate given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled. If any Mono terminates without value, the returned sequence will be terminated
    * immediately and pending results cancelled. If several Monos error, the exceptions are combined (suppressed
    * into a combining exception).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/whent.png" alt="">
    * <p>
    *
    * @param monos      The monos to use.
    * @param combinator the function to transform the combined array into an arbitrary
    *                   object.
    * @tparam R the combined result
    * @return a [[Mono]].
    */
  def whenDelayError[R](monos: Iterable[_ <: Mono[_]], combinator: (Array[Any] => _ <: R)) ={
    val combinatorFunction = new Function[Array[Object], R] {
      override def apply(t: Array[Object]): R = {
        val v = t.map { v => v: Any }
        combinator(v)
      }
    }
    val jMonos: JIterable[JMono[_]] = monos.map(_.asJava()).asJava
    Mono(JMono.whenDelayError[R](jMonos, combinatorFunction))
  }

  /**
    * Merge given void publishers into a new a `Mono` that will be fulfilled
    * when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[Mono]].
    */
  def whenDelayError(sources: (Publisher[Unit] with MapablePublisher[Unit])*): Mono[Unit] = Mono[Unit](
    JMono.whenDelayError(sources.map(s => s.map((t: Unit) => None.orNull: Void)).toArray: _*)
      .map((t: Void) => ())
  )

  /**
    * Merge given monos into a new a `Mono` that will be fulfilled when all of the given `Monos`
    * have been fulfilled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param monos      The monos to use.
    * @param combinator the function to transform the combined array into an arbitrary
    *                   object.
    * @tparam R the combined result
    * @return a combined [[Mono]].
    */
  def whenDelayError[R](combinator: (Array[Any] => R), monos: Mono[Any]*): Mono[R] = {
    val combinatorFunction = new Function[Array[Object], R] {
      override def apply(t: Array[Object]): R = {
        val v = t.map { v => v: Any }
        combinator(v)
      }
    }
    val jMonos = monos.map(_.jMono.map(new Function[Any, Object] {
      override def apply(t: Any): Object = t.asInstanceOf[Object]
    }))

    Mono[R](JMono.whenDelayError(combinatorFunction, jMonos.toArray: _*))
  }

  /**
    * Aggregate given monos into a new a `Mono` that will be fulfilled when all of the given `Monos` have been fulfilled.
    * If any Mono terminates without value, the returned sequence will be terminated immediately and pending results cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/zip1.png" alt="">
    * <p>
    *
    * @param combinator the combinator [[scala.Function]]
    * @param monos      The monos to use.
    * @tparam T The super incoming type
    * @tparam V The type of the function result.
    * @return a [[Mono]].
    */
  def zip[T, V](combinator: (Array[AnyRef] => V), monos: Mono[_ <: T]*): Mono[V] = {
    val jMonos = monos.map(_.jMono)
    new Mono[V](
      JMono.zip(combinator, jMonos.toArray: _*)
    )
  }

  /**
    * Aggregate given monos into a new a `Mono` that will be fulfilled when all of the given `Monos` have been fulfilled.
    * If any Mono terminates without value, the returned sequence will be terminated immediately and pending results cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/zip1.png" alt="">
    * <p>
    *
    * @param combinator the combinator [[scala.Function]]
    * @param monos      The monos to use.
    * @tparam T The type of the function result.
    * @tparam V The result type
    * @return a [[Mono]].
    */
  def zip[T, V](combinator: (Array[AnyRef] => V), monos: Iterable[Mono[_ <: T]]): Mono[V] = {
    val combinatorFunction = new Function[Array[Object], V] {
      override def apply(t: Array[Object]): V = {
        //the reason we do the following is because the underlying reactor is by default allocating 8 elements with null, so we need to get rid of null
        val v = t.map { v => Option(v): Option[AnyRef] }.filterNot(_.isEmpty).map(_.getOrElse(None.orNull))
        combinator(v)
      }
    }
    val jMonos = monos.map(_.jMono).asJava.asInstanceOf[JIterable[JMono[T]]]
    new Mono[V](
      JMono.zip(combinatorFunction, jMonos)
    )
  }
}
