package reactor.core.scala.publisher

import reactor.core.publisher.{Mono => JMono}

import scala.language.implicitConversions

trait ScalaConverters {
  implicit class PimpJMono[T](jMono: JMono[T]) {
    def asScala: SMono[T] = new ReactiveSMono[T](jMono)
  }
}

object ScalaConverters extends ScalaConverters
