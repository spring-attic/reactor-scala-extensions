package reactor.core.scala.publisher.catsTest

import cats.effect.ExitCase
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.scalatest.freespec.AnyFreeSpec
import reactor.core.scala.publisher.SFlux

class SFluxBracketTest extends AnyFreeSpec with IdiomaticMockito with ArgumentMatchersSugar {
  "SFlux" - {
    "should handle bracket" in {
      val x = spy(SFlux.just(123))
      SFlux.catsInstances.bracketCase(x)(i => SFlux.just(i.toString))((_, _) => SFlux.just(()))
      x.bracketCase(any[Int => SFlux[String]])(any[(Int, ExitCase[Throwable]) => Unit]) was called
    }
  }

}
