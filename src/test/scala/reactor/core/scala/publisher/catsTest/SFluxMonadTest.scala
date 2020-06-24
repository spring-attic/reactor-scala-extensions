package reactor.core.scala.publisher.catsTest

import cats.Monad
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SFlux

import scala.language.higherKinds

class SFluxMonadTest extends AnyFreeSpec with Matchers {
  "SFlux" - {
    "should pass left identity law" in {
      leftIdentity(5, (i: Int) => SFlux.just(i.toString))
    }

    "should pass right identity law" in {
      rightIdentity(SFlux.just(11))
    }

    "should pass associativity law" in {
      associativity(SFlux.just(13), (i: Int) => SFlux.just(i.toDouble), (d: Double) => SFlux.just(d.toString))
    }
  }

  def leftIdentity[A, B](a: A, func: A => SFlux[B]): Assertion = {
    val x = SFlux.catsInstances.pure(a).flatMap(func)
    val y = func(a)
    x.toIterable().toSeq shouldBe y.toIterable().toSeq
  }

  def rightIdentity[F[_]](f: F[_])(implicit m: Monad[F]): Assertion = {
    val x = m.flatMap(f)(m.pure)
    x.toIterable().toSeq shouldBe f.toIterable().toSeq
  }

  def associativity[A, B, C, F[_]](fa: F[A], f: A => F[B], g: B => F[C])(implicit m: Monad[F]): Assertion = {
    val x = m.flatMap(m.flatMap(fa)(f))(g)
    val y = m.flatMap(fa)(a => m.flatMap(f(a))(g))
    x.toIterable().toSeq shouldBe y.toIterable().toSeq
  }
}
