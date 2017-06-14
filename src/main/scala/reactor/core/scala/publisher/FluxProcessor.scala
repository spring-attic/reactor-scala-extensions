package reactor.core.scala.publisher

import org.reactivestreams.{Processor, Publisher, Subscriber, Subscription}
import reactor.core.publisher.{FluxProcessor => JFluxProcessor, UnicastProcessor => JUnicastProcessor}

/**
  * A base processor that exposes [[Flux]] API for [[org.reactivestreams.Processor]].
  *
  * Implementors include [[reactor.core.publisher.UnicastProcessor]], [[reactor.core.publisher.EmitterProcessor]],
  * [[reactor.core.publisher.ReplayProcessor]], [[reactor.core.publisher.WorkQueueProcessor]] and [[reactor.core.publisher.TopicProcessor]].
  *
  * @tparam IN  the input value type
  * @tparam OUT the output value type
  */
trait FluxProcessor[IN, OUT] extends Flux[OUT] with Processor[IN, OUT] {

  protected def jFluxProcessor: JFluxProcessor[IN, OUT]

  override def onComplete(): Unit = jFluxProcessor.onComplete()

  override def onError(t: Throwable): Unit = jFluxProcessor.onError(t)

  override def onNext(t: IN): Unit = jFluxProcessor.onNext(t)

  override def onSubscribe(s: Subscription): Unit = jFluxProcessor.onSubscribe(s)
}

object FluxProcessor {

  /**
    * Build a [[FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]].
    * The [[Flux]] will complete once both the publishers source and the last switched to [[Publisher]] have
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
    wrap[Publisher[_ <: T], T](emitter, Flux.switchOnNext[T](emitter))
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
  def wrap[IN, OUT](upstream: Subscriber[IN], downstream: Publisher[OUT]) = {
    val jFluxProcessorWrapper: JFluxProcessor[IN, OUT] = JFluxProcessor.wrap(upstream, downstream)

    new Flux[OUT](jFluxProcessorWrapper) with FluxProcessor[IN, OUT] {
      override protected def jFluxProcessor: JFluxProcessor[IN, OUT] = jFluxProcessorWrapper
    }
  }
}
