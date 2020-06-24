package reactor.core.scala.publisher.catsTest

import org.scalatest.freespec.AnyFreeSpec
import reactor.core.scala.publisher.SFlux
import reactor.test.StepVerifier

class SFluxFunctorFilterTest extends AnyFreeSpec {
  "SFlux" - {
    "should be able to map and filter in single operation" in {
      val x = SFlux.catsInstances.mapFilter(SFlux.just(1, 2, 3, 4, 5)) {
        case i if i % 2 == 1 => Option(i)
        case _ => None
      }
      StepVerifier.create(x)
        .expectNext(1, 3, 5)
        .verifyComplete()
    }
  }
}
