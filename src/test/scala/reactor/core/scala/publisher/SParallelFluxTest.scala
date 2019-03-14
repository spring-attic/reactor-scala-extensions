package reactor.core.scala.publisher

import org.mockito.Mockito.{spy, times, verify}
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{Flux => JFlux, ParallelFlux => JParallelFlux}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier

class SParallelFluxTest extends FreeSpec with Matchers {
  "SParallelFlux" - {
    val data = Seq(1, 2, 3)
    val flux = Flux.just(data.head,data.tail:_*)
    val fluxParallel: SParallelFlux[Int] = flux.parallel()
    ".asJava should convert as Java ParallelFlux" in {
      fluxParallel.asJava shouldBe a[JParallelFlux[Int]]
    }

    ".apply should convert Java ParallelFlux into SParallelFlux" in {
      SParallelFlux(JFlux.just(1, 2, 3).parallel()).asJava shouldBe a[JParallelFlux[Int]]
    }

    ".filter should filter elements" in {
      StepVerifier.create(fluxParallel.filter((i:Int) => i % 2 == 0))
        .expectNext(2)
        .verifyComplete()
    }

    ".map should map from T to U" in {
      val expected = data.map(_.toString)
      StepVerifier.create(fluxParallel.map(i => i.toString))
        .expectNextMatches((i: String) => expected.contains(i))
        .expectNextMatches((i: String) => expected.contains(i))
        .expectNextMatches((i: String) => expected.contains(i))
        .verifyComplete()
    }

    ".sequential should merge the rails" in {
      val expected = data.map(_.toString)
      StepVerifier.create(fluxParallel.map(i => i.toString).sequential())
        .expectNextMatches((i: String) => expected.contains(i))
        .expectNextMatches((i: String) => expected.contains(i))
        .expectNextMatches((i: String) => expected.contains(i))
        .verifyComplete()
    }

    ".runOn should run on different thread" in {
      val scheduler = spy(Schedulers.parallel())
      StepVerifier.create(flux.parallel(2).runOn(scheduler))
        .expectNextMatches((i: Int) => data.contains(i))
        .expectNextMatches((i: Int) => data.contains(i))
        .expectNextMatches((i: Int) => data.contains(i))
        .verifyComplete()

      verify(scheduler, times(2)).createWorker()
    }
  }
}
