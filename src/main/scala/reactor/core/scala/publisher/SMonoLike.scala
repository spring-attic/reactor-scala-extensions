package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.publisher.{Mono => JMono}

import scala.annotation.unchecked.uncheckedVariance
import scala.language.higherKinds

trait SMonoLike[+T] extends ScalaConverters {

  private[publisher] def coreMono: JMono[_ <: T]

  /**
    * Concatenate emissions of this [[SMono]] with the provided [[Publisher]]
    * (no interleave).
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat1.png" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[SFlux]]
    * @return a concatenated [[SFlux]]
    */
  final def concatWith[U >: T](other: Publisher[U]): SFlux[U] = {
    coreMono.concatWith(other.asInstanceOf[Publisher[Nothing]]).asScala
  }

  /**
    * Alias for [[SMono.concatWith]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat1.png" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[SMono]]
    * @return a concatenated [[SFlux]]
    */
  final def ++[U >: T](other: Publisher[U]): SFlux[U] = concatWith(other)
}
