package reactor.core.scala

import java.lang.{Boolean => JBoolean}
import java.time.{Duration => JDuration}
import java.util.Optional

import reactor.util.function.Tuple2

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
}
