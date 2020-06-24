package reactor.core.scala.publisher

import java.util.concurrent.TimeUnit
import java.util.function.Function

import org.reactivestreams.{Publisher, Subscription}
import reactor.core.publisher.{Flux => JFlux}
import reactor.core.scheduler.Schedulers
import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait SFluxLike[+T] extends ScalaConverters { self: SFlux[T] =>

  private[publisher] def coreFlux: JFlux[_ <: T]

  private def defaultToFluxError[U](t: Throwable): SFlux[U] = SFlux.error(t)

  final def doOnSubscribe(onSubscribe: Subscription => Unit): SFlux[T] = coreFlux.doOnSubscribe(onSubscribe).asScala

  final def drop(n: Long): SFlux[T] = skip(n)

  final def flatten[S](implicit ev: T <:< SFlux[S]): SFlux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)

  final def fold[R](initial: R)(binaryOps: (R, T) => R): SMono[R] = {
    coreFlux.reduce(initial, binaryOps).asScala
  }

  final def foldWith[R](initial: => R)(binaryOps: (R, T) => R): SMono[R] = {
    coreFlux.reduceWith(()=> initial, binaryOps).asScala
  }

  final def head: SMono[T] = coreFlux.next.asScala

  final def max[R >: T](implicit ev: Ordering[R]): SMono[Option[R]] = fold(None: Option[R]) { (acc: Option[R], el: T) => {
    acc map (a => ev.max(a, el)) orElse Option(el)
  }
  }

  final def min[R >: T](implicit ev: Ordering[R]): SMono[Option[R]] = fold(None: Option[R]) { (acc: Option[R], el: T) => {
    acc map (a => ev.min(a, el)) orElse Option(el)
  }
  }

  final def onErrorRecover[U >: T](pf: PartialFunction[Throwable, U]): SFlux[U] = {
    def recover(t: Throwable): SFlux[U] = pf.andThen(u => SFlux.just(u)).applyOrElse(t, defaultToFluxError)

    onErrorResume(recover)
  }

  final def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, SFlux[U]]): SFlux[U] = {
    def recover(t: Throwable): SFlux[U] = pf.applyOrElse(t, defaultToFluxError)
    onErrorResume(recover)
  }

  final def onErrorResume[U >: T](fallback: Throwable => Publisher[U]): SFlux[U] = {
    def f[P <: T]: Function[Throwable, Publisher[P]] = (t: Throwable) => fallback(t).asInstanceOf[Publisher[P]]

    coreFlux.onErrorResume(f).asScala
  }

  /**
    * Multiple all element within this [[SFlux]] given the type element is [[Numeric]]
    * @tparam R [[Numeric]]
    * @return [[SMono]] with the result of all element multiplied.
    */
  final def product[R >: T](implicit R: Numeric[R]): SMono[R] = {
    import R._
    fold(one)(_ * _)
  }

  final def sum[R >: T](implicit R: Numeric[R]): SMono[R] = {
    import R._
    fold(zero)(_ + _ )
  }

  /**
    * Alias for [[skip]](1)
    * @return
    */
  final def tail: SFlux[T] = skip(1)

  final def zipWithTimeSinceSubscribe(): SFlux[(T, Long)] = {
    val scheduler = Schedulers.single()
    var subscriptionTime: Long = 0
    doOnSubscribe(_ => subscriptionTime = scheduler.now(TimeUnit.MILLISECONDS))
      .map(t => (t, scheduler.now(TimeUnit.MILLISECONDS) - subscriptionTime))
  }

  /**
    * Alias for [[SFlux.concatWith]]
    * @param other the other [[Publisher]] sequence to concat after this [[SFlux]]
    * @return a concatenated [[SFlux]]
    */
  final def ++[U >: T](other: Publisher[U]): SFlux[U] = concatWith(other)
}
