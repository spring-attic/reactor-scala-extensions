package reactor.core.scala.publisher

import org.reactivestreams.Publisher
import reactor.core.publisher.{Mono => JMono}

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait SMonoLike[+T] extends ScalaConverters { self: SMono[T] =>

  private[publisher] def coreMono: JMono[_ <: T]

  final def using[R](use: T => SMono[R])(release: T => Unit): SMono[R] = usingWhen(use)((t, _) => release(t))

  final def usingWhen[R](use: T => SMono[R])(release: (T, ExitCondition) => Unit): SMono[R] = {
    val f: T => SMono[R] = (t: T) => (Try(use(t)) match {
      case Success(value) => value.doOnSuccess(_ => release(t, Completed))
      case Failure(exception) => SMono.error(exception)
    }).doOnError(ex => release(t, Error(ex)))
        .doOnCancel(() => release(t, Cancelled))
    flatMap(f)
  }

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

  /**
    * Adding element of this [[SMono]] with the element of the other one.
    * @param other The other element
    * @tparam R [[Numeric]]
    * @return [[SMono]] containing the sum of this and the other one.
    */
  final def +[R >: T](other: SMono[R])(implicit R: Numeric[R]): SMono[R] = concatWith(other).sum

  /**
    * Subtract element in this [[SMono]] with the element of the other one
    * @param other The other eleent
    * @tparam R [[Numeric]]
    * @return [[SMono]] containing the result of subtraction.
    */
  final def -[R >: T](other: SMono[R])(implicit R: Numeric[R]): SMono[R] = {
    import R._
    concatWith(other).reduce((a, b) => a - b)
  }

  /**
    * Multiply element of this [[SMono]] with element of the other one.
    * @param other The other element
    * @tparam R [[Numeric]]
    * @return [[SMono]] containing the product of this and the other one.
    */
  final def *[R >: T](other: SMono[R])(implicit R: Numeric[R]): SMono[R] = concatWith(other).product
}
