package reactor.core.scala.publisher.catsTest

import org.scalatest.freespec.AnyFreeSpec
import reactor.core.scala.publisher.SFlux
import reactor.test.StepVerifier

class SFluxApplicativeErrorTest extends AnyFreeSpec {
  "SFlux" - {
    "should raise Error" in {
      StepVerifier.create(SFlux.catsInstances.raiseError(new RuntimeException))
        .expectError(classOf[RuntimeException])
        .verify()
    }
    "should handle error" in {
      StepVerifier.create(SFlux.catsInstances.handleErrorWith(SFlux.error(new RuntimeException))(t => SFlux.just(123)))
        .expectNext(123)
        .verifyComplete()
    }
  }
}
