package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux, Mono => JMono}

import scala.language.implicitConversions

trait ScalaConverters {
  implicit class PimpJMono[T](jMono: JMono[T]) {
    def asScala: SMono[T] = new ReactiveSMono[T](jMono)
  }

  implicit class PimpJFlux[T](jFlux: JFlux[T]) {
    def asScala: SFlux[T] = new ReactiveSFlux[T](jFlux)
  }
}

object ScalaConverters extends ScalaConverters
