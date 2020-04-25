package reactor.core.scala.publisher

import reactor.core.publisher.{ConnectableFlux, Flux => JFlux, Mono => JMono}

import scala.language.implicitConversions

trait ScalaConverters {
  implicit class PimpJMono[T](jMono: JMono[T]) {
    def asScala: SMono[T] = new ReactiveSMono[T](jMono)
  }

  implicit class PimpJFlux[T](jFlux: JFlux[T]) {
    def asScala: SFlux[T] = new ReactiveSFlux[T](jFlux)
  }

  implicit class PimpConnectableFlux[T](connectableFlux: ConnectableFlux[T]) {
    def asScala: ConnectableSFlux[T] = ConnectableSFlux(connectableFlux)
  }
}

object ScalaConverters extends ScalaConverters
