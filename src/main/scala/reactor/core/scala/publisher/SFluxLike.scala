package reactor.core.scala.publisher

import java.util.concurrent.TimeUnit
import java.util.function.Function

import org.reactivestreams.{Publisher, Subscription}
import reactor.core.publisher.{Flux => JFlux}
import reactor.core.scheduler.Schedulers
import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

import scala.language.higherKinds

trait SFluxLike[T, Self[U] <: SFluxLike[U, Self]] extends ScalaConverters {
  self: Self[T] =>

  final def collect[E](containerSupplier: () => E, collector: (E, T) => Unit): SMono[E] = new ReactiveSMono[E](coreFlux.collect(containerSupplier, collector: JBiConsumer[E, T]))

  final def concatMap[V](mapper: T => Publisher[_ <: V], prefetch: Int = XS_BUFFER_SIZE): SFlux[V] = new ReactiveSFlux[V](coreFlux.concatMap[V](mapper, prefetch))

  /**
    * Concatenate emissions of this [[SFlux]] with the provided [[Publisher]] (no interleave).
    * <p>
    * <img class="marble" src="https://github.com/reactor/reactor-core/tree/master/reactor-core/src/main/java/reactor/core/publisher/doc-files/marbles/concatWithForFlux.svg" alt="">
    *
    * @param other the [[Publisher]] sequence to concat after this [[SFlux]]
    * @return a concatenated [[SFlux]]
    */
  final def concatWith(other: Publisher[_ <: T]): SFlux[T] = SFlux.fromPublisher(coreFlux.concatWith(other))

  private[publisher] def coreFlux: JFlux[T]

  private def defaultToFluxError[U](t: Throwable): SFlux[U] = SFlux.raiseError(t)

  final def doOnSubscribe(onSubscribe: Subscription => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnSubscribe(onSubscribe))

  final def drop(n: Long): SFlux[T] = skip(n)

  final def flatten[S](implicit ev: T <:< SFlux[S]): SFlux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)

  final def fold[R](initial: R)(binaryOps: (R, T) => R): SMono[R] = reduce(initial)(binaryOps)

  final def foldLeft[R](initial: R)(binaryOps: (R, T) => R): SMono[R] = reduce[R](initial)(binaryOps)

  final def head: SMono[T] = take(1).as(SMono.fromPublisher)

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
    val x: SFlux[T] = coreFlux.onErrorResume(predicate).asScala
    x.as[SFlux[U]](t => t.map(u => u.asInstanceOf[U]))
  }

  final def reduce[A](initial: A)(accumulator: (A, T) => A): SMono[A] = coreFlux.reduce[A](initial, accumulator).asScala

  final def skip(skipped: Long): SFlux[T] = coreFlux.skip(skipped).asScala

  final def sum[R >: T](implicit R: Numeric[R]): SMono[R] = {
    import R._
    foldLeft(R.zero) { (acc: R, el: T) => acc + el }
  }

  /**
    * Alias for [[skip]](1)
    * @return
    */
  final def tail: SFlux[T] = skip(1)

  final def take(n: Long): SFlux[T] = new ReactiveSFlux[T](coreFlux.take(n))

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
  final def ++(other: Publisher[_ <: T]): SFlux[T] = concatWith(other)
}
