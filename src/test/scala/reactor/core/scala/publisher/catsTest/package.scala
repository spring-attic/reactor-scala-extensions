package reactor.core.scala.publisher

import scala.language.{higherKinds, implicitConversions}

package object catsTest {
  implicit def monoidK2SFlux[A, F[_]](m: F[A]): SFlux[A] = m.asInstanceOf[SFlux[A]]
}
