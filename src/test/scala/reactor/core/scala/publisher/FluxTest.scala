package reactor.core.scala.publisher

import org.scalatest.FreeSpec
import reactor.test.StepVerifier

/**
  * Created by winarto on 1/10/17.
  */
class FluxTest extends FreeSpec {
  "Flux" - {
    ".just" - {
      "with varargs should emit values from provided data" in {
        val flux = Flux.just(1, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2)
          .verifyComplete()
      }
    }

    ".count should return Mono which emit the number of value in this flux" in {
      val mono = Flux.just(10, 9, 8).count()
      StepVerifier.create(mono)
        .expectNext(3)
        .verifyComplete()
    }
  }
}
