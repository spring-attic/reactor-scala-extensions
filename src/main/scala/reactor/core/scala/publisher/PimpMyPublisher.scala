package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux}

import scala.language.implicitConversions

/**
  * Created by winarto on 1/17/17.
  */
object PimpMyPublisher {

  implicit def jfluxToFlux(jflux: JFlux[_]): Flux[_] = Flux.from(jflux)

}
