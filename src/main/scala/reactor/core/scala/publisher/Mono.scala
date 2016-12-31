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

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Callable, CompletableFuture}
import java.util.function.{BiConsumer, Consumer, Function, Supplier}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{MonoSink, Mono => JMono}
import reactor.core.scheduler.TimedScheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Created by winarto on 12/26/16.
  */
class Mono[T](private val jMono: JMono[T]) extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jMono.subscribe(s)

  def  map[R](mapper: T => R): Mono[R] = {
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
        override def apply(t: Void):Unit = ()
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
}
