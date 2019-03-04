package reactor.core.scala.publisher

import reactor.core.publisher.{ParallelFlux => JParallelFlux}

class SParallelFlux[T]private(private val jParallelFlux: JParallelFlux[T]) {
  def asJava: JParallelFlux[T] = jParallelFlux
}

object SParallelFlux {
  def apply[T](jParallelFlux: JParallelFlux[T]) = new SParallelFlux[T](jParallelFlux)
}
