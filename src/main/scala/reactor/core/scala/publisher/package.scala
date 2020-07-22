package reactor.core.scala

import java.lang.{Iterable => JIterable, Long => JLong}
import java.time.{Duration => JDuration}
import java.util.Optional
import java.util.Optional.empty
import java.util.concurrent.Callable
import java.util.function.{BiConsumer, BiFunction, BiPredicate, BooleanSupplier, Consumer, Function, LongConsumer, Predicate, Supplier}

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux}
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Created by winarto on 12/31/16.
  */
package object publisher {
  implicit def scalaDuration2JavaDuration(duration: Duration): JDuration = {
    JDuration.ofNanos(duration.toNanos)
  }

  implicit def scalaOption2JavaOptional[T](option: Option[T]): Optional[T] = option.map(Optional.of[T]).getOrElse(empty())

  implicit def tupleTwo2ScalaTuple2[T1, T2](javaTuple2: Tuple2[T1, T2]): (T1, T2) = (javaTuple2.getT1, javaTuple2.getT2)

  implicit def tupleThree2ScalaTuple3[T1, T2, T3](javaTuple3: Tuple3[T1, T2, T3]): (T1, T2, T3) = (javaTuple3.getT1, javaTuple3.getT2, javaTuple3.getT3)

  implicit def tupleFour2ScalaTuple4[T1, T2, T3, T4](javaTuple4: Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = (javaTuple4.getT1, javaTuple4.getT2, javaTuple4.getT3, javaTuple4.getT4)

  implicit def tupleFive2ScalaTuple5[T1, T2, T3, T4, T5](javaTuple5: Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = (javaTuple5.getT1, javaTuple5.getT2, javaTuple5.getT3, javaTuple5.getT4, javaTuple5.getT5)

  implicit def tupleSix2ScalaTuple6[T1, T2, T3, T4, T5, T6](javaTuple6: Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = (javaTuple6.getT1, javaTuple6.getT2, javaTuple6.getT3, javaTuple6.getT4, javaTuple6.getT5, javaTuple6.getT6)

  implicit def javaTupleLongAndT2ScalaTupleLongAndT[T](tuple2: Tuple2[JLong, T]): (Long, T) = (tuple2.getT1, tuple2.getT2)

  type SConsumer[T] = T => Unit
  type SPredicate[T] = T => Boolean
  type SBiConsumer[T, U] = (T, U) => Unit
  type JBiConsumer[T, U] = BiConsumer[T, U]

  implicit def scalaConsumer2JConsumer[T](sc: SConsumer[T]): Consumer[T] = (t: T) => Option(sc).foreach(x => x(t))

  implicit def scalaPredicate2JPredicate[T](sp: SPredicate[T]): Predicate[T] = (t: T) => sp(t)

  implicit def scalaLongConsumer2JLongConsumer(lc: SConsumer[Long]): LongConsumer = (value: Long) => lc(value)

  implicit def scalaFunction2JavaFunction[T, U](function: T => U): Function[T, U] = (t: T) => function(t)

  implicit def unit2SupplierT[T](supplier: () => T): Supplier[T] = () => supplier()

  implicit def scalaBiConsumer2JavaBiConsumer[T, U](biConsumer: SBiConsumer[T, U]): JBiConsumer[T, U] = (t: T, u: U) => biConsumer(t, u)

  implicit def scalaBooleanSupplier2JavaBooleanSupplier(supplier: () => Boolean): BooleanSupplier = new BooleanSupplier {
    override def getAsBoolean: Boolean = supplier()
  }

  implicit def fluxTToU2JFluxTToU[T, U](fluxTToU: SFlux[T] => U): Function[JFlux[T], U] = (t: JFlux[T]) => fluxTToU(SFlux.fromPublisher(t))

  implicit def runnableMapper(runnable: => Unit): Runnable = () => runnable
  
  implicit def publisherUnit2PublisherVoid(publisher: MapablePublisher[Unit]): Publisher[Void] = {
    publisher.map[Void](_ => null: Void)
  }

  implicit def scalaBiFunction2JavaBiFunction[T, U, V](biFunction: (T, U) => V): BiFunction[T, U, V] = (t: T, u: U) => biFunction(t, u)

  implicit def scalaIterable2JavaIterable[T](scalaIterable: Iterable[T]): JIterable[T] = {
    scalaIterable.asJava
  }

  implicit def scalaFunction2JavaCallable[T] (scalaFunction: () => T): Callable[T] = () => scalaFunction()

  implicit def scalaFunction2JavaRunnable(scalaFunction: () => Unit): Runnable = () => scalaFunction()

  implicit def scalaBiPredicate2JavaBiPredicate[T, U](scalaBiPredicate: (T, U) => Boolean): BiPredicate[T, U] = (t: T, u: U) => scalaBiPredicate(t, u)

  implicit def javaOptional2ScalaOption[T](jOptional: Optional[T]): Option[T] = if(jOptional.isPresent) Some(jOptional.get()) else None

}
