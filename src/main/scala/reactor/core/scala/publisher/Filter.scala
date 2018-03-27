package reactor.core.scala.publisher

import org.reactivestreams.Publisher

trait Filter[T] { self: Publisher[T] =>
  /**
    * Evaluate each accepted value against the given predicate T => Boolean. If the predicate test succeeds, the value is
    * passed into the new [[Publisher]]. If the predicate test fails, the value is ignored and a request of 1 is
    * emitted.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/filter.png" alt="">
    *
    * @param f the [[Function1]] predicate to test values against
    * @return a new [[Publisher]] containing only values that pass the predicate test
    */
  def filter(f: T => Boolean): Publisher[T]
}
