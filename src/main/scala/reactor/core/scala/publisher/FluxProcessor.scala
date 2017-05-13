package reactor.core.scala.publisher

import org.reactivestreams.{Processor, Publisher, Subscription}
import reactor.core.publisher.{FluxProcessor => JFluxProcessor}

/**
  * A base processor that exposes [[Flux]] API for [[org.reactivestreams.Processor]].
  *
  * Implementors include [[reactor.core.publisher.UnicastProcessor]], [[reactor.core.publisher.EmitterProcessor]],
  * [[reactor.core.publisher.ReplayProcessor]], [[reactor.core.publisher.WorkQueueProcessor]] and [[reactor.core.publisher.TopicProcessor]].
  *
  * @tparam IN the input value type
  * @tparam OUT the output value type
  */
class FluxProcessor[IN, OUT](private val jFluxProcessor: JFluxProcessor[IN, OUT]) extends Flux[OUT](jFluxProcessor) with Processor[IN, OUT]{
  override def onComplete(): Unit = jFluxProcessor.onComplete()

  override def onError(t: Throwable): Unit = jFluxProcessor.onError(t)

  override def onNext(t: IN): Unit = jFluxProcessor.onNext(t)

  override def onSubscribe(s: Subscription): Unit = jFluxProcessor.onSubscribe(s)
}

object FluxProcessor {

  private[publisher] def apply[IN, OUT](jFluxProcessor: JFluxProcessor[IN, OUT]) = new FluxProcessor[IN, OUT](jFluxProcessor)

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
  def switchOnNext[T](): FluxProcessor[Publisher[_ <: T], T] = apply[Publisher[_ <: T], T](JFluxProcessor.switchOnNext[T]())
}
