package reactor.core.scala.publisher

import java.util.concurrent.atomic.AtomicReference

import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux => JFlux}
import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait SFluxLike[T, Self[U] <: SFluxLike[U, Self]] {
  self: Self[T] =>

  final def collect[E](containerSupplier: () => E, collector: (E, T) => Unit): SMono[E] = new ReactiveSMono[E](coreFlux.collect(containerSupplier, collector: JBiConsumer[E, T]))

  final def concatMap[V](mapper: T => Publisher[_ <: V], prefetch: Int = XS_BUFFER_SIZE): SFlux[V] = new ReactiveSFlux[V](coreFlux.concatMap[V](mapper, prefetch))

  private[publisher] def coreFlux: JFlux[T]

  final def flatten[S](implicit ev: T <:< SFlux[S]): SFlux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)

  final def foldLeft[R](initial: R)(binaryOps: (R, T) => R): SMono[R] = {
    val acc = new AtomicReference[R](initial)
    collect(() => acc, (acc: AtomicReference[R], el: T) => {
      val newAcc = binaryOps(acc.get(), el)
      acc.set(newAcc)
    })
      .map(ar => ar.get())
  }

  final def sum[R >: T](implicit R: Numeric[R]): SMono[R] = {
    import R._
    foldLeft(R.zero){(acc: R, el: T) => acc + el}
  }
}
