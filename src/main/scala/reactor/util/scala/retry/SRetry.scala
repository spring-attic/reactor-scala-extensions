package reactor.util.scala.retry

import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SFlux
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal

object SRetry {
  /**
    * A wrapper around [[Function1]] to provide [[Retry]] by using lambda expressions.
    *
    * @param function the { @link Function} representing the desired { @link Retry} strategy as a lambda
    * @return the { @link Retry} strategy adapted from the { @link Function}
    */
  final def from(function: SFlux[RetrySignal] => Publisher[_]): Retry = Retry.from(function)
}
