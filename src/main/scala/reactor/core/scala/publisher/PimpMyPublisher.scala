package reactor.core.scala.publisher

import java.lang.{Long => JLong}

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

  implicit def jFluxJLong2FluxLong(jFluxLong: JFlux[JLong]): Flux[Long] = Flux.from(jFluxLong.map(Long2long(_: JLong)))

  implicit def jFluxJInt2JFluxInt(jFluxInt: JFlux[Integer]): JFlux[Int] = jFluxInt.map[Int]((i: Integer) => Integer2int(i))

  implicit def jMonoJLong2JMonoLong(mono: JMono[JLong]): JMono[Long] = mono.map(Long2long(_: JLong))
}
