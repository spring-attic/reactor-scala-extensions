package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux, Mono => JMono}

import scala.language.implicitConversions

/**
  * Created by winarto on 1/17/17.
  */
object PimpMyPublisher {

  implicit def jfluxToFlux(jflux: JFlux[_]): Flux[_] = Flux.from(jflux)

  implicit def fluxToJFlux(flux: Flux[_]): JFlux[_] = flux.asJava()

  implicit def jMonoToMono(jMono: JMono[_]): Mono[_] = Mono(jMono)

  implicit def monoToJMono(mono: Mono[_]): JMono[_] = mono.asJava()
}
