package reactor.core.scala

import java.time.{Duration => JDuration}

import scala.concurrent.duration.Duration
import scala.language.implicitConversions

/**
  * Created by winarto on 12/31/16.
  */
package object publisher {
  implicit def scalaDuration2JavaDuration(duration: Duration): JDuration = {
    JDuration.ofNanos(duration.toNanos)
  }
}
