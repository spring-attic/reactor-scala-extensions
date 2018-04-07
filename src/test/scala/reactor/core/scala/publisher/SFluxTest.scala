package reactor.core.scala.publisher

import org.scalatest.FreeSpec
import reactor.core.publisher.FluxSink
import reactor.test.StepVerifier

class SFluxTest extends FreeSpec {
  "SFlux" - {
    ".apply should return a proper SFlux" in {
      StepVerifier.create(SFlux(1, 2, 3))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".combineLatest" - {
      "of two should combine two publishers into single SFlux that emit tuple2" in {
        StepVerifier.create(SFlux.combineLatest(Mono.just(1), Mono.just(2)))
          .expectNext((1, 2))
          .verifyComplete()
      }
      "of many should combine all of them into single SFlux that emit Seq" in {
        val flux = SFlux.combineLatest(Mono.just(1), Mono.just(2), Mono.just(3), Mono.just(4))
        StepVerifier.create(flux)
          .expectNext(Seq(1, 2, 3, 4))
          .verifyComplete()
      }
    }

    ".combineLatestMap" - {
      "of two should combine two publishers into single SFlux and apply mapper" in {
        StepVerifier.create(SFlux.combineLatestMap(Mono.just(1), Mono.just(2), (i: Int, j: Int) => s"$i-$j"))
          .expectNext("1-2")
          .verifyComplete()
      }
      "of many should combine them into single SFlux and apply mapper" in {
        val flux = SFlux.combineLatestMap((array: Array[Int]) => s"${array(0)}-${array(1)}-${array(2)}", SFlux(1, 2), SFlux(10, 20), SFlux(100, 200))
        StepVerifier.create(flux)
          .expectNext("2-20-100")
          .expectNext("2-20-200")
          .verifyComplete()
      }
    }

    ".concat" - {
      "with varargs of publisher should concatenate the underlying publisher" in {
        val flux = SFlux.concat(Flux.just(1, 2, 3), Mono.just(3), Flux.just(3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 3, 3, 4)
          .verifyComplete()
      }
    }

    ".concatDelayError" - {
      "with varargs of publishers should concatenate all sources emitted from parents" in {
        val flux = SFlux.concatDelayError[Int](Mono.just(1), Mono.just(2), Mono.just(3))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".create should create a flux" in {
      val flux = SFlux.create[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".empty should return an empty SFlux" in {
      StepVerifier.create(SFlux.empty)
        .verifyComplete()
    }

    ".push should create a flux" in {
      val flux = SFlux.push[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.next(2)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1, 2)
        .verifyComplete()
    }
  }
}
