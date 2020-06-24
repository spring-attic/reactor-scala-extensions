package reactor.core.scala.publisher.catsTest

import org.scalatest.freespec.AnyFreeSpec
import reactor.core.scala.publisher.SFlux
import reactor.test.StepVerifier

class SFluxCoflatMapTest extends AnyFreeSpec {
  "SFlux" - {
    "coFlatMap should work" in {
      val x = SFlux.catsInstances.coflatMap(SFlux.just(123))(_ => "abc")
      StepVerifier.create(x)
        .expectNext("abc")
        .verifyComplete()
    }
  }

}
