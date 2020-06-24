package reactor.core.scala.publisher.catsTest

import cats.Functor
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SFlux

import scala.language.{higherKinds, implicitConversions}

class SFluxFunctorTest extends AnyFreeSpec with Matchers {
  "SFlux" - {
    "should pass identity law" in {
      identityLaw(SFlux.catsInstances.pure(3))
    }

    "should pass composition law" in {
      compositionLaw(SFlux.catsInstances.pure(5))
    }
  }

  def identityLaw[A, F[_]](x: F[A])(implicit f: Functor[F]): Assertion = {
    f.map(x)(x => x).toIterable().toSeq shouldBe x.toIterable().toSeq
  }

  def compositionLaw[B, C, F[_]](x: F[Int])(implicit functor: Functor[F]): Assertion = {
    val f = (n: Int) => n.toDouble
    val g = (d: Double) => d.toString
    val y = functor.map(x)(f).map(g)
    val z = functor.map(functor.map(x)(f))(g)
    z.toIterable().toSeq shouldBe y.toIterable().toSeq
  }
}