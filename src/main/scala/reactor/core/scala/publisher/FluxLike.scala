package reactor.core.scala.publisher

import reactor.util.concurrent.Queues.XS_BUFFER_SIZE

trait FluxLike[T] { self: Flux[T] =>
  /** $concatDescription
    *
    * Alias for [[concatMap]].
    *
    * @return $concatReturn
    */
  final def flatten[S](implicit ev: T <:< Flux[S]): Flux[S] = concatMap[S](x => ev(x), XS_BUFFER_SIZE)

  private def defaultToFluxError[U](t: Throwable): Flux[U] = Flux.error(t)

  /** Returns a Flux that mirrors the behavior of the source,
    * unless the source is terminated with an `onError`, in which
    * case the streaming of events fallbacks to an observable
    * emitting a single element generated by the backup function.
    *
    * The created Flux mirrors the behavior of the source
    * in case the source does not end with an error or if the
    * thrown `Throwable` is not matched.
    *
    * See [[onErrorResume]] for the version that takes a
    * total function as a parameter.
    *
    * @param pf - a function that matches errors with a
    *        backup element that is emitted when the source
    *        throws an error.
    */
  final def onErrorRecover[U <: T](pf: PartialFunction[Throwable, U]): Flux[T] = {
    def recover(t: Throwable): Flux[U] = pf.andThen(u => Flux.just(u)).applyOrElse(t, defaultToFluxError)
    onErrorResume(recover)
  }

  /** Returns a Flux that mirrors the behavior of the source,
    * unless the source is terminated with an `onError`, in which case
    * the streaming of events continues with the specified backup
    * sequence generated by the given function.
    *
    * The created Flux mirrors the behavior of the source in
    * case the source does not end with an error or if the thrown
    * `Throwable` is not matched.
    *
    * See [[onErrorResume]] for the version that takes a
    * total function as a parameter.
    *
    * @param pf is a function that matches errors with a
    *        backup throwable that is subscribed when the source
    *        throws an error.
    */
  final def onErrorRecoverWith[U <: T](pf: PartialFunction[Throwable, Flux[U]]): Flux[T] = {
    def recover(t: Throwable): Flux[U] = pf.applyOrElse(t, defaultToFluxError)
    onErrorResume(recover)
  }
}