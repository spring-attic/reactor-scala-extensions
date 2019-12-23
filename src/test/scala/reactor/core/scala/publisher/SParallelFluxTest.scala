package reactor.core.scala.publisher

import org.mockito.IdiomaticMockito
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.publisher.{Flux => JFlux, ParallelFlux => JParallelFlux}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier

class SParallelFluxTest extends AnyFreeSpec with Matchers with IdiomaticMockito {
  "SParallelFlux" - {
    val data = Seq(1, 2, 3)
    val flux = SFlux.just[Int](data: _*)
    val fluxParallel: SParallelFlux[Int] = flux.parallel()
    ".asJava should convert as Java ParallelFlux" in {
      fluxParallel.asJava shouldBe a[JParallelFlux[_]]
    }

    ".apply should convert Java ParallelFlux into SParallelFlux" in {
      SParallelFlux(JFlux.just(1, 2, 3).parallel()).asJava shouldBe a[JParallelFlux[_]]
    }

    ".filter should filter elements" in {
      StepVerifier.create(fluxParallel.filter((i: Int) => i % 2 == 0))
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

    ".reduce should aggregate the values" - {
      "without initial supplier" in {
        val mono = fluxParallel.reduce(_ + _)
        StepVerifier.create(mono)
          .expectNext(6)
          .verifyComplete()
      }
      "with initial value should aggregate the values with initial one" ignore {
        val parallelFlux = fluxParallel.reduce[String](() => "0", (agg, v) => s"$agg-${v.toString}")
        StepVerifier.create(parallelFlux)
          .expectNext("0-1")
          .expectNext("0-2")
          .expectNext("0-3")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .expectNext("0")
          .verifyComplete()
      }
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

      scheduler.createWorker() wasCalled twice
    }
  }
}
