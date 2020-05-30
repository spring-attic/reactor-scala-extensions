package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.concurrent.{Callable, CompletableFuture}
import java.util.function.{Consumer, Function, Supplier}
import java.util.logging.Level

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.publisher.{MonoSink, Signal, SignalType, SynchronousSink, Flux => JFlux, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.PimpMyPublisher._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.core.{Disposable, Scannable => JScannable}
import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE
import reactor.util.context.Context
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * A Reactive Streams [[Publisher]] with basic rx operators that completes successfully by emitting an element, or
  * with an error.
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mono.png" alt="">
  * <p>
  *
  * <p>The rx operators will offer aliases for input [[SMono]] type to preserve the "at most one"
  * property of the resulting [[SMono]]. For instance [[SMono.flatMap flatMap]] returns a [[SFlux]] with
  * possibly
  * more than 1 emission. Its alternative enforcing [[SMono]] input is [[SMono.`then` then]].
  *
  * <p>`SMono[Unit]` should be used for [[Publisher]] that just completes without any value.
  *
  * <p>It is intended to be used in implementations and return types, input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>Note that using state in the `scala.Function` / lambdas used within Mono operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the type of the single value of this class
  * @see [[SFlux]]
  */
trait SMono[T] extends SMonoLike[T, SMono] with MapablePublisher[T] with ScalaConverters {
  self =>

  /**
    * Join the termination signals from this mono and another source into the returned
    * void mono
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/and.png" alt="">
    * <p>
    *
    * @param other the [[Publisher]] to wait for
    *              complete
    * @return a new combined [[SMono]]
    * @see [[SMono.when]]
    */
  final def and(other: Publisher[_]): SMono[Unit] = {
    new ReactiveSMono(coreMono.and(other match {
      case f: SFlux[_] => f.coreFlux
      case m: SMono[_] => m.coreMono
    })) map[Unit] (_ => ())
  }

  /**
    * Transform this [[SMono]] into a target type.
    *
    * `mono.as(Flux::from).subscribe()`
    *
    * @param transformer the { @link Function} applying this { @link Mono}
    * @tparam P the returned instance type
    * @return the transformed [[SMono]] to instance P
    * @see [[SMono.compose]] for a bounded conversion to [[org.reactivestreams.Publisher]]
    */
  final def as[P](transformer: SMono[T] => P): P = transformer(this)

  /**
    * Get the underlying [[reactor.core.publisher.Mono]]
    *
    * @return [[reactor.core.publisher.Mono]]
    */
  final def asJava(): JMono[T] = coreMono

  /**
    * Block until a next signal is received, will return null if onComplete, T if onNext, throw a
    * `Exceptions.DownstreamException` if checked error or origin RuntimeException if unchecked.
    * If the default timeout `30 seconds` has elapsed,a [[RuntimeException]]  will be thrown.
    *
    * Note that each block() will subscribe a new single (MonoSink) subscriber, in other words, the result might
    * miss signal from hot publishers.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/block.png" alt="">
    * <p>
    *
    * @param timeout maximum time period to wait for before raising a [[RuntimeException]]. Defaulted to [[Duration.Inf]]
    * @return T the result
    */
  final def block(timeout: Duration = Duration.Inf): T =
    if (timeout == Duration.Inf) coreMono.block()
    else coreMono.block(timeout)

  /**
    * Subscribe to this [[SMono]] and <strong>block</strong> until a next signal is
    * received, the Mono completes empty or a timeout expires. Returns an [[Option]]
    * for the first two cases, which can be used to replace the empty case with an
    * Exception via [[Option.orElse(throw exception)]].
    * In case the Mono itself errors, the original exception is thrown (wrapped in a
    * [[RuntimeException]] if it was a checked exception).
    * If the provided timeout expires, a [[RuntimeException]] is thrown.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.1.RELEASE/src/docs/marble/block.png" alt="">
    * <p>
    * Note that each block() will trigger a new subscription: in other words, the result
    * might miss signal from hot publishers.
    *
    * @param timeout maximum time period to wait for before raising a [[RuntimeException]]. Defaulted to [[Duration.Inf]]
    * @return T the result
    */
  final def blockOption(timeout: Duration = Duration.Inf): Option[T] =
    if (timeout == Duration.Inf) coreMono.blockOptional()
    else coreMono.blockOptional(timeout)

  /**
    * Cast the current [[SMono]] produced type into a target produced type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/cast1.png" alt="">
    *
    * @tparam E the [[SMono]] output type
    * @param clazz the target type to cast to
    * @return a casted [[SMono]]
    */
  @deprecated("Use the other cast signature instead", "reactor-scala-extensions 0.5.0")
  final def cast[E](clazz: Class[E]): SMono[E] = coreMono.cast(clazz).asScala

  /**
    * Cast the current [[SMono]] produced type into a target produced type.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/cast1.png" alt="">
    *
    * @tparam E the [[SMono]] output type
    * @return a casted [[SMono]]
    */
  final def cast[E](implicit classTag: ClassTag[E]): SMono[E] = coreMono.cast(classTag.runtimeClass.asInstanceOf[Class[E]]).asScala

  /**
    * Turn this [[SMono]] into a hot source and cache last emitted signals for further
    * [[Subscriber]], with an expiry timeout.
    * <p>
    * Completion and Error will also be replayed until `ttl` triggers in which case
    * the next [[Subscriber]] will start over a new subscription.
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/cache1.png"
    * alt="">
    *
    * @return a replaying [[SMono]]
    */
  final def cache(ttl: Duration = Duration.Inf): SMono[T] =
    if (ttl == Duration.Inf) coreMono.cache().asScala
    else coreMono.cache(ttl).asScala

  /**
    * Prepare this [[SMono]] so that subscribers will cancel from it on a
    * specified
    * [[reactor.core.scheduler.Scheduler]].
    *
    * @param scheduler the [[reactor.core.scheduler.Scheduler]] to signal cancel  on
    * @return a scheduled cancel [[SMono]]
    */
  final def cancelOn(scheduler: Scheduler): SMono[T] = coreMono.cancelOn(scheduler).asScala

  /**
    * Defer the given transformation to this [[SMono]] in order to generate a
    * target [[SMono]] type. A transformation will occur for each
    * [[org.reactivestreams.Subscriber]].
    *
    * `mono.transformDeferred(SMono::fromPublisher).subscribe()`
    *
    * @param transformer the function to lazily map this [[SMono]] into a target [[SMono]]
    *                    instance.
    * @tparam V the item type in the returned [[org.reactivestreams.Publisher]]
    * @return a new [[SMono]]
    * @see [[SMono.as]] for a loose conversion to an arbitrary type
    */
  final def transformDeferred[V](transformer: SMono[T] => Publisher[V]): SMono[V] = {
    val transformerFunction = new Function[JMono[T], Publisher[V]] {
      override def apply(t: JMono[T]): Publisher[V] = transformer(SMono.this)
    }
    coreMono.transformDeferred(transformerFunction).asScala
  }

  @deprecated("will be removed, use transformDeferred() instead", since="reactor-scala-extensions 0.5.0 reactor-core 3.3.0")
  final def compose[V](transformer: SMono[T] => Publisher[V]): SMono[V] = transformDeferred(transformer)

  /**
    * Provide a default unique value if this mono is completed without any data
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[SMono]]
    * @see [[SFlux.defaultIfEmpty]]
    */
  final def defaultIfEmpty(defaultV: T): SMono[T] = coreMono.defaultIfEmpty(defaultV).asScala

  /**
    * Delay this [[SMono]] element ([[Subscriber.onNext]] signal) by a given
    * [[Duration]], on a particular [[Scheduler]]. Empty monos or error signals are not delayed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/delayonnext.png" alt="">
    *
    * <p>
    * Note that the scheduler on which the mono chain continues execution will be the
    * scheduler provided if the mono is valued, or the current scheduler if the mono
    * completes empty or errors.
    *
    * @param delay [[Duration]] by which to delay the [[Subscriber.onNext]] signal
    * @param timer a time-capable [[Scheduler]] instance to delay the value signal on
    * @return a delayed [[SMono]]
    */
  final def delayElement(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = coreMono.delayElement(delay).asScala

  /**
    * Delay the [[SMono.subscribe subscription]] to this [[SMono]] source until the given
    * [[Duration]] elapses.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/delaysubscription1.png" alt="">
    *
    * @param delay [[Duration]] before subscribing this [[SMono]]
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return a delayed [[SMono]]
    *
    */
  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(delay, timer))

  /**
    * Delay the subscription to this [[SMono]] until another [[Publisher]]
    * signals a value or completes.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/delaysubscriptionp1.png" alt="">
    *
    * @param subscriptionDelay a
    *                          [[Publisher]] to signal by next or complete this [[SMono.subscribe]]
    * @tparam U the other source type
    * @return a delayed [[SMono]]
    *
    */
  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SMono[T] = new ReactiveSMono[T](coreMono.delaySubscription(subscriptionDelay))

  /**
    * Subscribe to this [[SMono]] and another [[Publisher]] that is generated from
    * this Mono's element and which will be used as a trigger for relaying said element.
    * <p>
    * That is to say, the resulting [[SMono]] delays until this Mono's element is
    * emitted, generates a trigger Publisher and then delays again until the trigger
    * Publisher terminates.
    * <p>
    * Note that contiguous calls to all delayUntil are fused together.
    * The triggers are generated and subscribed to in sequence, once the previous trigger
    * completes. Error is propagated immediately
    * downstream. In both cases, an error in the source is immediately propagated.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/delayUntil.png" alt="">
    *
    * @param triggerProvider a [[Function1]] that maps this Mono's value into a
    *                                  [[Publisher]] whose termination will trigger relaying the value.
    * @return this [[SMono]], but delayed until the derived publisher terminates.
    */
  final def delayUntil(triggerProvider: T => Publisher[_]): SMono[T] = coreMono.delayUntil(triggerProvider).asScala

  /**
    * A "phantom-operator" working only if this
    * [[SMono]] is a emits onNext, onError or onComplete [[reactor.core.publisher.Signal]]. The relative [[org.reactivestreams.Subscriber]]
    * callback will be invoked, error [[reactor.core.publisher.Signal]] will trigger onError and complete [[reactor.core.publisher.Signal]] will trigger
    * onComplete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/dematerialize1.png" alt="">
    *
    * @tparam X the dematerialized type
    * @return a dematerialized [[SMono]]
    */
  final def dematerialize[X](): SMono[X] = coreMono.dematerialize[X]().asScala

  /**
    * Triggered after the [[SMono]] terminates, either by completing downstream successfully or with an error.
    * The arguments will be null depending on success, success with data and error:
    * <ul>
    * <li>null, null : completed without data</li>
    * <li>T, null : completed with data</li>
    * <li>null, Throwable : failed with/without data</li>
    * </ul>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doafterterminate1.png" alt="">
    * <p>
    *
    * @param afterTerminate the callback to call after [[org.reactivestreams.Subscriber.onNext]], [[org.reactivestreams.Subscriber.onComplete]] without preceding [[org.reactivestreams.Subscriber.onNext]] or [[org.reactivestreams.Subscriber.onError]]
    * @return a new [[SMono]]
    */
  @deprecated("prefer using `doAfterTerminate` or `doFinally`. will be removed", since="reactor-scala-extensions 0.5.0, reactor-core 3.3.0")
  final def doAfterSuccessOrError(afterTerminate: Try[_ <: T] => Unit): SMono[T] = {
    val biConsumer = (t: T, u: Throwable) => Option(t) match {
      case Some(s) => afterTerminate(Success(s))
      case Some(null) | None => afterTerminate(Failure(u))
    }
    coreMono.doAfterSuccessOrError(biConsumer).asScala
  }

  /**
    * Add behavior (side-effect) triggered after the [[SMono]] terminates, either by
    * completing downstream successfully or with an error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/doafterterminate1.png" alt="">
    * <p>
    *
    * @param afterTerminate the callback to call after [[Subscriber.onComplete]] or [[Subscriber.onError]]
    * @return an observed  [[SMono]]
    */
  final def doAfterTerminate(afterTerminate: () => Unit): SMono[T] = coreMono.doAfterTerminate(afterTerminate).asScala

  /**
    * Add behavior triggering <strong>after</strong> the [[SMono]] terminates for any reason,
    * including cancellation. The terminating event [[SignalType.ON_COMPLETE]],
    * [[SignalType#ON_ERROR]] and [[SignalType#CANCEL]]) is passed to the consumer,
    * which is executed after the signal has been passed downstream.
    * <p>
    * Note that the fact that the signal is propagated downstream before the callback is
    * executed means that several doFinally in a row will be executed in
    * <strong>reverse order</strong>. If you want to assert the execution of the callback
    * please keep in mind that the Mono will complete before it is executed, so its
    * effect might not be visible immediately after eg. a [[SMono.block()]].
    *
    * @param onFinally the callback to execute after a terminal signal (complete, error
    *                  or cancel)
    * @return an observed [[SMono]]
    */
  final def doFinally(onFinally: SignalType => Unit): SMono[T] = coreMono.doFinally(onFinally).asScala

  /**
    * Triggered when the [[SMono]] is cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/dooncancel.png" alt="">
    * <p>
    *
    * @param onCancel the callback to call on [[org.reactivestreams.Subscriber.cancel]]
    * @return a new [[SMono]]
    */
  final def doOnCancel(onCancel: () => Unit): SMono[T] = coreMono.doOnCancel(onCancel).asScala

  /**
    * Add behavior triggered when the [[SMono]] emits a data successfully.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/doonnext.png" alt="">
    * <p>
    *
    * @param onNext the callback to call on [[Subscriber.onNext]]
    * @return a new [[SMono]]
    */
  final def doOnNext(onNext: T => Unit): SMono[T] = coreMono.doOnNext(onNext).asScala

  /**
    * Triggered when the [[SMono]] completes successfully.
    *
    * <ul>
    * <li>null : completed without data</li>
    * <li>T: completed with data</li>
    * </ul>
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonsuccess.png" alt="">
    * <p>
    *
    * @param onSuccess the callback to call on, argument is null if the [[SMono]]
    *                  completes without data
    *                  [[org.reactivestreams.Subscriber.onNext]] or [[org.reactivestreams.Subscriber.onComplete]] without preceding [[org.reactivestreams.Subscriber.onNext]]
    * @return a new [[SMono]]
    */
  final def doOnSuccess(onSuccess: T => Unit): SMono[T] = coreMono.doOnSuccess(onSuccess).asScala

  /**
    * Triggered when the [[SMono]] completes with an error.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonerror1.png" alt="">
    * <p>
    *
    * @param onError the error callback to call on [[org.reactivestreams.Subscriber.onError]]
    * @return a new [[SMono]]
    */
  final def doOnError(onError: Throwable => Unit): SMono[T] = coreMono.doOnError(onError).asScala

  /**
    * Attach a `Long consumer` to this [[SMono]] that will observe any request to this [[SMono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonrequest1.png" alt="">
    *
    * @param consumer the consumer to invoke on each request
    * @return an observed  [[SMono]]
    */
  final def doOnRequest(consumer: Long => Unit): SMono[T] = coreMono.doOnRequest(consumer).asScala

  /**
    * Triggered when the [[SMono]] is subscribed.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/doonsubscribe.png" alt="">
    * <p>
    *
    * @param onSubscribe the callback to call on [[Subscriber.onSubscribe]]
    * @return a new [[SMono]]
    */
  final def doOnSubscribe(onSubscribe: Subscription => Unit): SMono[T] = coreMono.doOnSubscribe(onSubscribe).asScala

  /**
    * Add behavior triggered when the [[SMono]] terminates, either by completing successfully or with an error.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/doonterminate1.png" alt="">
    * <p>
    *
    * @param onTerminate the callback to call [[Subscriber.onNext]], [[Subscriber.onComplete]] without preceding [[Subscriber.onNext]] or [[Subscriber.onError]]
    * @return a new [[SMono]]
    */
  final def doOnTerminate(onTerminate: () => Unit): SMono[T] = coreMono.doOnTerminate(onTerminate).asScala

  /**
    * Map this [[SMono]] sequence into [[scala.Tuple2]] of T1 [[Long]] timemillis and T2
    * `T` associated data. The timemillis corresponds to the elapsed time between the subscribe and the first
    * next signal.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/elapsed1.png" alt="">
    *
    * @param scheduler the [[Scheduler]] to read time from. Defaulted to [[Schedulers.parallel()]]
    * @return a transforming [[SMono]] that emits a tuple of time elapsed in milliseconds and matching data
    */
  final def elapsed(scheduler: Scheduler = Schedulers.parallel()): SMono[(Long, T)] = new ReactiveSMono[(Long, T)](coreMono.elapsed().map((t: Tuple2[JLong, T]) => javaTupleLongAndT2ScalaTupleLongAndT[T](t)))

  /**
    * Recursively expand elements into a graph and emit all the resulting element,
    * in a depth-first traversal order.
    * <p>
    * That is: emit the value from this [[SMono]], expand it and emit the first value
    * at this first level of recursion, and so on... When no more recursion is possible,
    * backtrack to the previous level and re-apply the strategy.
    * <p>
    * For example, given the hierarchical structure
    * <pre>
    * A
    *   - AA
    *     - aa1
    *   - AB
    *     - ab1
    *   - a1
    * </pre>
    *
    * Expands `Mono.just(A)` into
    * <pre>
    * A
    * AA
    * aa1
    * AB
    * ab1
    * a1
    * </pre>
    *
    * @param expander the [[Function1]] applied at each level of recursion to expand
    *                             values into a [[Publisher]], producing a graph.
    * @param capacityHint a capacity hint to prepare the inner queues to accommodate n
    *                     elements per level of recursion.
    * @return this Mono expanded depth-first to a [[SFlux]]
    */
  final def expandDeep(expander: T => Publisher[_ <: T], capacityHint: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreMono.expandDeep(expander, capacityHint).asScala

  /**
    * Recursively expand elements into a graph and emit all the resulting element using
    * a breadth-first traversal strategy.
    * <p>
    * That is: emit the value from this [[SMono]] first, then it each at a first level of
    * recursion and emit all of the resulting values, then expand all of these at a
    * second level and so on...
    * <p>
    * For example, given the hierarchical structure
    * <pre>
    * A
    *   - AA
    *     - aa1
    *   - AB
    *     - ab1
    *   - a1
    * </pre>
    *
    * Expands `Mono.just(A)` into
    * <pre>
    * A
    * AA
    * AB
    * a1
    * aa1
    * ab1
    * </pre>
    *
    * @param expander the [[Function1]] applied at each level of recursion to expand
    *                             values into a [[Publisher]], producing a graph.
    * @param capacityHint a capacity hint to prepare the inner queues to accommodate n
    *                     elements per level of recursion.
    * @return this Mono expanded breadth-first to a [[SFlux]]
    */
  final def expand(expander: T => Publisher[_ <: T], capacityHint: Int = SMALL_BUFFER_SIZE): SFlux[T] = coreMono.expand(expander, capacityHint).asScala

  /**
    * Test the result if any of this [[SMono]] and replay it if predicate returns true.
    * Otherwise complete without value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/filter1.png" alt="">
    * <p>
    *
    * @param tester the predicate to evaluate
    * @return a filtered [[SMono]]
    */
  final def filter(tester: T => Boolean): SMono[T] = coreMono.filter(tester).asScala

  /**
    * If this [[SMono]] is valued, test the value asynchronously using a generated
    * [[Publisher[Boolean]]] test. The value from the Mono is replayed if the
    * first item emitted by the test is `true`. It is dropped if the test is
    * either empty or its first emitted value is false``.
    * <p>
    * Note that only the first value of the test publisher is considered, and unless it
    * is a [[SMono]], test will be cancelled after receiving that first value.
    *
    * @param asyncPredicate the function generating a [[Publisher]] of [[Boolean]]
    *                                                         to filter the Mono with
    * @return a filtered [[SMono]]
    */
  final def filterWhen(asyncPredicate: T => _ <: MapablePublisher[Boolean]): SMono[T] = {
    val asyncPredicateFunction = new Function[T, Publisher[JBoolean]] {
      override def apply(t: T): Publisher[JBoolean] = asyncPredicate(t).map(Boolean2boolean(_))
    }
    coreMono.filterWhen(asyncPredicateFunction).asScala
  }

  /**
    * Transform the item emitted by this [[SMono]] asynchronously, returning the
    * value emitted by another [[SMono]] (possibly changing the value type).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/then.png" alt="">
    * <p>
    *
    * @param transformer the function to dynamically bind a new [[SMono]]
    * @tparam R the result type bound
    * @return a new [[SMono]] with an asynchronously mapped value.
    */
  final def flatMap[R](transformer: T => SMono[R]): SMono[R] = coreMono.flatMap[R]((t: T) => transformer(t).coreMono).asScala

  /**
    * Transform the item emitted by this [[SMono]] into a Publisher, then forward
    * its emissions into the returned [[SFlux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmap1.png" alt="">
    * <p>
    *
    * @param mapper the
    *               [[Function1]] to produce a sequence of R from the the eventual passed [[Subscriber.onNext]]
    * @tparam R the merged sequence type
    * @return a new [[SFlux]] as the sequence is not guaranteed to be single at most
    */
  final def flatMapMany[R](mapper: T => Publisher[R]): SFlux[R] = coreMono.flatMapMany(mapper).asScala

  /**
    * Transform the signals emitted by this [[SMono]] into a Publisher, then forward
    * its emissions into the returned [[SFlux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmaps1.png" alt="">
    * <p>
    *
    * @param mapperOnNext     the [[Function1]] to call on next data and returning a sequence to merge
    * @param mapperOnError    the[[Function1]] to call on error signal and returning a sequence to merge
    * @param mapperOnComplete the [[Function1]] to call on complete signal and returning a sequence to merge
    * @tparam R the type of the produced inner sequence
    * @return a new [[SFlux]] as the sequence is not guaranteed to be single at most
    * @see [[SFlux.flatMap]]
    */
  final def flatMapMany[R](mapperOnNext: T => Publisher[R],
                           mapperOnError: Throwable => Publisher[R],
                           mapperOnComplete: () => Publisher[R]): SFlux[R] =
    coreMono.flatMapMany(mapperOnNext, mapperOnError, mapperOnComplete).asScala

  /**
    * Transform the item emitted by this [[SMono]] into [[Iterable]], , then forward
    * its elements into the returned [[SFlux]]. The prefetch argument allows to
    * give an
    * arbitrary prefetch size to the inner [[Iterable]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/flatmap.png" alt="">
    *
    * @param mapper the [[Function1]] to transform input item into a sequence [[Iterable]]
    * @tparam R the merged output sequence type
    * @return a merged [[SFlux]]
    *
    */
  final def flatMapIterable[R](mapper: T => Iterable[R]): SFlux[R] = coreMono.flatMapIterable(mapper.andThen(it => it.asJava)).asScala

  /**
    * Convert this [[SMono]] to a [[SFlux]]
    *
    * @return a [[SFlux]] variant of this [[SMono]]
    */
  final def flux(): SFlux[T] = coreMono.flux().asScala

  /**
    * Emit a single boolean true if this [[SMono]] has an element.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/haselement.png" alt="">
    *
    * @return a new [[SMono]] with <code>true</code> if a value is emitted and <code>false</code>
    *                       otherwise
    */
  final def hasElement: SMono[Boolean] = coreMono.hasElement.map[Boolean](scalaFunction2JavaFunction((jb: JBoolean) => boolean2Boolean(jb.booleanValue()))).asScala

  /**
    * Handle the items emitted by this [[SMono]] by calling a biconsumer with the
    * output sink for each onNext. At most one [[SynchronousSink.next]]
    * call must be performed and/or 0 or 1 [[SynchronousSink.error]] or
    * [[SynchronousSink.complete]].
    *
    * @param handler the handling `BiConsumer`
    * @tparam R the transformed type
    * @return a transformed [[SMono]]
    */
  final def handle[R](handler: (T, SynchronousSink[R]) => Unit): SMono[R] = coreMono.handle[R](handler).asScala

  /**
    * Hides the identity of this [[SMono]] instance.
    *
    * <p>The main purpose of this operator is to prevent certain identity-based
    * optimizations from happening, mostly for diagnostic purposes.
    *
    * @return a new [[SMono]] instance
    */
  final def hide(): SMono[T] = coreMono.hide().asScala

  /**
    * Ignores onNext signal (dropping it) and only reacts on termination.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignoreelement.png" alt="">
    * <p>
    *
    * @return a new completable [[SMono]].
    */
  final def ignoreElement: SMono[T] = coreMono.ignoreElement().asScala

  /**
    * Observe Reactive Streams signals matching the passed flags `options` and use
    * [[reactor.util.Logger]] support to handle trace implementation. Default will use the passed
    * [[Level]] and java.util.logging. If SLF4J is available, it will be used instead.
    *
    * Options allow fine grained filtering of the traced signal, for instance to only capture onNext and onError:
    * <pre>
    *     mono.log("category", SignalType.ON_NEXT, SignalType.ON_ERROR)
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/log1.png" alt="">
    * <p>
    *
    * @param category to be mapped into logger configuration (e.g. org.springframework
    *                 .reactor). If category ends with "." like "reactor.", a generated operator
    *                 suffix will complete, e.g. "reactor.Flux.Map".
    * @param level    the { @link Level} to enforce for this tracing Mono (only FINEST, FINE,
    *                             INFO, WARNING and SEVERE are taken into account)
    * @param options a [[Seq]] of [[SignalType]] option to filter log messages
    * @return a new [[SMono]]
    *
    */
  final def log(category: Option[String] = None, level: Level = Level.INFO, showOperator: Boolean = false, options: Seq[SignalType] = Nil): SMono[T] = coreMono.log(category.orNull, level, showOperator, options: _*).asScala

  /**
    * Transform the item emitted by this [[SMono]] by applying a synchronous function to it.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/map1.png" alt="">
    * <p>
    *
    * @param mapper the synchronous transforming [[Function1]]
    * @tparam R the transformed type
    * @return a new [[SMono]]
    */
  final def map[R](mapper: T => R): SMono[R] = coreMono.map[R](mapper).asScala

  /**
    * Transform incoming onNext, onError and onComplete signals into [[Signal]] instances,
    * materializing these signals.
    * Since the error is materialized as a [[Signal]], the propagation will be stopped and onComplete will be
    * emitted. Complete signal will first emit a [[Signal.complete()]] and then effectively complete the flux.
    * All these [[Signal]] have a [[reactor.util.context.Context]] associated to them.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/materialize1.png" alt="">
    *
    * @return a [[SMono]] of materialized [[Signal]]
    * @see [[SMono.dematerialize()]]
    */
  final def materialize(): SMono[Signal[T]] = coreMono.materialize().asScala

  /**
    * Merge emissions of this [[SMono]] with the provided [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/merge1.png" alt="">
    * <p>
    *
    * @param other the other [[Publisher]] to merge with
    * @return a new [[SFlux]] as the sequence is not guaranteed to be at most 1
    */
  final def mergeWith(other: Publisher[_ <: T]): SFlux[T] = coreMono.mergeWith(other).asScala

  /**
    * Activate metrics for this sequence, provided there is an instrumentation facade
    * on the classpath (otherwise this method is a pure no-op).
    * <p>
    * Metrics are gathered on [[Subscriber]] events, and it is recommended to also
    * [[name]] (and optionally [[tag]]) the sequence.
    *
    * @return an instrumented [[SMono]]
    */
  final def metrics: SMono[T] = SMono.fromPublisher(coreMono.metrics())

  /**
    * Give a name to this sequence, which can be retrieved using [[Scannable.name]]
    * as long as this is the first reachable [[Scannable.parents]].
    *
    * @param name a name for the sequence
    * @return the same sequence, but bearing a name
    */
  final def name(name: String): SMono[T] = coreMono.name(name).asScala

  /**
    * Evaluate the accepted value against the given [[Class]] type. If the
    * predicate test succeeds, the value is
    * passed into the new [[SMono]]. If the predicate test fails, the value is
    * ignored.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/filter.png" alt="">
    *
    * @param clazz the [[Class]] type to test values against
    * @return a new [[SMono]] reduced to items converted to the matched type
    */
  @deprecated("Use the other ofType signature instead", "reactor-scala-extensions 0.5.0")
  final def ofType[U](clazz: Class[U]): SMono[U] = coreMono.ofType[U](clazz).asScala


  /**
    * Evaluate the accepted value against the given [[Class]] type. If the
    * predicate test succeeds, the value is
    * passed into the new [[SMono]]. If the predicate test fails, the value is
    * ignored.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/filter.png" alt="">
    *
    * @tparam U the [[Class]] type to test values against
    * @return a new [[SMono]] reduced to items converted to the matched type
    */
  final def ofType[U](implicit classTag: ClassTag[U]): SMono[U] = coreMono.ofType[U](classTag.runtimeClass.asInstanceOf[Class[U]]).asScala

  /**
    * Transform the error emitted by this [[SMono]] by applying a function.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/maperror.png"
    * <p>
    *
    * @param mapper the error transforming [[PartialFunction]]
    * @return a transformed [[SMono]]
    */
  final def onErrorMap(mapper: PartialFunction[Throwable, Throwable]): SMono[T] =
    coreMono.onErrorMap((t: Throwable) => if (mapper.isDefinedAt(t)) mapper(t) else t).asScala

  private def defaultToMonoError[U](t: Throwable): SMono[U] = SMono.raiseError[U](t)

  final def onErrorRecover[U <: T](pf: PartialFunction[Throwable, U]): SMono[T] = {
    def recover(t: Throwable): SMono[U] = pf.andThen(u => SMono.just(u)).applyOrElse(t, defaultToMonoError)

    onErrorResume(recover)
  }

  /**
    * Subscribe to a returned fallback publisher when any error occurs.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/otherwise.png" alt="">
    * <p>
    *
    * @param fallback the function to map an alternative [[SMono]]
    * @return an alternating [[SMono]] on source onError
    * @see [[SFlux.onErrorResume]]
    */
  final def onErrorResume(fallback: Throwable => SMono[_ <: T]): SMono[T] = {
    val fallbackFunction = new Function[Throwable, JMono[_ <: T]] {
      override def apply(t: Throwable): JMono[_ <: T] = fallback(t).coreMono
    }
    coreMono.onErrorResume(fallbackFunction).asScala
  }

  /**
    * Detaches the both the child [[Subscriber]] and the [[Subscription]] on
    * termination or cancellation.
    * <p>This should help with odd retention scenarios when running
    * with non-reactor [[Subscriber]].
    *
    * @return a detachable [[SMono]]
    */
  final def onTerminateDetach(): SMono[T] = coreMono.onTerminateDetach().asScala

  /**
    * Emit the any of the result from this mono or from the given mono
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/or.png" alt="">
    * <p>
    *
    * @param other the racing other [[SFlux]] to compete with for the result
    * @return a new [[SFlux]]
    * @see [[SMono.firstEmitter()]]
    */
  final def or(other: SMono[_ <: T]): SMono[T] = coreMono.or(other.coreMono).asScala

  /**
    * Shares a [[SMono]] for the duration of a function that may transform it and
    * consume it as many times as necessary without causing multiple subscriptions
    * to the upstream.
    *
    * @param transform the transformation function
    * @tparam R the output value type
    * @return a new [[SMono]]
    */
  final def publish[R](transform: SMono[T] => SMono[R]): SMono[R] = {
    val transformFunction = new Function[JMono[T], JMono[R]] {
      override def apply(t: JMono[T]): JMono[R] = transform(SMono.this).coreMono
    }
    coreMono.publish[R](transformFunction).asScala
  }

  /**
    * Run onNext, onComplete and onError on a supplied [[Scheduler]]
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/publishon1.png" alt="">
    * <p> <p>
    * Typically used for fast publisher, slow consumer(s) scenarios.
    *
    * `mono.publishOn(Schedulers.single()).subscribe()`
    *
    * @param scheduler a checked { @link reactor.core.scheduler.Scheduler.Worker} factory
    * @return an asynchronously producing [[SMono]]
    */
  //TODO: How to test this?
  final def publishOn(scheduler: Scheduler): SMono[T] = coreMono.publishOn(scheduler).asScala

  /**
    * Repeatedly subscribe to the source completion of the previous subscription.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/repeatnb.png" alt="">
    *
    * @return an indefinitively repeated [[SFlux]] on onComplete
    */
  final def repeat(numRepeat: Long = Long.MaxValue, predicate: () => Boolean = () => true): SFlux[T] = coreMono.repeat(numRepeat, predicate).asScala

  /**
    * Repeatedly subscribe to this [[SMono]] when a companion sequence signals a number of emitted elements in
    * response to the flux completion signal.
    * <p>If the companion sequence signals when this [[SMono]] is active, the repeat
    * attempt is suppressed and any terminal signal will terminate this [[SFlux]] with
    * the same signal immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/repeatwhen.png" alt="">
    *
    * @param whenFactory the [[Function1]] providing a [[SFlux]] signalling an exclusive number of
    *                                emitted elements on onComplete and returning a [[Publisher]] companion.
    * @return an eventually repeated [[SFlux]] on onComplete when the companion [[Publisher]] produces an
    *                                        onNext signal
    *
    */
  final def repeatWhen(whenFactory: SFlux[Long] => _ <: Publisher[_]): SFlux[T] = {
    val when = new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = whenFactory(new ReactiveSFlux[Long](t))
    }
    coreMono.repeatWhen(when).asScala
  }

  /**
    * Repeatedly subscribe to this [[SMono]] until there is an onNext signal when a companion sequence signals a
    * number of emitted elements.
    * <p>If the companion sequence signals when this [[SMono]] is active, the repeat
    * attempt is suppressed and any terminal signal will terminate this [[SMono]] with the same signal immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/repeatwhenempty.png" alt="">
    *
    * @param repeatFactory the
    *                      [[Function1]] providing a [[SFlux]] signalling the current number of repeat on onComplete and returning a [[Publisher]] companion.
    * @return an eventually repeated [[SMono]] on onComplete when the companion [[Publisher]] produces an
    *                                        onNext signal
    *
    */
  final def repeatWhenEmpty(repeatFactory: SFlux[Long] => Publisher[_], maxRepeat: Int = Int.MaxValue): SMono[T] = {
    val when = new Function[JFlux[JLong], Publisher[_]] {
      override def apply(t: JFlux[JLong]): Publisher[_] = repeatFactory(new ReactiveSFlux[Long](t))
    }
    coreMono.repeatWhenEmpty(when).asScala
  }

  /**
    * Re-subscribes to this [[SMono]] sequence if it signals any error
    * either indefinitely or a fixed number of times.
    * <p>
    * The times == Long.MAX_VALUE is treated as infinite retry.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/retryn1.png" alt="">
    *
    * @param numRetries the number of times to tolerate an error
    * @return a re-subscribing [[SMono]] on onError up to the specified number of retries.
    *
    */
  final def retry(numRetries: Long = Long.MaxValue, retryMatcher: Throwable => Boolean = (_: Throwable) => true): SMono[T] = coreMono.retry(numRetries, retryMatcher).asScala

  /**
    * Retries this [[SMono]] when a companion sequence signals
    * an item in response to this [[SMono]] error signal
    * <p>If the companion sequence signals when the [[SMono]] is active, the retry
    * attempt is suppressed and any terminal signal will terminate the [[SMono]] source with the same signal
    * immediately.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/retrywhen1.png" alt="">
    *
    * @param whenFactory the [[Function1]] providing a [[SFlux]] signalling any error from the source sequence and returning a [[Publisher]] companion.
    * @return a re-subscribing [[SMono]] on onError when the companion [[Publisher]] produces an
    *                                  onNext signal
    */
  final def retryWhen(whenFactory: SFlux[Throwable] => Publisher[_]): SMono[T] = {
    val when = new Function[JFlux[Throwable], Publisher[_]] {
      override def apply(t: JFlux[Throwable]): Publisher[_] = whenFactory(new ReactiveSFlux[Throwable](t))
    }
    coreMono.retryWhen(when).asScala
  }

  /**
    * Expect exactly one item from this [[SMono]] source or signal
    * [[java.util.NoSuchElementException]] for an empty source.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/single.png" alt="">
    * <p>
    * Note Mono doesn't need [[SFlux.single(AnyRef)]], since it is equivalent to
    * [[SMono.defaultIfEmpty(AnyRef)]] in a [[SMono]].
    *
    * @return a [[SMono]] with the single item or an error signal
    */
  final def single(): SMono[T] = coreMono.single().asScala

  /**
    * Subscribe to this [[SMono]] and request unbounded demand.
    * <p>
    * This version doesn't specify any consumption behavior for the events from the
    * chain, especially no error handling, so other variants should usually be preferred.
    *
    * <p>
    * <img width="500" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/unbounded1.png" alt="">
    * <p>
    *
    * @return a new [[Disposable]] that can be used to cancel the underlying [[Subscription]]
    */
  final def subscribe(): Disposable = coreMono.subscribe()

  /**
    * Subscribe a [[scala.Function1[T,Unit] Consumer]] to this [[SMono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[SMono.doOnSuccess]] and
    * [[SMono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribe1.png" alt="">
    *
    * @param consumer the consumer to invoke on each value
    * @return a new [[Runnable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit): Disposable = coreMono.subscribe(consumer)

  /**
    * Subscribe [[scala.Function1[T,Unit] Consumer]] to this [[SMono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[SMono.doOnSuccess]] and
    * [[SMono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/subscribeerror1.png" alt="">
    *
    * @param consumer      the consumer to invoke on each next signal
    * @param errorConsumer the consumer to invoke on error signal
    * @return a new [[Runnable]] to dispose the [[org.reactivestreams.Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer)

  /**
    * Subscribe `consumer` to this [[SMono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[SMono.doOnSuccess]] and
    * [[SMono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribecomplete1.png" alt="">
    *
    * @param consumer         the consumer to invoke on each value
    * @param errorConsumer    the consumer to invoke on error signal
    * @param completeConsumer the consumer to invoke on complete signal
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer, completeConsumer)

  /**
    * Subscribe `consumer` to this [[SMono]] that will consume all the
    * sequence.
    * <p>
    * For a passive version that observe and forward incoming data see [[SMono.doOnSuccess]] and
    * [[SMono.doOnError]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribecomplete1.png" alt="">
    *
    * @param consumer             the consumer to invoke on each value
    * @param errorConsumer        the consumer to invoke on error signal
    * @param completeConsumer     the consumer to invoke on complete signal
    * @param subscriptionConsumer the consumer to invoke on subscribe signal, to be used
    *                             for the initial [[Subscription.request request]], or null for max request
    * @return a new [[Disposable]] to dispose the [[Subscription]]
    */
  final def subscribe(consumer: T => Unit, errorConsumer: Throwable => Unit, completeConsumer: => Unit, subscriptionConsumer: Subscription => Unit): Disposable = coreMono.subscribe(consumer, errorConsumer, completeConsumer, subscriptionConsumer)

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreMono.subscribe(s)

  /**
    * Enrich a potentially empty downstream [[Context]] by adding all values
    * from the given [[Context]], producing a new [[Context]] that is propagated
    * upstream.
    * <p>
    * The [[Context]] propagation happens once per subscription (not on each onNext):
    * it is done during the `subscribe(Subscriber)` phase, which runs from
    * the last operator of a chain towards the first.
    * <p>
    * So this operator enriches a [[Context]] coming from under it in the chain
    * (downstream, by default an empty one) and passes the new enriched [[Context]]
    * to operators above it in the chain (upstream, by way of them using
    * [[SFlux.subscribe(Subscriber,Context)]]).
    *
    * @param mergeContext the [[Context]] to merge with a previous [[Context]]
    *                                 state, returning a new one.
    * @return a contextualized [[SMono]]
    * @see [[Context]]
    */
  final def subscriberContext(mergeContext: Context): SMono[T] = coreMono.subscriberContext(mergeContext).asScala

  /**
    * Enrich a potentially empty downstream [[Context]] by applying a [[Function1]]
    * to it, producing a new [[Context]] that is propagated upstream.
    * <p>
    * The [[Context]] propagation happens once per subscription (not on each onNext):
    * it is done during the `subscribe(Subscriber)` phase, which runs from
    * the last operator of a chain towards the first.
    * <p>
    * So this operator enriches a [[Context]] coming from under it in the chain
    * (downstream, by default an empty one) and passes the new enriched [[Context]]
    * to operators above it in the chain (upstream, by way of them using
    * `Flux#subscribe(Subscriber,Context)`).
    *
    * @param doOnContext the function taking a previous [[Context]] state
    *                                                           and returning a new one.
    * @return a contextualized [[SMono]]
    * @see [[Context]]
    */
  final def subscriberContext(doOnContext: Context => Context): SMono[T] = coreMono.subscriberContext(doOnContext).asScala

  /**
    * Run the requests to this Publisher [[SMono]] on a given worker assigned by the supplied [[Scheduler]].
    * <p>
    * `mono.subscribeOn(Schedulers.parallel()).subscribe())`
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/subscribeon1.png" alt="">
    * <p>
    *
    * @param scheduler a checked [[reactor.core.scheduler.Scheduler.Worker]] factory
    * @return an asynchronously requesting [[SMono]]
    */
  final def subscribeOn(scheduler: Scheduler): SMono[T] = coreMono.subscribeOn(scheduler).asScala

  /**
    * Subscribe the given [[Subscriber]] to this [[SMono]] and return said
    * [[Subscriber]] (eg. a [[reactor.core.publisher.MonoProcessor]].
    *
    * @param subscriber the [[Subscriber]] to subscribe with
    * @tparam E the reified type of the [[Subscriber]] for chaining
    * @return the passed [[Subscriber]] after subscribing it to this [[SMono]]
    */
  final def subscribeWith[E <: Subscriber[_ >: T]](subscriber: E): E = coreMono.subscribeWith(subscriber)

  /**
    * Provide an alternative [[SMono]] if this mono is completed without data
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/otherwiseempty.png" alt="">
    * <p>
    *
    * @param alternate the alternate mono if this mono is empty
    * @return an alternating [[SMono]] on source onComplete without elements
    * @see [[SFlux.switchIfEmpty]]
    */
  final def switchIfEmpty(alternate: SMono[_ <: T]): SMono[T] = coreMono.switchIfEmpty(alternate.coreMono).asScala

  /**
    * Tag this mono with a key/value pair. These can be retrieved as a [[Stream]] of
    * all tags throughout the publisher chain by using [[reactor.core.scala.Scannable.tags()]] (as
    * traversed
    * by [[reactor.core.scala.Scannable.parents()]]).
    *
    * @param key   a tag key
    * @param value a tag value
    * @return the same sequence, but bearing tags
    */
  final def tag(key: String, value: String): SMono[T] = coreMono.tag(key, value).asScala

  /**
    * Give this Mono a chance to resolve within a specified time frame but complete if it
    * doesn't. This works a bit like [[SMono.timeout(Duration)]] except that the resulting
    * [[SMono]] completes rather than errors when the timer expires.
    * <p>
    * The timeframe is evaluated using the provided [[Scheduler]].
    *
    * @param duration the maximum duration to wait for the source Mono to resolve.
    * @param timer    the [[Scheduler]] on which to measure the duration.
    * @return a new [[SMono]] that will propagate the signals from the source unless
    *                       no signal is received for `duration`, in which case it completes.
    */
  final def take(duration: Duration, timer: Scheduler = Schedulers.parallel()): SMono[T] = coreMono.take(duration, timer).asScala

  /**
    * Give this Mono a chance to resolve before a companion [[Publisher]] emits. If
    * the companion emits before any signal from the source, the resulting SMono will
    * complete. Otherwise, it will relay signals from the source.
    *
    * @param other a companion [[Publisher]] that short-circuits the source with an
    *                                  onComplete signal if it emits before the source emits.
    * @return a new [[SMono]] that will propagate the signals from the source unless
    *                       a signal is first received from the companion [[Publisher]], in which case it
    *                       completes.
    */
  final def takeUntilOther(other: Publisher[_]): SMono[T] = coreMono.takeUntilOther(other).asScala

  /**
    * Return an `SMono[Unit]` which only replays complete and error signals
    * from this [[SMono]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen.png" alt="">
    * <p>
    *
    * @return a [[SMono]] ignoring its payload (actively dropping)
    */
  final def `then`(): SMono[Unit] = new ReactiveSMono[Unit](coreMono.`then`().map((_: Void) => ()))

  /**
    * Ignore element from this [[SMono]] and transform its completion signal into the
    * emission and completion signal of a provided `Mono[V]`. Error signal is
    * replayed in the resulting `SMono[V]`.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen1.png" alt="">
    *
    * @param other a [[SMono]] to emit from after termination
    * @tparam V the element type of the supplied Mono
    * @return a new [[SMono]] that emits from the supplied [[SMono]]
    */
  final def `then`[V](other: SMono[V]): SMono[V] = coreMono.`then`(other.coreMono).asScala

  /**
    * Return a `SMono[Unit]` that waits for this [[SMono]] to complete then
    * for a supplied [[Publisher Publisher[Unit]]] to also complete. The
    * second completion signal is replayed, or any error signal that occurs instead.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/ignorethen.png"
    * alt="">
    *
    * @param other a [[Publisher]] to wait for after this Mono's termination
    * @return a new [[SMono]] completing when both publishers have completed in
    *                       sequence
    */
  final def thenEmpty(other: MapablePublisher[Unit]): SMono[Unit] = new ReactiveSMono[Unit]((coreMono: JMono[T]).thenEmpty(other).map((_: Void) => ()))

  /**
    * Ignore element from this mono and transform the completion signal into a
    * `SFlux[V]` that will emit elements from the provided [[Publisher]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/ignorethens.png" alt="">
    *
    * @param other a [[Publisher]] to emit from after termination
    * @tparam V the element type of the supplied Publisher
    * @return a new [[SMono]] that emits from the supplied [[Publisher]] after
    *                       this SMono completes.
    */
  final def thenMany[V](other: Publisher[V]): SFlux[V] = coreMono.thenMany(other).asScala

  /**
    * Switch to a fallback [[SMono]] in case an item doesn't arrive before the given period.
    *
    * <p> If the given [[Publisher]] is null, signal a [[java.util.concurrent.TimeoutException]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timeouttimefallback1.png" alt="">
    *
    * @param timeout  the timeout before the onNext signal from this [[SMono]]
    * @param fallback the fallback [[SMono]] to subscribe when a timeout occurs
    * @param timer a time-capable [[Scheduler]] instance to run on
    * @return an expirable [[SMono]] with a fallback [[SMono]]
    */
  final def timeout(timeout: Duration, fallback: Option[SMono[_ <: T]] = None, timer: Scheduler = Schedulers.parallel()): SMono[T] =
    coreMono.timeout(timeout, fallback.map(_.coreMono).orNull[JMono[_ <: T]], timer).asScala

  /**
    * Switch to a fallback [[Publisher]] in case the  item from this {@link Mono} has
    * not been emitted before the given [[Publisher]] emits.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/timeoutfallbackp1.png" alt="">
    *
    * @param firstTimeout the timeout
    *                     [[Publisher]] that must not emit before the first signal from this [[SMono]]
    * @param fallback the fallback [[Publisher]] to subscribe when a timeout occurs
    * @tparam U the element type of the timeout Publisher
    * @return an expirable [[SMono]] with a fallback [[SMono]] if the item doesn't
    *                              come before a [[Publisher]] signals
    *
    */
  final def timeoutWhen[U](firstTimeout: Publisher[U], fallback: Option[SMono[_ <: T]] = None): SMono[T] = {
    val x = fallback.map((sm: SMono[_ <: T]) => coreMono.timeout[U](firstTimeout, sm.coreMono))
      .getOrElse(coreMono.timeout[U](firstTimeout))
    new ReactiveSMono[T](x)
  }

  /**
    * Emit a [[Tuple2]] pair of T1 [[Long]] current system time in
    * millis and T2 `T` associated data for the eventual item from this [[SMono]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/timestamp1.png" alt="">
    *
    * @param scheduler a [[Scheduler]] instance to read time from
    * @return a timestamped [[SMono]]
    */
  //  How to test this?
  final def timestamp(scheduler: Scheduler = Schedulers.parallel()): SMono[(Long, T)] = new ReactiveSMono[(Long, T)](coreMono.timestamp(scheduler).map((t2: Tuple2[JLong, T]) => (Long2long(t2.getT1), t2.getT2)))

  /**
    * Transform this [[SMono]] into a [[Future]] completing on onNext or onComplete and failing on
    * onError.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/master/reactor-core/src/main/java/reactor/core/publisher/doc-files/marbles/toFuture.svg" alt="">
    * <p>
    *
    * @return a [[Future]]
    */
  final def toFuture: Future[T] = {
    val promise = Promise[T]()
    coreMono.toFuture.handle[Unit]((value: T, throwable: Throwable) => {
      Option(throwable) match {
        case Some(_) => promise.failure(throwable)
        case None => promise.complete(Success(value))
      }
      ()
    })
    promise.future
  }

  /**
    * Transform this [[SMono]] in order to generate a target [[SMono]]. Unlike [[SMono.compose]], the
    * provided function is executed as part of assembly.
    *
    * @example {{{
    *    val applySchedulers = mono => mono.subscribeOn(Schedulers.elastic()).publishOn(Schedulers.parallel());
    *    mono.transform(applySchedulers).map(v => v * v).subscribe()
    *          }}}
    * @param transformer the [[Function1]] to immediately map this [[SMono]] into a target [[SMono]]
    *                                instance.
    * @tparam V the item type in the returned [[SMono]]
    * @return a new [[SMono]]
    * @see [[SMono.compose]] for deferred composition of [[SMono]] for each [[Subscriber]]
    * @see [[SMono.as]] for a loose conversion to an arbitrary type
    */
  final def transform[V](transformer: SMono[T] => Publisher[V]): SMono[V] = coreMono.transform[V]((_: JMono[T]) => transformer(SMono.this)).asScala

}

object SMono extends ScalaConverters {

  /**
    * An alias of [[SMono.fromPublisher]]
    * @param source The underlying [[Publisher]]. This can be used to convert [[JMono]] into [[SMono]]
    * @tparam T a value type parameter of this [[SMono]]
    * @return [[SMono]]
    */
  def apply[T](source: Publisher[_ <: T]): SMono[T] = SMono.fromPublisher[T](source)

  /**
    * Creates a deferred emitter that can be used with callback-based
    * APIs to signal at most one value, a complete or an error signal.
    * <p>
    * Bridging legacy API involves mostly boilerplate code due to the lack
    * of standard types and methods. There are two kinds of API surfaces:
    * 1) addListener/removeListener and 2) callback-handler.
    * <p>
    * <b>1) addListener/removeListener pairs</b><br>
    * To work with such API one has to instantiate the listener,
    * call the sink from the listener then register it with the source:
    * <pre><code>
    * SMono.&lt;String&gt;create(sink =&gt; {
    * HttpListener listener = event =&gt; {
    * if (event.getResponseCode() >= 400) {
    *             sink.error(new RuntimeException("Failed"));
    * } else {
    * String body = event.getBody();
    * if (body.isEmpty()) {
    *                 sink.success();
    * } else {
    *                 sink.success(body.toLowerCase());
    * }
    * }
    * };
    *
    *     client.addListener(listener);
    *
    *     sink.onDispose(() =&gt; client.removeListener(listener));
    * });
    * </code></pre>
    * Note that this works only with single-value emitting listeners. Otherwise,
    * all subsequent signals are dropped. You may have to add `client.removeListener(this);`
    * to the listener's body.
    * <p>
    * <b>2) callback handler</b><br>
    * This requires a similar instantiation pattern such as above, but usually the
    * successful completion and error are separated into different methods.
    * In addition, the legacy API may or may not support some cancellation mechanism.
    * <pre><code>
    * SMono.&lt;String&gt;create(sink =&gt; {
    * Callback&lt;String&gt; callback = new Callback&lt;String&gt;() {
    * &#64;Override
    * public void onResult(String data) {
    *             sink.success(data.toLowerCase());
    * }
    *
    * &#64;Override
    * public void onError(Exception e) {
    *             sink.error(e);
    * }
    * }
    *
    * // without cancellation support:
    *
    *     client.call("query", callback);
    *
    * // with cancellation support:
    *
    * AutoCloseable cancel = client.call("query", callback);
    *     sink.onDispose(() => {
    * try {
    *             cancel.close();
    * } catch (Exception ex) {
    *             Exceptions.onErrorDropped(ex);
    * }
    * });
    * });
    * </code></pre>
    *
    * @param callback Consume the { @link MonoSink} provided per-subscriber by Reactor to generate signals.
    * @tparam T The type of the value emitted
    * @return a [[SMono]]
    */
  def create[T](callback: MonoSink[T] => Unit): SMono[T] = JMono.create[T](callback).asScala

  def defer[T](supplier: () => SMono[T]): SMono[T] = SMono.fromPublisher(JMono.defer[T](() => supplier().asJava()))

  def delay(duration: Duration, timer: Scheduler = Schedulers.parallel()): SMono[Long] = new ReactiveSMono[Long](JMono.delay(duration, timer))

  def empty[T]: SMono[T] = JMono.empty[T]().asScala

  /**
    * Pick the first result coming from any of the given monos and populate a new `Mono`.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.6.RELEASE/src/docs/marble/first.png" alt="">
    * <p>
    *
    * @param monos The deferred monos to use.
    * @tparam T The type of the function result.
    * @return a [[SMono]].
    */
  def firstEmitter[T](monos: SMono[_ <: T]*): SMono[T] = JMono.first[T](monos.map(_.asJava()): _*).asScala

  def fromPublisher[T](source: Publisher[_ <: T]): SMono[T] = JMono.from[T](source).asScala

  def fromCallable[T](supplier: Callable[T]): SMono[T] = JMono.fromCallable[T](supplier).asScala

  def fromDirect[I](source: Publisher[_ <: I]): SMono[I] = JMono.fromDirect[I](source).asScala

  def fromFuture[T](future: Future[T])(implicit executionContext: ExecutionContext): SMono[T] = {
    val completableFuture = new CompletableFuture[T]()
    future onComplete {
      case Success(t) => completableFuture.complete(t)
      case Failure(error) => completableFuture.completeExceptionally(error)
    }
    JMono.fromFuture[T](completableFuture).asScala
  }

  /**
    * Transform a [[Try]] into an [[SMono]]
    * @param aTry a Try
    * @tparam T The type of the [[Try]]
    * @return an [[SMono]]
    */
  def fromTry[T](aTry: => Try[T]): SMono[T] = create[T](sink => {
    aTry match {
      case Success(t) => sink.success(t)
      case Failure(ex) => sink.error(ex)
    }
  })

  def ignoreElements[T](source: Publisher[T]): SMono[T] = JMono.ignoreElements(source).asScala

  def just[T](data: T): SMono[T] = new ReactiveSMono[T](JMono.just(data))

  def justOrEmpty[T](data: Option[_ <: T]): SMono[T] = JMono.justOrEmpty[T](data).asScala

  def justOrEmpty[T](data: T): SMono[T] = JMono.justOrEmpty(data).asScala

  def never[T]: SMono[T] = JMono.never[T]().asScala

  def sequenceEqual[T](source1: Publisher[_ <: T], source2: Publisher[_ <: T], isEqual: (T, T) => Boolean = (t1: T, t2: T) => t1 == t2, bufferSize: Int = SMALL_BUFFER_SIZE): SMono[Boolean] =
    new ReactiveSMono[JBoolean](JMono.sequenceEqual[T](source1, source2, isEqual, bufferSize)).map(Boolean2boolean)

  /**
    * Create a [[SMono]] emitting the [[Context]] available on subscribe.
    * If no Context is available, the mono will simply emit the
    * [[Context.empty() empty Context]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.3.RELEASE/src/docs/marble/justorempty.png" alt="">
    * <p>
    *
    * @return a new [[SMono]] emitting current context
    * @see [[SMono.subscribe(CoreSubscriber)]]
    */
  def subscribeContext(): SMono[Context] = JMono.subscriberContext().asScala

  def raiseError[T](error: Throwable): SMono[T] = JMono.error[T](error).asScala

  /**
    * Aggregate given void publishers into a new a `Mono` that will be
    * fulfilled when all of the given `Monos` have been fulfilled. If any Mono terminates without value,
    * the returned sequence will be terminated immediately and pending results cancelled.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[SMono]].
    */
  def when(sources: Iterable[_ <: Publisher[Unit] with MapablePublisher[Unit]]): SMono[Unit] = {
    new ReactiveSMono[Unit](
      JMono.when(sources.map(s => s.map((_: Unit) => None.orNull: Void)).asJava).map((_: Void) => ())
    )
  }

  /**
    * Aggregate given publishers into a new `Mono` that will be fulfilled
    * when all of the given `sources` have been fulfilled. An error will cause
    * pending results to be cancelled and immediate error emission to the returned [[SMono]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.RC1/src/docs/marble/whent.png" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[SMono]].
    */
  def when(sources: Publisher[Unit] with MapablePublisher[Unit]*): SMono[Unit] = new ReactiveSMono[Unit](
    JMono.when(sources.map(s => s.map((_: Unit) => None.orNull: Void)).asJava).map((_: Void) => ())
  )

  def zipDelayError[T1, T2](p1: SMono[_ <: T1], p2: SMono[_ <: T2]): SMono[(T1, T2)] = {
    new ReactiveSMono[(T1, T2)](JMono.zipDelayError[T1, T2](p1.coreMono, p2.coreMono).map((t: Tuple2[T1, T2]) => tupleTwo2ScalaTuple2(t)))
  }

  def zipDelayError[T1, T2, T3](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3]): SMono[(T1, T2, T3)] = {
    new ReactiveSMono[(T1, T2, T3)](JMono.zipDelayError[T1, T2, T3](p1.coreMono, p2.coreMono, p3.coreMono).map((t: Tuple3[T1, T2, T3]) => tupleThree2ScalaTuple3(t)))
  }

  def zipDelayError[T1, T2, T3, T4](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4]): SMono[(T1, T2, T3, T4)] = {
    new ReactiveSMono[(T1, T2, T3, T4)](
      JMono.zipDelayError[T1, T2, T3, T4](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono).map((t: Tuple4[T1, T2, T3, T4]) => tupleFour2ScalaTuple4(t))
    )
  }

  def zipDelayError[T1, T2, T3, T4, T5](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4], p5: SMono[_ <: T5]): SMono[(T1, T2, T3, T4, T5)] = {
    new ReactiveSMono[(T1, T2, T3, T4, T5)](
      JMono.zipDelayError[T1, T2, T3, T4, T5](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono, p5.coreMono).map((t: Tuple5[T1, T2, T3, T4, T5]) => tupleFive2ScalaTuple5(t))
    )
  }

  def zipDelayError[T1, T2, T3, T4, T5, T6](p1: SMono[_ <: T1], p2: SMono[_ <: T2], p3: SMono[_ <: T3], p4: SMono[_ <: T4], p5: SMono[_ <: T5], p6: SMono[_ <: T6]): SMono[(T1, T2, T3, T4, T5, T6)] = new ReactiveSMono[(T1, T2, T3, T4, T5, T6)](
    JMono.zipDelayError[T1, T2, T3, T4, T5, T6](p1.coreMono, p2.coreMono, p3.coreMono, p4.coreMono, p5.coreMono, p6.coreMono).map((t: Tuple6[T1, T2, T3, T4, T5, T6]) => tupleSix2ScalaTuple6(t))
  )

  /**
    * Aggregate given publishers into a new [[SMono]] that will be
    * fulfilled when all of the given <code>sources</code> have completed. If any Publisher
    * terminates without value, the returned sequence will be terminated immediately and
    * pending results cancelled. Errors from the sources are delayed.
    * If several Publishers error, the exceptions are combined (as suppressed exceptions on a root exception).
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/master/reactor-core/src/main/java/reactor/core/publisher/doc-files/marbles/whenDelayError.svg" alt="">
    * <p>
    *
    * @param sources The sources to use.
    * @return a [[SMono]].
    */
  def whenDelayError(sources: Iterable[_ <: Publisher[_] with MapablePublisher[_]]): SMono[Unit] = new ReactiveSMono[Unit](
    JMono.whenDelayError(sources.map(s => s.map((_: Any) => None.orNull: Void)).asJava).map((_: Void) => ())
  )

  def zipDelayError[R](monos: Iterable[_ <: SMono[_]], combinator: Array[AnyRef] => _ <: R): SMono[R] = {
    new ReactiveSMono[R](JMono.zipDelayError[R](monos.map(_.asJava()).asJava, new Function[Array[AnyRef], R] {
      override def apply(t: Array[AnyRef]): R = {
        val v = t.map { v => v: AnyRef }
        combinator(v)
      }
    }))
  }

  def zipDelayError[R](combinator: Array[Any] => R, monos: SMono[_]*): SMono[R] = {
    val combinatorFunction = new Function[Array[AnyRef], R] {
      override def apply(t: Array[AnyRef]): R = {
        val v = t.map { v => v: Any }
        combinator(v)
      }
    }
    new ReactiveSMono[R](JMono.zipDelayError[R](combinatorFunction, monos.map(_.asJava()): _*))
  }

  def zip[R](combinator: Array[AnyRef] => R, monos: SMono[_]*): SMono[R] = new ReactiveSMono[R](JMono.zip(combinator, monos.map(_.asJava()).toArray: _*))

  def zip[R](monos: Iterable[_ <: SMono[_]], combinator: Array[AnyRef] => R): SMono[R] =
    new ReactiveSMono[R](JMono.zip(monos.map(_.asJava()).asJava, new Function[Array[Object], R] {
      override def apply(t: Array[Object]) = combinator(t.map { v => Option(v): Option[AnyRef] }.filterNot(_.isEmpty).map(_.getOrElse(None.orNull)))
    }))
}