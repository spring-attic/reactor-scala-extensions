package reactor.core.scala.publisher

import java.util.function.Function

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux}
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait SFluxLike[T, Self[U] <: SFluxLike[U, Self]] {
  self: Self[T] =>

  final def collect[E](containerSupplier: () => E, collector: (E, T) => Unit): SMono[E] = new ReactiveSMono[E](coreFlux.collect(containerSupplier, collector: JBiConsumer[E, T]))

  final def concatMap[V](mapper: T => Publisher[_ <: V], prefetch: Int = XS_BUFFER_SIZE): SFlux[V] = new ReactiveSFlux[V](coreFlux.concatMap[V](mapper, prefetch))

  private[publisher] def coreFlux: JFlux[T]

  private def defaultToFluxError[U](t: Throwable): SFlux[U] = SFlux.raiseError(t)

  final def flatten[S](implicit ev: T <:< SFlux[S]): SFlux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)

  final def foldLeft[R](initial: R)(binaryOps: (R, T) => R): SMono[R] = reduce[R](initial, binaryOps)

  final def max[R >: T](implicit ev: Ordering[R]): SMono[Option[R]] = foldLeft(None: Option[R]) { (acc: Option[R], el: T) => {
    acc map (a => ev.max(a, el)) orElse Option(el)
  }
  }

  final def min[R >: T](implicit ev: Ordering[R]): SMono[Option[R]] = foldLeft(None: Option[R]) { (acc: Option[R], el: T) => {
    acc map (a => ev.min(a, el)) orElse Option(el)
  }
  }

  final def onErrorRecover[U <: T](pf: PartialFunction[Throwable, U]): SFlux[T] = {
    def recover(t: Throwable): SFlux[U] = pf.andThen(u => SFlux.just(u)).applyOrElse(t, defaultToFluxError)

    onErrorResume(recover)
  }

  final def onErrorRecoverWith[U <: T](pf: PartialFunction[Throwable, SFlux[U]]): SFlux[T] = {
    def recover(t: Throwable): SFlux[U] = pf.applyOrElse(t, defaultToFluxError)
    onErrorResume(recover)
  }

  final def onErrorResume[U <: T](fallback: Throwable => _ <: Publisher[_ <: U]): SFlux[U] = {
    val predicate = new Function[Throwable, Publisher[_ <: U]] {
      override def apply(t: Throwable): Publisher[_ <: U] = fallback(t)
    }
    val x: SFlux[T] = coreFlux.onErrorResume(predicate)
    x.as[SFlux[U]](t => t.map(u => u.asInstanceOf[U]))
  }

  final def reduce[A](initial: A, accumulator: (A, T) => A): SMono[A] = coreFlux.reduce[A](initial, accumulator)

  final def sum[R >: T](implicit R: Numeric[R]): SMono[R] = {
    import R._
    foldLeft(R.zero) { (acc: R, el: T) => acc + el }
  }
}
