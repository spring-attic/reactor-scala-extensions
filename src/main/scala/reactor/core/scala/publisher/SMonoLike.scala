package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.publisher.{Mono => JMono}

import scala.language.higherKinds

trait SMonoLike[T, Self[U] <: SMonoLike[U, Self]] extends ScalaConverters {
  self: Self[T] =>


  private[publisher] def coreMono: JMono[T]

  /**
    * Concatenate emissions of this [[SMono]] with the provided [[Publisher]]
    * (no interleave).
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat1.png" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[SFlux]]
    * @return a concatenated [[SFlux]]
    */
  final def concatWith(other: Publisher[T]): SFlux[T] = coreMono.concatWith(other).asScala

  /**
    * Alias for [[SMono.concatWith]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat1.png" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[SMono]]
    * @return a concatenated [[SFlux]]
    */
  final def ++(other: Publisher[T]): SFlux[T] = concatWith(other)
}
