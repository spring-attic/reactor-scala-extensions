package reactor.core.scala.publisher

import org.reactivestreams.{Processor, Publisher, Subscriber, Subscription}
import reactor.core
import reactor.core.Disposable
import reactor.core.Scannable.Attr
import reactor.core.publisher.{FluxSink, FluxProcessor => JFluxProcessor, UnicastProcessor => JUnicastProcessor}
import reactor.core.scala.Scannable
import scala.jdk.CollectionConverters._

/**
  * A base processor that exposes [[SFlux]] API for [[org.reactivestreams.Processor]].
  *
  * Implementors include [[reactor.core.publisher.UnicastProcessor]], [[reactor.core.publisher.EmitterProcessor]],
  * [[reactor.core.publisher.ReplayProcessor]], [[reactor.core.publisher.WorkQueueProcessor]] and [[reactor.core.publisher.TopicProcessor]].
  *
  * @tparam IN  the input value type
  * @tparam OUT the output value type
  */
trait FluxProcessor[IN, OUT] extends SFlux[OUT] with Processor[IN, OUT] with Disposable with Scannable {

  protected def jFluxProcessor: JFluxProcessor[IN, OUT]

  /**
    * Return the number of active [[Subscriber]] or `-1` if untracked.
    *
    * @return the number of active [[Subscriber]] or `-1` if untracked
    */
  def downstreamCount: Long = jFluxProcessor.downstreamCount()

  /**
    * Return the processor buffer capacity if any or [[Int.MaxValue]]
    *
    * @return processor buffer capacity if any or [[Int.MaxValue]]
    */
  def bufferSize(): Int = jFluxProcessor.getBufferSize

  /**
    * Current error if any, default to [[None]]
    *
    * @return Current error if any, default to [[None]]
    */
  def error = Option(jFluxProcessor.getError)

  /**
    * Return true if any [[Subscriber]] is actively subscribed
    *
    * @return true if any [[Subscriber]] is actively subscribed
    */
  def hasDownstreams: Boolean = jFluxProcessor.hasDownstreams

  /**
    * Return true if terminated with onComplete
    *
    * @return true if terminated with onComplete
    */
  def hasCompleted: Boolean = jFluxProcessor.hasCompleted

  /**
    * Return true if terminated with onError
    *
    * @return true if terminated with onError
    */
  def hasError: Boolean = jFluxProcessor.hasError

  override def inners(): Stream[_ <: Scannable] = jFluxProcessor.inners().iterator().asScala.map(js=> js: Scannable).toStream

  /**
    * Has this upstream finished or "completed" / "failed" ?
    *
    * @return has this upstream finished or "completed" / "failed" ?
    */
  def isTerminated: Boolean = jFluxProcessor.isTerminated

  /**
    * Return true if this [[FluxProcessor]] supports multithread producing
    *
    * @return true if this [[FluxProcessor]] supports multithread producing
    */
  def isSerialized: Boolean = jFluxProcessor.isSerialized

  override def scanUnsafe(key: Attr[_]): Option[AnyRef] = Option(jFluxProcessor.scanUnsafe(key))

  /**
    * Create a [[FluxProcessor]] that safely gates multi-threaded producer
    *
    * @return a serializing [[FluxProcessor]]
    */
  final def serialize(): FluxProcessor[IN, OUT] = new ReactiveSFlux[OUT](jFluxProcessor) with FluxProcessor[IN, OUT] {
    override protected def jFluxProcessor: JFluxProcessor[IN, OUT] = jFluxProcessor.serialize()

    override val jScannable: core.Scannable = jFluxProcessor
  }

  /**
    * Create a [[FluxSink]] that safely gates multi-threaded producer
    * [[Subscriber.onNext]].
    *
    * <p> The returned [[FluxSink]] will not apply any
    * [[FluxSink.OverflowStrategy]] and overflowing [[FluxSink.next]]
    * will behave in two possible ways depending on the Processor:
    * <ul>
    * <li> an unbounded processor will handle the overflow itself by dropping or
    * buffering </li>
    * <li> a bounded processor will block/spin</li>
    * </ul>
    *
    * @return a serializing [[FluxSink]]
    */
  final def sink(): FluxSink[IN] = jFluxProcessor.sink()

  /**
    * Create a [[FluxSink]] that safely gates multi-threaded producer
    * [[Subscriber.onNext]].
    *
    * <p> The returned [[FluxSink]] will not apply any
    * [[FluxSink.OverflowStrategy]] and overflowing [[FluxSink.next]]
    * will behave in two possible ways depending on the Processor:
    * <ul>
    * <li> an unbounded processor will handle the overflow itself by dropping or
    * buffering </li>
    * <li> a bounded processor will block/spin on IGNORE strategy, or apply the
    * strategy behavior</li>
    * </ul>
    *
    * @param strategy the overflow strategy, see [[FluxSink.OverflowStrategy]]
    *                                                    for the
    *                                                    available strategies
    * @return a serializing [[FluxSink]]
    */
  final def sink(strategy: FluxSink.OverflowStrategy): FluxSink[IN] = jFluxProcessor.sink(strategy)

  /**
    * Returns serialization strategy. If true, [[FluxProcessor.sink()]] will always
    * be serialized. Otherwise sink is serialized only if [[FluxSink.onRequest(Long => Unit)]]
    * is invoked.
    *
    * @return true to serialize any sink, false to delay serialization till onRequest
    */
  protected def serializeAlways = true

  override def dispose(): Unit = jFluxProcessor.dispose()

  override def onComplete(): Unit = jFluxProcessor.onComplete()

  override def onError(t: Throwable): Unit = jFluxProcessor.onError(t)

  override def onNext(t: IN): Unit = jFluxProcessor.onNext(t)

  override def onSubscribe(s: Subscription): Unit = jFluxProcessor.onSubscribe(s)

  override def subscribe(s: Subscriber[_ >: OUT]): Unit = jFluxProcessor.subscribe(s)
}

object FluxProcessor {

  /**
    * Build a [[FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]].
    * The [[SFlux]] will complete once both the publishers source and the last switched to [[Publisher]] have
    * completed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/switchonnext.png" alt="">
    *
    * @tparam T the produced type
    * @return a [[FluxProcessor]] accepting publishers and producing T
    */
  def switchOnNext[T](): FluxProcessor[Publisher[_ <: T], T] = {
    val emitter = new UnicastProcessor[Publisher[_ <: T]](JUnicastProcessor.create())
    wrap[Publisher[_ <: T], T](emitter, SFlux.switchOnNext[T](emitter))
  }

  /**
    * Transform a receiving [[Subscriber]] and a producing [[Publisher]] in a logical [[FluxProcessor]].
    * The link between the passed upstream and returned downstream will not be created automatically, e.g. not
    * subscribed together. A [[Processor]] might choose to have orthogonal sequence input and output.
    *
    * @tparam IN  the receiving type
    * @tparam OUT the producing type
    * @param upstream   the upstream subscriber
    * @param downstream the downstream publisher
    * @return a new blackboxed [[FluxProcessor]]
    */
  def wrap[IN, OUT](upstream: Subscriber[IN], downstream: Publisher[OUT]): FluxProcessor[IN, OUT] = {
    val jFluxProcessorWrapper: JFluxProcessor[IN, OUT] = JFluxProcessor.wrap(upstream, downstream)

    new ReactiveSFlux[OUT](jFluxProcessorWrapper) with FluxProcessor[IN, OUT] {
      override protected def jFluxProcessor: JFluxProcessor[IN, OUT] = jFluxProcessorWrapper

      override val jScannable: core.Scannable = jFluxProcessor
    }
  }
}
