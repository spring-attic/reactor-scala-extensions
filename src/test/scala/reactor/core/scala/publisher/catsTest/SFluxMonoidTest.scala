package reactor.core.scala.publisher.catsTest

import cats.MonoidK
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SFlux

import scala.language.{higherKinds, implicitConversions}

class SFluxMonoidTest extends AnyFreeSpec with Matchers {
  "SFlux" - {
    "should pass associative law" in {
      val x = SFlux.catsInstances.pure(5)
      val y = SFlux.catsInstances.pure(7)
      val z = SFlux.catsInstances.pure(9)
      associativeLaw(x, y, z)
    }
    "should pass identity law" in {
      identityLaw(SFlux.catsInstances.pure(10))
    }
  }

  def associativeLaw[A, F[_]](x: F[A], y: F[A], z: F[A])(implicit m: MonoidK[F]): Assertion = {
    val a = m.combineK(x, m.combineK(y, z)).toIterable().toSeq
    val b = m.combineK(m.combineK(x, y), z).toIterable().toSeq
    a shouldBe b
  }

  def identityLaw[A, F[_]](x: F[A])(implicit m: MonoidK[F]): Assertion = {
    ((m.combineK(x, m.empty).toIterable().toSeq == x.toIterable().toSeq)
      && (m.combineK(m.empty, x).toIterable().toSeq == x.toIterable().toSeq)) shouldBe true
  }
}