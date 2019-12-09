package reactor.core.scala

import java.lang.{Iterable => JIterable, Long => JLong}
import java.time.{Duration => JDuration}
import java.util.Optional.empty
import java.util.concurrent.Callable
import java.util.function.{BiConsumer, BiFunction, BiPredicate, BooleanSupplier, Consumer, Function, LongConsumer, Predicate, Supplier}
import java.util.stream.{StreamSupport, Stream => JStream}
import java.util.{Optional, Spliterator, Spliterators}

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux, Mono => JMono}
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

/**
  * Created by winarto on 12/31/16.
  */
package object publisher {
  implicit def scalaDuration2JavaDuration(duration: Duration): JDuration = {
    JDuration.ofNanos(duration.toNanos)
  }

  implicit def scalaOption2JavaOptional[T](option: Option[T]): Optional[T] = option.map(Optional.of[T]).getOrElse(empty())

  implicit def tupleTwo2ScalaTuple2[T1, T2](javaTuple2: Tuple2[T1, T2]): (T1, T2) = {
    (javaTuple2.getT1, javaTuple2.getT2)
  }

  implicit def tupleThree2ScalaTuple3[T1, T2, T3](javaTuple3: Tuple3[T1, T2, T3]): (T1, T2, T3) = {
    (javaTuple3.getT1, javaTuple3.getT2, javaTuple3.getT3)
  }

  implicit def tupleFour2ScalaTuple4[T1, T2, T3, T4](javaTuple4: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = {
    (javaTuple4.getT1, javaTuple4.getT2, javaTuple4.getT3, javaTuple4.getT4)
  }

  implicit def tupleFive2ScalaTuple5[T1, T2, T3, T4, T5](javaTuple5: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = {
    (javaTuple5.getT1, javaTuple5.getT2, javaTuple5.getT3, javaTuple5.getT4, javaTuple5.getT5)
  }

  implicit def tupleSix2ScalaTuple6[T1, T2, T3, T4, T5, T6](javaTuple6: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = {
    (javaTuple6.getT1, javaTuple6.getT2, javaTuple6.getT3, javaTuple6.getT4, javaTuple6.getT5, javaTuple6.getT6)
  }

  implicit def javaTupleLongAndT2ScalaTupleLongAndT[T](tuple2: Tuple2[JLong, T]): (Long, T) = (tuple2.getT1, tuple2.getT2)

  type SConsumer[T] = T => Unit
  type SPredicate[T] = T => Boolean
  type SBiConsumer[T, U] = (T, U) => Unit
  type JBiConsumer[T, U] = BiConsumer[T, U]

  implicit def scalaConsumer2JConsumer[T](sc: SConsumer[T]): Consumer[T] = {
    new Consumer[T] {
      override def accept(t: T): Unit = Option(sc).foreach(x => x(t))
    }
  }

  implicit def scalaPredicate2JPredicate[T](sp: SPredicate[T]): Predicate[T] = {
    new Predicate[T] {
      override def test(t: T): Boolean = sp(t)
    }
  }

  implicit def scalaLongConsumer2JLongConsumer(lc: SConsumer[Long]): LongConsumer = {
    new LongConsumer {
      override def accept(value: Long): Unit = lc(value)
    }
  }

  implicit def scalaFunction2JavaFunction[T, U](function: T => U): Function[T, U] = {
    new Function[T, U] {
      override def apply(t: T): U = function(t)
    }
  }

  implicit def unit2SupplierT[T](supplier: () => T): Supplier[T] = {
    new Supplier[T] {
      override def get(): T = supplier()
    }
  }

  implicit def scalaBiConsumer2JavaBiConsumer[T, U](biConsumer: SBiConsumer[T, U]): JBiConsumer[T, U] = {
    new BiConsumer[T, U] {
      override def accept(t: T, u: U): Unit = biConsumer(t, u)
    }
  }

  implicit def scalaBooleanSupplier2JavaBooleanSupplier(supplier: () => Boolean): BooleanSupplier = {
    new BooleanSupplier {
      override def getAsBoolean: Boolean = supplier()
    }
  }


  implicit def mappableJLong2MappableLong(mappableJLong: MapablePublisher[JLong]): MapablePublisher[Long] = mappableJLong.map(Long2long(_: JLong))

  implicit def fluxTToU2JFluxTToU[T, U](fluxTToU: SFlux[T] => U): Function[JFlux[T], U] = {
    new Function[JFlux[T], U] {
      override def apply(t: JFlux[T]): U = fluxTToU(SFlux.fromPublisher(t))
    }
  }

  implicit def runnableMapper(runnable: => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = runnable
    }
  }

  implicit def scalaFunctionTToMonoR2JavaFunctionTToJMonoR[T, R](function: T => SMono[R]): Function[T, JMono[_ <: R]] = {
    new Function[T, JMono[_ <: R]] {
      override def apply(t: T): JMono[R] = function(t).asJava()
    }
  }

  implicit def scalaSupplierSMonoR2JavaSupplierJMonoR[R](supplier: () => SMono[R]): Supplier[JMono[R]] = {
    new Supplier[JMono[R]] {
      override def get(): JMono[R] = supplier().asJava()
    }
  }

  implicit def publisherUnit2PublisherVoid(publisher: MapablePublisher[Unit]): Publisher[Void] = {
    publisher.map[Void](_ => null: Void)
  }

  implicit def scalaBiFunction2JavaBiFunction[T, U, V](biFunction: (T, U) => V): BiFunction[T, U, V] = {
    new BiFunction[T, U, V] {
      override def apply(t: T, u: U): V = biFunction(t, u)
    }
  }

  implicit def scalaIterable2JavaIterable[T](scalaIterable: Iterable[T]): JIterable[T] = {
    scalaIterable.asJava
  }

  implicit def scalaFunction2JavaCallable[T] (scalaFunction: () => T): Callable[T] = {
    new Callable[T] {
      override def call(): T = scalaFunction()
    }
  }

  implicit def scalaFunction2JavaRunnable(scalaFunction: () => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = scalaFunction()
    }
  }

  implicit def scalaBiPredicate2JavaBiPredicate[T, U](scalaBiPredicate: (T, U) => Boolean): BiPredicate[T, U] = new BiPredicate[T, U] {
    override def test(t: T, u: U): Boolean = scalaBiPredicate(t, u)
  }

  implicit def javaOptional2ScalaOption[T](jOptional: Optional[T]): Option[T] = if(jOptional.isPresent) Some(jOptional.get()) else None

  implicit def scalaStream2JavaStream[T](stream: Stream[T]): JStream[T] = {
    StreamSupport.stream(Spliterators.spliteratorUnknownSize[T](stream.toIterator.asJava, Spliterator.NONNULL), false)
  }
}
