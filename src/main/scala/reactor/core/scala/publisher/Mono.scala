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
import java.util.function.{BiConsumer, BiFunction, Consumer, Function, Predicate, Supplier}
import java.util.logging.Level

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.Disposable
import reactor.core.publisher.{MonoProcessor, MonoSink, Signal, SignalType, SynchronousSink, Flux => JFlux, Mono => JMono}
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, TimedScheduler}
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
class Mono[T] private (private val jMono: JMono[T]) extends Publisher[T] {
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
    val combinedMono: JMono[Tuple2[T, T2]] = jMono.and(other.jMono)
    new Mono[(T, T2)](
      combinedMono
        .map(new Function[Tuple2[T, T2], (T, T2)] {
          override def apply(t: Tuple2[T, T2]): (T, T2) = tupleTwo2ScalaTuple2(t)
        })
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
    * @param other the [[Mono]] to combine with
    * @param combinator a [[scala.Function2]] combinator function when both sources
    *                             complete
    * @tparam T2 the element type of the other Mono instance
    * @tparam O the element type of the combination
    * @return a new combined Mono
    * @see [[Mono.when]]
    */
  final def and[T2, O](other: Mono[T2], combinator: (T, T2) => O): Mono[O] = {
    val combinatorFunction: BiFunction[T, T2, O] = new BiFunction[T, T2, O] {
      override def apply(t: T, u: T2): O = combinator(t, u)
    }
    val combinedMono: JMono[O] = jMono.and(other.jMono, combinatorFunction)
    new Mono[O](combinedMono)
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
    val rightGeneratorFunction: Function[T, JMono[_ <: T2]] = new Function[T, JMono[_ <: T2]]() {
      def apply(i: T): JMono[_ <: T2] = rightGenerator(i).jMono
    }

    new Mono[(T, T2)](
      jMono.and[T2](rightGeneratorFunction).map(new Function[Tuple2[T, T2], (T, T2)] {
        override def apply(t: Tuple2[T, T2]): (T, T2) = tupleTwo2ScalaTuple2(t)
      })
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
    * @param combinator a [[scala.Function2]] combinator function when both sources complete
    * @tparam T2 the element type of the other Mono instance
    * @tparam O the element type of the combination
    * @return a new combined Mono
    */

  final def and[T2, O](rightGenerator: (T => Mono[T2]), combinator: (T, T2) => O): Mono[O] = {
    val rightGeneratorFunction: Function[T, JMono[_ <: T2]] = new Function[T, JMono[_ <: T2]]() {
      def apply(i: T): JMono[_ <: T2] = rightGenerator(i).jMono
    }
    val combinatorFunction: BiFunction[T, T2, O] = new BiFunction[T, T2, O] {
      override def apply(t: T, u: T2): O = combinator(t, u)
    }
    new Mono[O](
      jMono.and[T2, O](rightGeneratorFunction, combinatorFunction)
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
    * Block until a next signal is received, will return null if onComplete, T if onNext, throw a
    * `Exceptions.DownstreamException` if checked error or origin RuntimeException if unchecked.
    * If the default timeout `30 seconds` has elapsed, a [[RuntimeException]]  will be thrown.
    *
    * Note that each block() will subscribe a new single (MonoSink) subscriber, in other words, the result might
    * miss signal from hot publishers.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/block.png" alt="">
    * <p>
    *
    * @param timeout maximum time period to wait for in milliseconds before raising a [[RuntimeException]]
    * @return T the result
    */
  final def blockMillis(timeout: Long): T = jMono.blockMillis(timeout)

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
  final def cache(): Mono[T] = new Mono[T](
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
  final def cancelOn(scheduler: Scheduler): Mono[T] = new Mono[T](
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
    new Mono[V](
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
  final def concatWith(other: Publisher[T]): Flux[T] = new Flux[T](
    jMono.concatWith(other)
  )

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

  final def delaySubscription(delay: Duration): Mono[T] = {
    new Mono[T](
      jMono.delaySubscription(delay)
    )
  }

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): Mono[T] = {
    new Mono[T](
      jMono.delaySubscription(subscriptionDelay)
    )
  }

  final def delaySubscriptionMillis(delay: Long): Mono[T] = {
    new Mono[T](
      jMono.delaySubscriptionMillis(delay)
    )
  }

  final def delaySubscriptionMillis(delay: Long, timedScheduler: TimedScheduler): Mono[T] = {
    new Mono[T](
      jMono.delaySubscriptionMillis(delay, timedScheduler)
    )
  }

  final def dematerialize[X](): Mono[X] = {
    new Mono[X](
      jMono.dematerialize[X]()
    )
  }

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

  final def doOnSuccess(onSuccess: (T => Unit)): Mono[T] = {
    val onSuccessFunction = new Consumer[T] {
      override def accept(t: T): Unit = onSuccess(t)
    }
    new Mono[T](
      jMono.doOnSuccess(onSuccessFunction)
    )
  }

  final def doOnError(onError: (Throwable => Unit)): Mono[T] = {
    new Mono[T](
      jMono.doOnError(onError)
    )
  }

  final def doOnError[E <: Throwable](exceptionType: Class[E], onError: (E => Unit)): Mono[T] = {
    new Mono[T](
      jMono.doOnError(exceptionType, onError: Consumer[E])
    )
  }

  final def doOnError(predicate: (Throwable => Boolean), onError: (Throwable => Unit)): Mono[T] = {
    new Mono[T](
      jMono.doOnError(predicate: Predicate[Throwable], onError: Consumer[Throwable])
    )
  }

  final def doOnRequest(onRequest: Long => Unit): Mono[T] = {
    new Mono[T](
      jMono.doOnRequest(onRequest)
    )
  }

  final def doOnSubscribe(onSubscribe: Subscription => Unit): Mono[T] = {
    new Mono[T](
      jMono.doOnSubscribe(onSubscribe)
    )
  }

  final def doOnTerminate(onTerminate: (T, Throwable) => Unit): Mono[T] = {
    Mono(jMono.doOnTerminate(new BiConsumer[T, Throwable] {
      override def accept(t: T, u: Throwable): Unit = onTerminate(t, u)
    }))
  }

  val javaTupleLongAndT2ScalaTupleLongAndT = new Function[Tuple2[JLong, T], (Long, T)] {
    override def apply(t: Tuple2[JLong, T]): (Long, T) = (Long2long(t.getT1), t.getT2)
  }

  final def elapsed(): Mono[(Long, T)] = {
    new Mono[(Long, T)](
      jMono.elapsed().map(javaTupleLongAndT2ScalaTupleLongAndT)
    )
  }

  final def elapsed(scheduler: TimedScheduler): Mono[(Long, T)] = {
    new Mono[(Long, T)](
      jMono.elapsed(scheduler).map(javaTupleLongAndT2ScalaTupleLongAndT)
    )
  }

  final def filter(tester: T => Boolean): Mono[T] = {
    new Mono[T](
      jMono.filter(tester)
    )
  }

  final def flatMap[R](mapper: T => Publisher[R]): Flux[R] = {
    new Flux[R](
      jMono.flatMap(mapper)
    )
  }

  final def flatMap[R](mapperOnNext: T => Publisher[R],
                       mapperOnError: Throwable => Publisher[R],
                       mapperOnComplete: () => Publisher[R]): Flux[R] = {
    new Flux[R](
      jMono.flatMap(mapperOnNext, mapperOnError, mapperOnComplete)
    )
  }

  final def flatMapIterable[R](mapper: T => Iterable[R]): Flux[R] = {
    val mapperFunction: Function[T, JIterable[R]] = mapper.andThen(it => it.asJava)
    new Flux[R](
      jMono.flatMapIterable(mapperFunction)
    )
  }

  final def flux(): Flux[T] = {
    new Flux[T](
      jMono.flux()
    )
  }

  final def hasElement: Mono[Boolean] = {
    new Mono[Boolean](
      jMono.hasElement.map[Boolean](scalaFunction2JavaFunction((jb: JBoolean) => boolean2Boolean(jb.booleanValue())))
    )
  }

  final def handle[R](handler: (T, SynchronousSink[R]) => Unit): Mono[R] = {
    new Mono[R](
      jMono.handle(handler)
    )
  }

  //TODO: How to test this?
  final def hide: Mono[T] = {
    new Mono[T](
      jMono.hide()
    )
  }

  final def ignoreElement: Mono[T] = {
    new Mono[T](
      jMono.ignoreElement()
    )
  }

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

  final def mapError(mapper: Throwable => Throwable): Mono[T] = {
    new Mono[T](jMono.mapError(mapper))
  }

  final def mapError[E <: Throwable](`type`: Class[E], mapper: E => Throwable): Mono[T] = {
    new Mono[T](jMono.mapError(`type`, mapper))
  }

  final def mapError(predicate: Throwable => Boolean, mapper: Throwable => Throwable): Mono[T] = {
    new Mono[T](jMono.mapError(predicate, mapper))
  }

  final def materialize(): Mono[Signal[T]] = {
    new Mono[Signal[T]](jMono.materialize())
  }

  final def mergeWith(other: Publisher[_ <: T]): Flux[T] = {
    new Flux[T](jMono.mergeWith(other))
  }

  final def or(other: Mono[_ <: T]): Mono[T] = {
    new Mono[T](jMono.or(other.jMono))
  }

  final def ofType[U](clazz: Class[U]): Mono[U] = {
    new Mono[U](jMono.ofType(clazz))
  }

  final def otherwise(fallback: Throwable => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction: Function[Throwable, JMono[_ <: T]] = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).jMono
    }
    new Mono[T](jMono.otherwise(fallbackFunction))
  }

  final def otherwise[E <: Throwable](`type`: Class[E], fallback: E => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction: Function[E, JMono[_ <: T]] = new Function[E, JMono[_ <: T]] {
      override def apply(t: E): JMono[_ <: T] = fallback(t).jMono
    }
    new Mono[T](jMono.otherwise(`type`, fallbackFunction))
  }

  final def otherwise(predicate: Throwable => Boolean, fallback: Throwable => Mono[_ <: T]): Mono[T] = {
    val fallbackFunction: Function[Throwable, JMono[_ <: T]] = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).jMono
    }
    new Mono[T](jMono.otherwise(predicate, fallbackFunction))
  }

  final def otherwiseIfEmpty(alternate: Mono[_ <: T]): Mono[T] = {
    new Mono[T](jMono.otherwiseIfEmpty(alternate.jMono))
  }

  final def otherwiseReturn(fallback: T): Mono[T] = {
    new Mono[T](jMono.otherwiseReturn(fallback))
  }

  final def otherwiseReturn[E <: Throwable](`type`: Class[E], fallback: T): Mono[T] = {
    new Mono[T](jMono.otherwiseReturn(`type`, fallback))
  }

  final def otherwiseReturn(predicate: Throwable => Boolean, fallback: T): Mono[T] = {
    new Mono[T](jMono.otherwiseReturn(predicate, fallback))
  }

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

  final def repeat(): Flux[T] = {
    new Flux[T](jMono.repeat())
  }

  final def repeat(predicate: () => Boolean): Flux[T] = {
    new Flux[T](jMono.repeat(predicate))
  }

  final def repeat(n: Long): Flux[T] = {
    new Flux[T](jMono.repeat(n))
  }

  final def repeat(n: Long, predicate: () => Boolean): Flux[T] = {
    new Flux[T](jMono.repeat(n, predicate))
  }

  private implicit def fluxLong2PublisherAnyToJFluxJLong2PublisherAny(mapper: (Flux[Long] => Publisher[_])): Function[JFlux[JLong], Publisher[_]] = {
    new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = mapper(t)
    }
  }

  //  TODO: How to test this?
  final def repeatWhen(whenFactory: Flux[Long] => _ <: Publisher[_]): Flux[T] = {
    new Flux[T](jMono.repeatWhen(whenFactory))
  }

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

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit): Disposable = {
    jMono.subscribe(consumer, errorConsumer)
  }

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit): Disposable = {
    jMono.subscribe(consumer, errorConsumer, completeConsumer)
  }

  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit, subscriptionConsumer: Subscription => Unit): Disposable = {
    jMono.subscribe(consumer, errorConsumer, completeConsumer, subscriptionConsumer)
  }

  //  TODO: How to test this?
  final def subscribeOn(scheduler: Scheduler): Mono[T] = {
    new Mono[T](jMono.subscribeOn(scheduler))
  }

  //  TODO: How to test this?
  final def subscribeWith[E <: Subscriber[_ >: T]](subscriber: E): E = {
    jMono.subscribeWith(subscriber)
  }

  implicit def jMonoVoid2jMonoUnit(jMonoVoid: JMono[Void]): JMono[Unit] = {
    jMonoVoid.map((_: Void) => ())
  }

  final def `then`(): Mono[Unit] = {
    new Mono[Unit](jMono.`then`())
  }

  final def `then`[R](transformer: T => Mono[R]): Mono[R] = {
    new Mono[R](jMono.`then`[R](transformer: Function[T, JMono[_ <: R]]))
  }

  final def `then`[R](other: Mono[R]): Mono[R] = {
    new Mono[R](jMono.`then`(other))
  }

  final def `then`[R](sourceSupplier: () => _ <: Mono[R]): Mono[R] = {
    new Mono[R](jMono.`then`[R](sourceSupplier))
  }

  final def thenEmpty(other: Publisher[Unit]): Mono[Unit] = {
    new Mono[Unit]((jMono: JMono[T]).thenEmpty(other))
  }

  final def thenMany[V](other: Publisher[V]): Flux[V] = {
    new Flux[V](jMono.thenMany(other))
  }

  final def thenMany[V](afterSupplier: () => Publisher[V]): Flux[V] = {
    new Flux[V](jMono.thenMany(afterSupplier))
  }

  final def timeout(duration: Duration): Mono[T] = {
    Mono(jMono.timeout(duration))
  }

  final def timeout(duration: Duration, fallback: Mono[_ <: T]): Mono[T] = {
    new Mono[T](jMono.timeout(duration, fallback))
  }

  final def timeout[U](firstTimeout: Publisher[U]): Mono[T] = {
    new Mono[T](jMono.timeout(firstTimeout))
  }

  final def timeout[U](firstTimeout: Publisher[U], fallback: Mono[_ <: T]): Mono[T] = {
    new Mono[T](jMono.timeout(firstTimeout, fallback))
  }

  final def timeoutMillis(timeout: Long): Mono[T] = {
    new Mono[T](jMono.timeoutMillis(timeout))
  }

  final def timeoutMillis(timeout: Long, timer: TimedScheduler): Mono[T] = {
    new Mono[T](jMono.timeoutMillis(timeout, timer))
  }

  final def timeoutMillis(timeout: Long, fallback: Mono[T]): Mono[T] = {
    new Mono[T](jMono.timeoutMillis(timeout, fallback))
  }

  final def timeoutMillis(timeout: Long, fallback: Mono[T], timer: TimedScheduler): Mono[T] = {
    new Mono[T](jMono.timeoutMillis(timeout, fallback, timer))
  }

  //  TODO: How to test timestamp(...) with the actual timestamp?
  final def timestamp(): Mono[(Long, T)] = {
    new Mono[(Long, T)](jMono.timestamp().map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))
  }

  final def timestamp(scheduler: TimedScheduler): Mono[(Long, T)] = {
    new Mono[(Long, T)](jMono.timestamp(scheduler).map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))
  }

  final def toFuture: Future[T] = {
    val promise = Promise[T]()
    jMono.toFuture.handle[Unit]((value: T, throwable: Throwable) => {
      Option(value).foreach(v => promise.complete(Try(v)))
      Option(throwable).foreach(t => promise.failure(t))
      ()
    })
    promise.future
  }

  final def transform[V](transformer: Mono[T] => Publisher[V]): Mono[V] = {
    new Mono[V](jMono.transform[V]((_: JMono[T]) => transformer(Mono.this)))
  }

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

  def delay(duration: scala.concurrent.duration.Duration): Mono[Long] = {
    new Mono[Long](
      JMono.delay(JDuration.ofNanos(duration.toNanos))
        .map(new Function[JLong, Long] {
          override def apply(t: JLong): Long = t
        })
    )
  }

  def delayMillis(duration: Long): Mono[Long] = {
    new Mono[Long](
      JMono.delayMillis(Long2long(duration))
        .map(new Function[JLong, Long] {
          override def apply(t: JLong): Long = t
        })
    )
  }

  def delayMillis(duration: Long, timedScheduler: TimedScheduler): Mono[Long] = {
    new Mono[Long](
      JMono.delayMillis(Long2long(duration), timedScheduler)
        .map(new Function[JLong, Long] {
          override def apply(t: JLong): Long = t
        })
    )
  }

  def empty[T]: Mono[T] = {
    new Mono[T](
      JMono.empty()
    )
  }

  def empty[T](source: Publisher[T]): Mono[Unit] = {
    new Mono[Unit](
      JMono.empty(source)
        .map(new Function[Void, Unit] {
          override def apply(t: Void): Unit = ()
        })
    )
  }

  def error[T](throwable: Throwable): Mono[T] = {
    new Mono[T](
      JMono.error(throwable)
    )
  }

  def from[T](publisher: Publisher[_ <: T]): Mono[T] = {
    new Mono[T](
      JMono.from(publisher)
    )
  }

  def fromCallable[T](callable: Callable[T]): Mono[T] = {
    new Mono[T](
      JMono.fromCallable(callable)
    )
  }

  def fromFuture[T](future: Future[T])(implicit executionContext: ExecutionContext): Mono[T] = {
    val completableFuture = new CompletableFuture[T]()
    future onComplete {
      case Success(t) => completableFuture.complete(t)
      case Failure(error) => completableFuture.completeExceptionally(error)
    }
    new Mono[T](
      JMono.fromFuture(completableFuture)
    )
  }

  def fromRunnable(runnable: Runnable): Mono[Unit] = {
    new Mono[Unit](
      JMono.fromRunnable(runnable).map(new Function[Void, Unit] {
        override def apply(t: Void): Unit = ()
      })
    )
  }

  def fromSupplier[T](supplier: () => T): Mono[T] = {
    new Mono[T](
      JMono.fromSupplier(new Supplier[T] {
        override def get(): T = supplier()
      })
    )
  }

  def ignoreElements[T](publisher: Publisher[T]): Mono[T] = {
    new Mono[T](
      JMono.ignoreElements(publisher)
    )
  }

  def just[T](data: T): Mono[T] = {
    new Mono[T](JMono.just(data))
  }

  def justOrEmpty[T](data: Option[_ <: T]): Mono[T] = {
    new Mono[T](
      JMono.justOrEmpty[T](data)
    )
  }

  def justOrEmpty[T](data: T): Mono[T] = {
    new Mono[T](
      JMono.justOrEmpty(data)
    )
  }

  def never[T]: Mono[T] = {
    new Mono[T](
      JMono.never[T]()
    )
  }

  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T]): Mono[Boolean] = {
    new Mono[Boolean](
      JMono.sequenceEqual[T](source1, source2).map(new Function[JBoolean, Boolean] {
        override def apply(t: JBoolean): Boolean = Boolean2boolean(t)
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
    * @param p1 The first upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param p2 The second upstream { @link org.reactivestreams.Publisher} to subscribe to.
    * @param combinator a [[scala.Function2]] combinator function when both sources
    *                             complete
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam O output value
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
  def when(sources: Iterable[_ <: Publisher[Unit]]): Mono[Unit] = {
    val mappedSources: Iterable[Publisher[Void]] = sources.map {
      case m: Mono[Unit] =>
        val jMono: JMono[Unit] = m.jMono
        jMono.map(new Function[Unit, Void] {
          override def apply(t: Unit): Void = None.orNull
        }): JMono[Void]
    }
    val mono: JMono[Void] = JMono.when(mappedSources.asJava)
    new Mono[Unit](
      mono.map(new Function[Void, Unit] {
        override def apply(t: Void): Unit = ()
      }): JMono[Unit]
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
  def when(sources: Publisher[Unit]*): Mono[Unit] = {
    val mappedSources: Seq[JMono[Void]] = sources.map {
      case m: Mono[Unit] =>
        val jMono: JMono[Unit] = m.jMono
        jMono.map(new Function[Unit, Void] {
          override def apply(t: Unit): Void = None.orNull
        }): JMono[Void]
    }
    val mono: JMono[Void] = JMono.when(mappedSources.asJava)
    new Mono[Unit](
      mono.map(new Function[Void, Unit] {
        override def apply(t: Void): Unit = ()
      })
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

  def whenDelayError[T1, T2](p1: Mono[_ <: T1], p2: Mono[_ <: T2]): Mono[(T1, T2)] = {
    val jMono = JMono.whenDelayError[T1, T2](p1.jMono, p2.jMono)
    new Mono[(T1, T2)](
      jMono.map(new Function[Tuple2[T1, T2], (T1, T2)] {
        override def apply(t: Tuple2[T1, T2]): (T1, T2) = tupleTwo2ScalaTuple2(t)
      })
    )
  }

  def whenDelayError[T1, T2, T3](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3]): Mono[(T1, T2, T3)] = {
    val jMono = JMono.whenDelayError[T1, T2, T3](p1.jMono, p2.jMono, p3.jMono)
    new Mono[(T1, T2, T3)](
      jMono.map(new Function[Tuple3[T1, T2, T3], (T1, T2, T3)] {
        override def apply(t: Tuple3[T1, T2, T3]): (T1, T2, T3) = tupleThree2ScalaTuple3(t)
      })
    )
  }

  def whenDelayError[T1, T2, T3, T4](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4]): Mono[(T1, T2, T3, T4)] = {
    val jMono = JMono.whenDelayError[T1, T2, T3, T4](p1.jMono, p2.jMono, p3.jMono, p4.jMono)
    new Mono[(T1, T2, T3, T4)](
      jMono.map(new Function[Tuple4[T1, T2, T3, T4], (T1, T2, T3, T4)] {
        override def apply(t: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = tupleFour2ScalaTuple4(t)
      })
    )
  }

  def whenDelayError[T1, T2, T3, T4, T5](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5]): Mono[(T1, T2, T3, T4, T5)] = {
    val jMono = JMono.whenDelayError[T1, T2, T3, T4, T5](p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono)
    new Mono[(T1, T2, T3, T4, T5)](
      jMono.map(new Function[Tuple5[T1, T2, T3, T4, T5], (T1, T2, T3, T4, T5)] {
        override def apply(t: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = tupleFive2ScalaTuple5(t)
      })
    )
  }

  def whenDelayError[T1, T2, T3, T4, T5, T6](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5], p6: Mono[_ <: T6]): Mono[(T1, T2, T3, T4, T5, T6)] = {
    val jMono = JMono.whenDelayError[T1, T2, T3, T4, T5, T6](p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono, p6.jMono)
    new Mono[(T1, T2, T3, T4, T5, T6)](
      jMono.map(new Function[Tuple6[T1, T2, T3, T4, T5, T6], (T1, T2, T3, T4, T5, T6)] {
        override def apply(t: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = tupleSix2ScalaTuple6(t)
      })
    )
  }

  def whenDelayError(sources: Publisher[Unit]*): Mono[Unit] = {
    val jSources: Seq[JMono[Void]] = sources.map {
      case m: Mono[Unit] => m.jMono.map(new Function[Unit, Void] {
        override def apply(t: Unit): Void = None.orNull
      }): JMono[Void]
    }
    new Mono[Unit](
      JMono.whenDelayError(jSources.toArray: _*)
        .map(new Function[Void, Unit] {
          override def apply(t: Void): Unit = ()
        })
    )
  }

  def whenDelayError[R](combinator: (Array[Any] => R), monos: Mono[Any]*): Mono[R] = {
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
      JMono.whenDelayError(combinatorFunction, jMonos.toArray: _*)
    )
  }

  def zip[T, V](combinator: (Array[Any] => V), monos: Mono[_ <: T]*): Mono[V] = {
    val combinatorFunction: Function[_ >: Array[Object], _ <: V] = new Function[Array[Object], V] {
      override def apply(t: Array[Object]): V = {
        val v: Array[Any] = t.map { v => v: Any }
        combinator(v)
      }
    }
    val jMonos = monos.map(_.jMono)
    new Mono[V](
      JMono.zip(combinatorFunction, jMonos.toArray: _*)
    )
  }

  def zip[T, V](combinator: (Array[Any] => V), monos: Iterable[Mono[_ <: T]]): Mono[V] = {
    val combinatorFunction: Function[_ >: Array[Object], _ <: V] = new Function[Array[Object], V] {
      override def apply(t: Array[Object]): V = {
        //the reason we do the following is because the underlying reactor is by default allocating 8 elements with null, so we need to get rid of null
        val v: Array[Any] = t.map { v => Option(v): Option[Any] }.filterNot(_.isEmpty).map(_.getOrElse(None.orNull))
        combinator(v)
      }
    }
    val jMonos: JIterable[JMono[T]] = monos.map(_.jMono).asJava.asInstanceOf[JIterable[JMono[T]]]
    new Mono[V](
      JMono.zip(combinatorFunction, jMonos)
    )
  }
}
