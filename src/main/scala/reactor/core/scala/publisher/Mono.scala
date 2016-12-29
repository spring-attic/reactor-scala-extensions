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

import java.lang.{Long => JLong}
import java.time.Duration
import java.util.function.{Consumer, Function, Supplier}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{MonoSink, Mono => JMono}
import reactor.core.scheduler.TimedScheduler


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
      JMono.delay(Duration.ofNanos(duration.toNanos))
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
}
