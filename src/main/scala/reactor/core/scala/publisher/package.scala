package reactor.core.scala

import java.lang.{Boolean => JBoolean, Iterable => JIterable, Long => JLong}
import java.time.{Duration => JDuration}
import java.util.Optional
import java.util.concurrent.Callable
import java.util.function.{BiConsumer, BiFunction, BooleanSupplier, Consumer, Function, LongConsumer, Predicate, Supplier}

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux, Mono => JMono}
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.concurrent.duration.Duration
import scala.language.implicitConversions

/**
  * Created by winarto on 12/31/16.
  */
package object publisher {
  implicit def scalaDuration2JavaDuration(duration: Duration): JDuration = {
    JDuration.ofNanos(duration.toNanos)
  }

  implicit def scalaOption2JavaOptional[T](option: Option[T]): Optional[T] = {
    option.map(Optional.of[T]).getOrElse(Optional.empty())
  }

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

/*
Uncomment this when used. It is not used for now and reduce the code coverage
  implicit def try2Boolean[T](atry: Try[T]): Boolean = atry match {
    case Success(_) => true
    case Failure(_) => false
  }
*/

  type SConsumer[T] = (T => Unit)
  type SPredicate[T] = (T => Boolean)
  type SBiConsumer[T, U] = (T, U) => Unit
  type JBiConsumer[T, U] = BiConsumer[T, U]

  implicit def scalaConsumer2JConsumer[T](sc: SConsumer[T]): Consumer[T] = {
    new Consumer[T] {
      override def accept(t: T): Unit = sc(t)
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

  implicit def jFluxJLong2FluxLong(jFluxLong: JFlux[JLong]): Flux[Long] = Flux.from(jFluxLong.map(Long2long(_: JLong)))

  implicit def jFluxJInt2JFluxInt(jFluxInt: JFlux[Integer]): JFlux[Int] = jFluxInt.map[Int]((i: Integer) => Integer2int(i))

  implicit def mappableJLong2MappableLong(mappableJLong: MapablePublisher[JLong]): MapablePublisher[Long] = mappableJLong.map(Long2long(_: JLong))

  implicit def fluxTToU2JFluxTToU[T, U](fluxTToU: Flux[T] => U): Function[JFlux[T], U] = {
    new Function[JFlux[T], U] {
      override def apply(t: JFlux[T]): U = fluxTToU(Flux.from(t))
    }
  }

  implicit def runnableMapper(runnable: => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = runnable
    }
  }

  implicit def scalaFunctionTToMonoR2JavaFunctionTToJMonoR[T, R](function: T => Mono[R]): Function[T, JMono[_ <: R]] = {
    new Function[T, JMono[_ <: R]] {
      override def apply(t: T): JMono[R] = function(t).asJava()
    }
  }

  implicit def scalaSupplierMonoR2JavaSupplierJMonoR[R](supplier: () => Mono[R]): Supplier[JMono[R]] = {
    new Supplier[JMono[R]] {
      override def get(): JMono[R] = supplier().asJava()
    }
  }

  implicit def publisherUnit2PublisherVoid(publisher: Publisher[Unit]): Publisher[Void] = {
    publisher match {
      case m: Mono[Unit] => m.map[Void](_ => null: Void)
    }
  }

  implicit def scalaBiFunction2JavaBiFunction[T, U, V](biFunction: (T, U) => V): BiFunction[T, U, V] = {
    new BiFunction[T, U, V] {
      override def apply(t: T, u: U): V = biFunction(t, u)
    }
  }

  implicit def scalaIterable2JavaIterable[T](scalaIterable: Iterable[T]): JIterable[T] = {
    import scala.collection.JavaConverters._
    scalaIterable.asJava
  }

  implicit def scalaFunction2JavaCallable[T] (scalaFunction: () => T): Callable[T] = {
    new Callable[T] {
      override def call(): T = scalaFunction()
    }
  }
}
