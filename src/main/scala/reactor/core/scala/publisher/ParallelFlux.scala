package reactor.core.scala.publisher

import reactor.core.publisher.{ParallelFlux => JParallelFlux}

class ParallelFlux[T]private (private val jParallelFlux: JParallelFlux[T]) {

}

object ParallelFlux {
  def apply[T](jParallelFlux: JParallelFlux[T]) = new ParallelFlux[T](jParallelFlux)
}
