package reactor.core.scala.publisher

import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{ParallelFlux => JParallelFlux, Flux => JFlux}

class SParallelFluxTest extends FreeSpec with Matchers {
  "SParallelFlux" - {
    ".asJava should convert as Java ParallelFlux" in {
      Flux.just(1, 2, 3).parallel().asJava shouldBe a[JParallelFlux[Int]]
    }

    ".apply should convert Java ParallelFlux into SParallelFlux" in {
      SParallelFlux(JFlux.just(1, 2, 3).parallel()).asJava shouldBe a[JParallelFlux[Int]]
    }
  }
}
