package reactor.core.scala.publisher

import reactor.core.publisher.{Flux => JFlux}
import scala.jdk.StreamConverters._

trait VersionedSFluxCompanion {

  /**
    * Create a [[SFlux]] that emits the items contained in the provided [[LazyList]].
    * Keep in mind that a [[LazyList]] cannot be re-used, which can be problematic in
    * case of multiple subscriptions or re-subscription (like with [[SFlux.repeat()]] or
    * [[SFlux.retryWhen()]]). The underlying stream  is closed automatically
    * by the operator on cancellation, error or completion.
    * <p>
    * <img class="marble" src="doc-files/marbles/fromStream.svg" alt="">
    *
    * @reactor.discard Upon cancellation, this operator attempts to discard remainder of the
    *                  [[LazyList]] through its open { @link Spliterator}, if it can safely ensure it is finite
    *                  (see [[reactor.core.publisher.Operators.onDiscardMultiple(Iterator, boolean, Context)]]).
    * @param s the [[LazyList]] to read data from
    * @tparam T The type of values in the source [[LazyList]] and resulting Flux
    * @return a new [[SFlux]]
    */
  def fromLazyList[T](s: () => LazyList[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](s().asJavaSeqStream))
}
