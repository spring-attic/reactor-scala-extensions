package reactor.core.scala.publisher

import java.lang.{Long => JLong}

import reactor.core.publisher.{Flux => JFlux, Mono => JMono}

import scala.language.implicitConversions

/**
  * Created by winarto on 1/17/17.
  */
object PimpMyPublisher {

  implicit def fluxToJFlux[T](flux: SFlux[T]): JFlux[T] = flux.asJava()

  implicit def monoToJMono[T](mono: SMono[T]): JMono[T] = mono.asJava()

  implicit def jFluxJLong2FluxLong(jFluxLong: JFlux[JLong]): SFlux[Long] = SFlux.fromPublisher(jFluxLong.map(Long2long(_: JLong)))

  implicit def jFluxJInt2JFluxInt(jFluxInt: JFlux[Integer]): JFlux[Int] = jFluxInt.map[Int]((i: Integer) => Integer2int(i))

  implicit def jMonoJLong2JMonoLong(mono: JMono[JLong]): JMono[Long] = mono.map(Long2long(_: JLong))

  implicit def jFlux2SFlux[T](jFlux: JFlux[T]): SFlux[T] = new ReactiveSFlux[T](jFlux)

  implicit def jMono2SMono[T](jMono: JMono[T]): SMono[T] = new ReactiveSMono[T](jMono)
}
