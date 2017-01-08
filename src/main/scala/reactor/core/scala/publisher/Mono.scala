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
import java.util.function.{BiConsumer, BiFunction, Consumer, Function, Supplier}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{MonoSink, Signal, SignalType, Mono => JMono}
import reactor.core.scheduler.{Scheduler, TimedScheduler}
import reactor.util.function._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by winarto on 12/26/16.
  */
class Mono[T](private val jMono: JMono[T]) extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jMono.subscribe(s)

  final def as[P](transformer: (Mono[T] => P)): P = {
    transformer(this)
  }

  final def and[T2](other: Mono[_ <: T2]): Mono[(T, T2)] = {
    val combinedMono: JMono[Tuple2[T, T2]] = jMono.and(other.jMono)
    new Mono[(T, T2)](
      combinedMono
        .map(new Function[Tuple2[T, T2], (T, T2)] {
          override def apply(t: Tuple2[T, T2]): (T, T2) = tupleTwo2ScalaTuple2(t)
        })
    )
  }

  final def ++[T2](other: Mono[_ <: T2]): Mono[(T, T2)] = {
    and(other)
  }

  final def and[T2, O](other: Mono[T2], combinator: (T, T2) => O): Mono[O] = {
    val combinatorFunction: BiFunction[T, T2, O] = new BiFunction[T, T2, O] {
      override def apply(t: T, u: T2): O = combinator(t, u)
    }
    val combinedMono: JMono[O] = jMono.and(other.jMono, combinatorFunction)
    new Mono[O](combinedMono)
  }

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

  final def awaitOnSubscribe(): Mono[T] = {
    new Mono[T](
      jMono.awaitOnSubscribe()
    )
  }

  final def block(): T = {
    jMono.block()
  }

  final def block(duration: Duration): T = {
    jMono.block(duration)
  }

  final def blockMillis(millis: Long): T = {
    jMono.blockMillis(millis)
  }

  final def cast[E](clazz: Class[E]): Mono[E] = {
    new Mono[E](
      jMono.cast(clazz)
    )
  }

  final def cache(): Mono[T] = {
    new Mono[T](
      jMono.cache()
    )
  }

  final def cancelOn(scheduler: Scheduler): Mono[T] = {
    new Mono[T](
      jMono.cancelOn(scheduler)
    )
  }

  final def compose[V](transformer: (Mono[T] => Publisher[V])): Mono[V] = {
    val transformerFunction: Function[JMono[T], Publisher[V]] = new Function[JMono[T], Publisher[V]] {
      override def apply(t: JMono[T]): Publisher[V] = transformer(Mono.this)
    }
    new Mono[V](
      jMono.compose(transformerFunction)
    )
  }

  final def concatWith(source: Publisher[T]): Flux[T] = {
    new Flux[T](
      jMono.concatWith(source)
    )
  }

  final def defaultIfEmpty(default: T): Mono[T] = {
    new Mono[T](
      jMono.defaultIfEmpty(default)
    )
  }

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

  def map[R](mapper: T => R): Mono[R] = {
    Mono(jMono.map(new Function[T, R] {
      override def apply(t: T): R = mapper(t)
    }))
  }

  def timeout(duration: Duration): Mono[T] = {
    Mono(jMono.timeout(duration))
  }

  def doOnTerminate(onTerminate: (T, Throwable) => Unit): Mono[T] = {
    Mono(jMono.doOnTerminate(new BiConsumer[T, Throwable] {
      override def accept(t: T, u: Throwable): Unit = onTerminate(t, u)
    }))
  }
}

object Mono {

  /**
    * This function is used as bridge to create scala-wrapper of Mono based on existing Java Mono
    *
    * @param javaMono
    * @tparam T
    * @return Wrapper of Java Mono
    */
  def apply[T](javaMono: JMono[T]) = new Mono[T](javaMono)

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
    new Mono[T](
      JMono.just(data)
    )
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

  def when[T1, T2](p1: Mono[_ <: T1], p2: Mono[_ <: T2]): Mono[(T1, T2)] = {
    val jMono: JMono[Tuple2[T1, T2]] = JMono.when(p1.jMono, p2.jMono)

    new Mono[(T1, T2)](
      jMono.map(new Function[Tuple2[T1, T2], (T1, T2)] {
        override def apply(t: Tuple2[T1, T2]): (T1, T2) = (t.getT1, t.getT2)
      })
    )
  }

  def when[T1, T2, O](p1: Mono[_ <: T1], p2: Mono[_ <: T2], combinator: (T1, T2) => O): Mono[O] = {
    val jMono: JMono[O] = JMono.when(p1.jMono, p2.jMono, new BiFunction[T1, T2, O] {
      override def apply(t: T1, u: T2): O = combinator(t, u)
    })
    new Mono[O](jMono)
  }

  def when[T1, T2, T3](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3]): Mono[(T1, T2, T3)] = {
    val jMono: JMono[Tuple3[T1, T2, T3]] = JMono.when(p1.jMono, p2.jMono, p3.jMono)
    new Mono[(T1, T2, T3)](
      jMono.map(new Function[Tuple3[T1, T2, T3], (T1, T2, T3)] {
        override def apply(t: Tuple3[T1, T2, T3]): (T1, T2, T3) = (t.getT1, t.getT2, t.getT3)
      })
    )
  }

  def when[T1, T2, T3, T4](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4]): Mono[(T1, T2, T3, T4)] = {
    val jMono: JMono[Tuple4[T1, T2, T3, T4]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono)
    new Mono[(T1, T2, T3, T4)](
      jMono.map(new Function[Tuple4[T1, T2, T3, T4], (T1, T2, T3, T4)] {
        override def apply(t: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = (t.getT1, t.getT2, t.getT3, t.getT4)
      })
    )
  }

  def when[T1, T2, T3, T4, T5](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5]): Mono[(T1, T2, T3, T4, T5)] = {
    val jMono: JMono[Tuple5[T1, T2, T3, T4, T5]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono)
    new Mono[(T1, T2, T3, T4, T5)](
      jMono.map(new Function[Tuple5[T1, T2, T3, T4, T5], (T1, T2, T3, T4, T5)] {
        override def apply(t: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = (t.getT1, t.getT2, t.getT3, t.getT4, t.getT5)
      })
    )
  }

  def when[T1, T2, T3, T4, T5, T6](p1: Mono[_ <: T1], p2: Mono[_ <: T2], p3: Mono[_ <: T3], p4: Mono[_ <: T4], p5: Mono[_ <: T5], p6: Mono[_ <: T6]): Mono[(T1, T2, T3, T4, T5, T6)] = {
    val jMono: JMono[Tuple6[T1, T2, T3, T4, T5, T6]] = JMono.when(p1.jMono, p2.jMono, p3.jMono, p4.jMono, p5.jMono, p6.jMono)
    new Mono[(T1, T2, T3, T4, T5, T6)](
      jMono.map(new Function[Tuple6[T1, T2, T3, T4, T5, T6], (T1, T2, T3, T4, T5, T6)] {
        override def apply(t: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = (t.getT1, t.getT2, t.getT3, t.getT4, t.getT5, t.getT6)
      })
    )
  }

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
