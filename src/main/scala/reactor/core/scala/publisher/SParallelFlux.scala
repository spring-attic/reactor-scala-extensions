package reactor.core.scala.publisher

import java.util
import java.util.function.Supplier

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.Disposable
import reactor.core.publisher.{ParallelFlux => JParallelFlux}
import reactor.core.scheduler.Scheduler
import reactor.util.concurrent.Queues

class SParallelFlux[T] private(private val jParallelFlux: JParallelFlux[T]) extends Publisher[T] with ScalaConverters {

  /**
    * Perform a fluent transformation to a value via a converter function which receives
    * this ParallelFlux.
    *
    * @tparam U the output value type
    * @param converter the converter function from [[SParallelFlux]] to some type
    * @return the value returned by the converter function
    */
  final def as[U](converter: SParallelFlux[T] => U): U = jParallelFlux.as((t: JParallelFlux[T]) => converter(SParallelFlux(t)))

  /**
    * Filters the source values on each 'rail'.
    * <p>
    * Note that the same predicate may be called from multiple threads concurrently.
    *
    * @param predicate the function returning true to keep a value or false to drop a
    *                  value
    * @return the new [[SParallelFlux]] instance
    */
  final def filter(predicate: SPredicate[T]) = SParallelFlux(jParallelFlux.filter(predicate))

  /**
    * Maps the source values on each 'rail' to another value.
    * <p>
    * Note that the same mapper function may be called from multiple threads
    * concurrently.
    *
    * @tparam U the output value type
    * @param mapper the mapper function turning Ts into Us.
    * @return the new [[SParallelFlux]] instance
    */
  final def map[U](mapper: T => _ <: U) = SParallelFlux(jParallelFlux.map[U](mapper))

  /**
    * Reduces all values within a 'rail' and across 'rails' with a reducer function into
    * a single sequential value.
    * <p>
    * Note that the same reducer function may be called from multiple threads
    * concurrently.
    *
    * @param reducer the function to reduce two values into one.
    * @return the new Mono instance emitting the reduced value or empty if the
    *         [[SParallelFlux]] was empty
    */
  final def reduce(reducer: (T, T) => T):SMono[T] = jParallelFlux.reduce(reducer).asScala

  /**
    * Reduces all values within a 'rail' to a single value (with a possibly different
    * type) via a reducer function that is initialized on each rail from an
    * initialSupplier value.
    * <p>
    * Note that the same mapper function may be called from multiple threads
    * concurrently.
    *
    * @tparam R the reduced output type
    * @param initialSupplier the supplier for the initial value
    * @param reducer         the function to reduce a previous output of reduce (or the initial
    *                        value supplied) with a current source value.
    * @return the new [[SParallelFlux]] instance
    */
  final def reduce[R](initialSupplier: () => R, reducer: (R, T) => R) = SParallelFlux(jParallelFlux.reduce[R](initialSupplier, reducer))

  /**
    * Specifies where each 'rail' will observe its incoming values with possibly
    * work-stealing and a given prefetch amount.
    * <p>
    * This operator uses the default prefetch size returned by [[Queues.SMALL_BUFFER_SIZE]].
    * <p>
    * The operator will call [[Scheduler.createWorker()]] as many times as this
    * ParallelFlux's parallelism level is.
    * <p>
    * No assumptions are made about the Scheduler's parallelism level, if the Scheduler's
    * parallelism level is lower than the ParallelFlux's, some rails may end up on
    * the same thread/worker.
    * <p>
    * This operator doesn't require the Scheduler to be trampolining as it does its own
    * built-in trampolining logic.
    *
    * @param scheduler the scheduler to use that rail's worker has run out of work.
    * @param prefetch  the number of values to request on each 'rail' from the source
    * @return the new [[SParallelFlux]] instance
    */
  final def runOn(scheduler: Scheduler, prefetch: Int = Queues.SMALL_BUFFER_SIZE) = SParallelFlux(jParallelFlux.runOn(scheduler, prefetch))


  /**
    * Merges the values from each 'rail' in a round-robin or same-order fashion and
    * exposes it as a regular Publisher sequence, running with a give prefetch value for
    * the rails.
    *
    * @param prefetch the prefetch amount to use for each rail
    * @return the new Flux instance
    */
  final def sequential(prefetch: Int = Queues.SMALL_BUFFER_SIZE): SFlux[T] = jParallelFlux.sequential(prefetch).asScala

  /**
    * Subscribes to this [[SParallelFlux]] by providing an onNext, onError,
    * onComplete and onSubscribe callback and triggers the execution chain for all
    * 'rails'.
    *
    * @param onNext      consumer of onNext signals
    * @param onError     consumer of error signal
    * @param onComplete  callback on completion signal
    * @param onSubscribe consumer of the subscription signal
    */
  final def subscribe(onNext: Option[T => Unit] = None,
                      onError: Option[Throwable => Unit] = None,
                      onComplete: Option[() => Unit] = None,
                      onSubscribe: Option[Subscription => Unit] = None): Disposable = (onNext, onError, onComplete, onSubscribe) match {
                        case (Some(fn), Some(fe), Some(fc), Some(fs)) => jParallelFlux.subscribe(fn, fe, fc, fs)
                        case (Some(fn), Some(fe), Some(fc), None) => jParallelFlux.subscribe(fn, fe, fc)
                        case (Some(fn), Some(fe), None, Some(fs)) => jParallelFlux.subscribe(fn, fe, null, fs)
                        case (Some(fn), Some(fe), None, None) => jParallelFlux.subscribe(fn, fe)
                        case (Some(fn), None, Some(fe), Some(fs)) => jParallelFlux.subscribe(fn, null, fe, fs)
                        case (Some(fn), None, Some(fe), None) => jParallelFlux.subscribe(fn, null, fe)
                        case (Some(fn), None, None, Some(fs)) => jParallelFlux.subscribe(fn, null, null, fs)
                        case (Some(fn), None, None, None) => jParallelFlux.subscribe(fn)
                        case (None, Some(fe), Some(fc), Some(fs)) => jParallelFlux.subscribe(null, fe, fc, fs)
                        case (None, Some(fe), Some(fc), None) => jParallelFlux.subscribe(null, fe, fc)
                        case (None, Some(fe), None, Some(fs)) => jParallelFlux.subscribe(null, fe, null, fs)
                        case (None, Some(fe), None, None) => jParallelFlux.subscribe(null, fe)
                        case (None, None, Some(fc), Some(fs)) => jParallelFlux.subscribe(null, null, fc, fs)
                        case (None, None, Some(fc), None) => jParallelFlux.subscribe(null, null, fc)
                        case (None, None, None, Some(fs)) => jParallelFlux.subscribe(null, null, null, fs)
                        case (None, None, None, None) => jParallelFlux.subscribe()
                      }

  /**
    * Merge the rails into a [[SFlux.sequential]] Flux and
    * [[SFlux#subscribe(Subscriber) subscribe]] to said Flux.
    *
    * @param s the subscriber to use on [[SParallelFlux#sequential()]] Flux
    */
  override def subscribe(s: Subscriber[_ >: T]): Unit = jParallelFlux.subscribe(s)

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
