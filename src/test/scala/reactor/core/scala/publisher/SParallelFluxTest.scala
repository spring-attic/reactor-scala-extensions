package reactor.core.scala.publisher

import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{Flux => JFlux, ParallelFlux => JParallelFlux}

class SParallelFluxTest extends FreeSpec with Matchers {
  "SParallelFlux" - {
    ".asJava should convert as Java ParallelFlux" in {
      SParallelFlux(JFlux.just(1, 2, 3).parallel()).asJava shouldBe a[JParallelFlux[Int]]
    }
  }
}
