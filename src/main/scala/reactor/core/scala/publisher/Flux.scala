package reactor.core.scala.publisher

import java.lang.{Iterable => JIterable, Long => JLong}
import java.util.function.Function

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{FluxSink, Flux => JFlux}

import scala.concurrent.duration.Duration

/**
  * A Reactive Streams [[Publisher]] with rx operators that emits 0 to N elements, and then completes
  * (successfully or with an error).
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/flux.png" alt="">
  * <p>
  *
  * <p>It is intended to be used in implementations and return types. Input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>If it is known that the underlying [[Publisher]] will emit 0 or 1 element, [[Mono]] should be used
  * instead.
  *
  * <p>Note that using state in the lambdas used within Flux operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the element type of this Reactive Streams [[Publisher]]
  * @see [[Mono]]
  */
class Flux[T](private[publisher] val jFlux: JFlux[T]) extends Publisher[T] with MapablePublisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)

  def count(): Mono[Long] = Mono[Long](jFlux.count().map(new Function[JLong, Long] {
    override def apply(t: JLong) = Long2long(t)
  }))

  def take(n: Long) = new Flux[T](jFlux.take(n))

  def sample(duration: Duration) = new Flux[T](jFlux.sample(duration))

  override def map[U](mapper: (T) => U) = new Flux[U](jFlux.map(mapper))

  /**
    * Provide a default unique value if this sequence is completed without any data
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[Flux]]
    */
  final def defaultIfEmpty(defaultV: T) = new Flux[T](jFlux.defaultIfEmpty(defaultV))

  final def asJava(): JFlux[T] = jFlux
}

object Flux {

  private def apply[T](jFlux: JFlux[T]): Flux[T] = new Flux[T](jFlux)

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, sources: Publisher[_ <: T]*): Flux[V] = Flux(JFlux.combineLatest(combinator, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, prefetch: Int, sources: Publisher[_ <: T]*) = Flux(JFlux.combineLatest(combinator, prefetch, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, V](source1: Publisher[_ <: T1],
                               source2: Publisher[_ <: T2],
                               combinator: (T1, T2) => V) = Flux(JFlux.combineLatest[T1, T2, V](source1, source2, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, V](source1: Publisher[_ <: T1],
                                   source2: Publisher[_ <: T2],
                                   source3: Publisher[_ <: T3],
                                   combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, V](source1, source2, source3, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, V](source1: Publisher[_ <: T1],
                                       source2: Publisher[_ <: T2],
                                       source3: Publisher[_ <: T3],
                                       source4: Publisher[_ <: T4],
                                       combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, V](source1, source2, source3, source4, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, V](source1: Publisher[_ <: T1],
                                           source2: Publisher[_ <: T2],
                                           source3: Publisher[_ <: T3],
                                           source4: Publisher[_ <: T4],
                                           source5: Publisher[_ <: T5],
                                           combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, V](source1, source2, source3, source4, source5, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param source6    The sixth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, T6, V](source1: Publisher[_ <: T1],
                                               source2: Publisher[_ <: T2],
                                               source3: Publisher[_ <: T3],
                                               source4: Publisher[_ <: T4],
                                               source5: Publisher[_ <: T5],
                                               source6: Publisher[_ <: T6],
                                               combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, T6, V](source1, source2, source3, source4, source5, source6, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], prefetch: Int, combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, prefetch, combinator))

  /**
    * Concat all sources pulled from the supplied
    * [[Iterator]] on [[Publisher.subscribe]] from the passed [[Iterable]] until [[Iterator.hasNext]]
    * returns false. A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Iterable[Publisher[T]]) = Flux(JFlux.concat(sources))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]]): Flux[T] = Flux(JFlux.concat(sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concat(sources, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The array of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Publisher[T]*) = Flux(JFlux.concat(sources: _*))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]]) = Flux(JFlux.concatDelayError[T](sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concatDelayError(sources, prefetch))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    *
    * Errors will be delayed after the current concat backlog if delayUntilEnd is
    * false or after all sources if delayUntilEnd is true.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources       The [[Publisher]] of [[Publisher]] to concat
    * @param delayUntilEnd delay error until all sources have been consumed instead of
    *                      after the current source
    * @param prefetch      the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], delayUntilEnd: Boolean, prefetch: Int) = Flux(JFlux.concatDelayError(sources, delayUntilEnd, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * Any error will be delayed until all sources have been concatenated.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concatDelayError[T](sources: Publisher[T]*) = Flux(JFlux.concatDelayError(sources: _*))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    * <p>
    * Handles backpressure by buffering all signals if the downstream can't keep up.
    *
    * <pre><code>
    * Flux.String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * });
    * <code></pre>
    *
    * @tparam T the value type
    * @param emitter the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
  def create[T](emitter: FluxSink[T] => Unit) = Flux(JFlux.create[T](emitter))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    *
    * <pre><code>
    * Flux.&lt;String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * }, FluxSink.OverflowStrategy.LATEST);
    * <code></pre>
    *
    * @tparam T the value type
    * @param backpressure the backpressure mode, see { @link OverflowStrategy} for the
    *                                                        available backpressure modes
    * @param emitter the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
//  TODO: How to test backpressure?
  def create[T](emitter: FluxSink[T] => Unit, backpressure: OverflowStrategy) = Flux(JFlux.create[T](emitter, backpressure))

  /**
    * Supply a [[Publisher]] everytime subscribe is called on the returned flux. The passed [[scala.Function1[Unit,Publisher[T]]]]
    * will be invoked and it's up to the developer to choose to return a new instance of a [[Publisher]] or reuse
    * one effectively behaving like [[Flux.from]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defer.png" alt="">
    *
    * @param supplier the [[Publisher]] Supplier to call on subscribe
    * @tparam T the type of values passing through the [[Flux]]
    * @return a deferred [[Flux]]
    */
  def defer[T](supplier: () => Publisher[T]): Flux[T] = {
    Flux(JFlux.defer(supplier))
  }

  /**
	 * Create a [[Flux]] that completes without emitting any item.
	 * <p>
	 * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/empty.png" alt="">
	 * <p>
	 * @tparam T the reified type of the target [[Subscriber]]
	 *
	 * @return an empty [[Flux]]
	 */
  def empty[T](): Flux[T] = Flux(JFlux.empty[T]())

  /**
	 * Create a [[Flux]] that completes with the specified error.
	 * <p>
	 * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/error.png" alt="">
	 * <p>
	 * @param error the error to signal to each [[Subscriber]]
	 * @tparam T the reified type of the target [[Subscriber]]
	 *
	 * @return a new failed  [[Flux]]
	 */
  def error[T](error: Throwable): Flux[T] = Flux(JFlux.error[T](error))
  /**
    * Expose the specified [[Publisher]] with the [[Flux]] API.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/from.png" alt="">
    * <p>
    *
    * @param source the source to decorate
    * @tparam T the source sequence type
    * @return a new [[Flux]]
    */
  def from[T](source: Publisher[_ <: T]): Flux[T] = {
    new Flux[T](
      JFlux.from(source)
    )
  }

  def just[T](data: T*): Flux[T] = {
    new Flux[T](
      JFlux.just(data: _*)
    )
  }
}
