package reactor.core.scala.publisher

import org.mockito.Mockito
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{Flux => JFlux, ParallelFlux => JParallelFlux}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier

class SParallelFluxTest extends FreeSpec with Matchers {
  "SParallelFlux" - {
    val data = Seq(1, 2, 3)
    val flux = Flux.just(data.head,data.tail:_*)
    ".asJava should convert as Java ParallelFlux" in {
      flux.parallel().asJava shouldBe a[JParallelFlux[Int]]
    }

    ".apply should convert Java ParallelFlux into SParallelFlux" in {
      SParallelFlux(JFlux.just(1, 2, 3).parallel()).asJava shouldBe a[JParallelFlux[Int]]
    }

    ".runOn should run on different thread" in {
      val scheduler = Mockito.spy(Schedulers.parallel())
      StepVerifier.create(flux.parallel(2).runOn(scheduler))
        .expectNextMatches(i => data.contains(i))
        .expectNextMatches(i => data.contains(i))
        .expectNextMatches(i => data.contains(i))
        .verifyComplete()

      Mockito.verify(scheduler, Mockito.times(2)).createWorker()
    }
  }
}
