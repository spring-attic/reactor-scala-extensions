package reactor.core.scala.publisher

import java.util
import java.util.function.Supplier

import org.reactivestreams.Publisher
import reactor.core.publisher.{ParallelFlux => JParallelFlux}
import reactor.util.concurrent.Queues

class SParallelFlux[T] private(private val jParallelFlux: JParallelFlux[T]) {

  /**
    * Perform a fluent transformation to a value via a converter function which receives
    * this ParallelFlux.
    *
    * @tparam U the output value type
    * @param converter the converter function from [[SParallelFlux]] to some type
    * @return the value returned by the converter function
    */
  final def as[U](converter: SParallelFlux[T] => U): U = jParallelFlux.as((t: JParallelFlux[T]) => converter(SParallelFlux(t)))

  def asJava: JParallelFlux[T] = jParallelFlux
}

object SParallelFlux {
  def apply[T](jParallelFlux: JParallelFlux[T]) = new SParallelFlux[T](jParallelFlux)

  /**
    * Take a Publisher and prepare to consume it on multiple 'rails' (one per CPU core)
    * in a round-robin fashion.
    *
    * @tparam T the value type
    * @param source        the source Publisher
    * @param parallelism   the number of parallel rails
    * @param prefetch      the number of values to prefetch from the source
    * @param queueSupplier the queue structure supplier to hold the prefetched values
    *                      from the source until there is a rail ready to process it.
    * @return the [[SParallelFlux]] instance
    */
  def from[T](source: Publisher[_ <: T],
              parallelism: Int = Runtime.getRuntime.availableProcessors(),
              prefetch: Int = Queues.SMALL_BUFFER_SIZE,
              queueSupplier: Supplier[util.Queue[T]] = Queues.small()) = SParallelFlux(JParallelFlux.from(source, parallelism, prefetch, queueSupplier))

  /**
    * Wraps multiple Publishers into a [[SParallelFlux]] which runs them in parallel and
    * unordered.
    *
    * @tparam T the value type
    * @param publishers the array of publishers
    * @return the [[SParallelFlux]] instance
    */
  def fromPublishers[T](publishers: Publisher[T]*) = SParallelFlux(JParallelFlux.from(publishers: _*))
}
