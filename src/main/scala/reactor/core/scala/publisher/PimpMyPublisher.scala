package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux, Mono => JMono}

import scala.language.implicitConversions

/**
  * Created by winarto on 1/17/17.
  */
object PimpMyPublisher {

  implicit def jfluxToFlux[T](jflux: JFlux[T]): Flux[T] = Flux.from(jflux)

  implicit def fluxToJFlux[T](flux: Flux[T]): JFlux[T] = flux.asJava()

  implicit def jMonoToMono[T](jMono: JMono[T]): Mono[T] = Mono(jMono)

  implicit def monoToJMono[T](mono: Mono[T]): JMono[T] = mono.asJava()
}
