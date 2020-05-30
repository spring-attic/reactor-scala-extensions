package reactor.core.scala.publisher

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}

import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.reactivestreams.Subscription
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.Disposable
import reactor.core.publisher.{BaseSubscriber, Signal, SynchronousSink, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.SMono.just
import reactor.core.scala.publisher.ScalaConverters._
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.test.scheduler.VirtualTimeScheduler
import reactor.test.{StepVerifier, StepVerifierOptions}
import reactor.util.context.Context

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.ScalaNumber
import scala.util.{Failure, Random, Success, Try}

class SMonoTest extends AnyFreeSpec with Matchers with TestSupport with IdiomaticMockito with ArgumentMatchersSugar {
  private val randomValue = Random.nextLong()

  "SMono" - {
    ".create should create a Mono" in {
      StepVerifier.create(SMono.create[Long](monoSink => monoSink.success(randomValue)))
        .expectNext(randomValue)
        .expectComplete()
        .verify()
    }

    ".defer should create a Mono with deferred Mono" in {
      StepVerifier.create(SMono.defer(() => just(randomValue)))
        .expectNext(randomValue)
        .expectComplete()
        .verify()
    }

    ".delay should create a Mono with the first element delayed according to the provided" - {
      "duration" in {
        StepVerifier.withVirtualTime(() => SMono.delay(5 days))
          .thenAwait(5 days)
          .expectNextCount(1)
          .expectComplete()
          .verify()
      }
      "duration in millis with given TimeScheduler" in {
        val vts = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(SMono.delay(50 seconds, vts))
          .`then`(() => vts.advanceTimeBy(50 seconds))
          .expectNextCount(1)
          .expectComplete()
          .verify()

      }
    }

    ".empty " - {
      "without source should create an empty Mono" in {
        StepVerifier.create(SMono.empty)
          .verifyComplete()
      }
    }

    ".firstEmitter" - {
      "with varargs should create mono that emit the first item" in {
        StepVerifier.withVirtualTime(() => SMono.firstEmitter(just(1).delaySubscription(3 seconds), just(2).delaySubscription(2 seconds)))
          .thenAwait(3 seconds)
          .expectNext(2)
          .verifyComplete()
      }
    }

    ".from" - {
      "a publisher should ensure that the publisher will emit 0 or 1 item." in {
        StepVerifier.create(SMono.fromPublisher(SFlux.just(1, 2, 3, 4, 5)))
          .expectNext(1)
          .expectComplete()
          .verify()
      }

      "a callable should ensure that Mono will return a value from the Callable" in {
        StepVerifier.create(SMono.fromCallable(() => randomValue))
          .expectNext(randomValue)
          .expectComplete()
          .verify()
      }

      "source direct should return mono of the source" in {
        StepVerifier.create(SMono.fromDirect(SFlux.just(1, 2, 3)))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }

      "a future should result Mono that will return the value from the future object" in {
        import scala.concurrent.ExecutionContext.Implicits.global
        StepVerifier.create(SMono.fromFuture(Future {
          randomValue
        }))
          .expectNext(randomValue)
          .verifyComplete()
      }

      "a Try should result SMono that when it is a" - {
        "Success will emit the value of the Try" in {
          def aSuccess = Try(randomValue)
          StepVerifier.create(SMono.fromTry(aSuccess))
            .expectNext(randomValue)
            .verifyComplete()
        }
        "Failure will emit onError with the exception" in {
          def aFailure = Try(throw new RuntimeException("error message"))
          StepVerifier.create(SMono.fromTry(aFailure))
            .expectErrorMessage("error message")
            .verify()
        }
      }
    }

    ".ignoreElements should ignore all elements from a publisher and just react on completion signal" in {
      StepVerifier.create(SMono.ignoreElements(just(randomValue)))
        .expectComplete()
        .verify()
    }

    ".just should emit the specified item" in {
      StepVerifier.create(just(randomValue))
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".justOrEmpty" - {
      "with Option should" - {
        "emit the specified item if the option is not empty" in {
          StepVerifier.create(SMono.justOrEmpty(Option(randomValue)))
            .expectNext(randomValue)
            .verifyComplete()
        }
        "just react on completion signal if the option is empty" in {
          StepVerifier.create(SMono.justOrEmpty(Option.empty))
            .expectComplete()
            .verify()
        }
      }
      "with data should" - {
        "emit the specified item if it is not null" in {
          val mono = SMono.justOrEmpty(randomValue)
          StepVerifier.create(mono)
            .expectNext(randomValue)
            .verifyComplete()
        }
        "just react on completion signal if it is null" in {
          val nullData:Any = null
          val mono = SMono.justOrEmpty(nullData)
          StepVerifier.create(mono)
            .expectComplete()
            .verify()
        }
      }
    }
    
    ".metrics should be a nop since Micrometer is not on the classpath" in {
      val mono = JMono.just("plain awesome")
      mono.asScala.metrics.coreMono shouldBe theSameInstanceAs(mono)
    }

    ".never will never signal any data, error or completion signal" in {
      StepVerifier.create(SMono.never)
        .expectSubscription()
        .expectNoEvent(1 second)
    }

    ".name should give name to this sequence" in {
      val name = "one two three four"
      val scannable: Scannable = Scannable.from(Option(just(randomValue).name(name)))
      scannable.name shouldBe name
    }

    ".sequenceEqual should" - {
      "emit Boolean.TRUE when both publisher emit the same value" in {
        StepVerifier.create(SMono.sequenceEqual(just(1), just(1)))
          .expectNext(true)
          .verifyComplete()
      }
      "emit true when both publisher emit the same value according to the isEqual function" in {
        val mono = SMono.sequenceEqual(just(10), just(100), (t1: Int, t2: Int) => t1 % 10 == t2 % 10)
        StepVerifier.create(mono)
          .expectNext(true)
          .verifyComplete()
      }
      "emit true when both publisher emit the same value according to the isEqual function with bufferSize" in {
        val mono = SMono.sequenceEqual(just(10), just(100), (t1: Int, t2: Int) => t1 % 10 == t2 % 10, 2)
        StepVerifier.create(mono)
          .expectNext(true)
          .verifyComplete()

      }
    }

    ".raiseError should create Mono that emit error" in {
      StepVerifier.create(SMono.raiseError(new RuntimeException("runtime error")))
        .expectError(classOf[RuntimeException])
        .verify()
    }

    ".when" - {
      "with iterable" - {
        "of publisher of unit should return when all of the sources has fulfilled" in {
          val completed = new ConcurrentHashMap[String, Boolean]()
          val mono = SMono.when(Iterable(
            just({
              completed.put("first", true)
              ()
            }),
            just({
              completed.put("second", true)
              ()
            })
          ))
          StepVerifier.create(mono)
            .expectComplete()
          completed should contain key "first"
          completed should contain key "second"
        }
      }

      "with varargs of publisher should return when all of the resources has fulfilled" in {
        val completed = new ConcurrentHashMap[String, Boolean]()
        val sources = Seq(just({
          completed.put("first", true)
          ()
        }),
          just[Unit]({
            completed.put("second", true)
            ()
          })
        )
        StepVerifier.create(SMono.when(sources.toArray: _*))
          .expectComplete()
        completed should contain key "first"
        completed should contain key "second"
      }
    }

    ".zipDelayError" - {
      "with p1 and p2 should merge when both Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(just(1), just("one")))
          .expectNext((1, "one"))
          .verifyComplete()
      }

      "with p1, p2 and p3 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(just(1), just("one"), just(1L)))
          .expectNext((1, "one", 1L))
          .verifyComplete()
      }

      "with p1, p2, p3 and p4 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(just(1), just(2), just(3), just(4)))
          .expectNext((1, 2, 3, 4))
          .verifyComplete()
      }

      "with p1, p2, p3, p4 and p5 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(just(1), just(2), just(3), just(4), just(5)))
          .expectNext((1, 2, 3, 4, 5))
          .verifyComplete()
      }

      "with p1, p2, p3, p4, p5 and p6 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(just(1), just(2), just(3), just(4), just(5), just(6)))
          .expectNext((1, 2, 3, 4, 5, 6))
          .verifyComplete()
      }

      "with iterable" - {
        "of publisher of unit should return when all of the sources has fulfilled" in {
          val completed = new ConcurrentHashMap[String, Boolean]()
          val mono: SMono[Unit] = SMono.whenDelayError(Iterable(
            just({
              completed.put("first", true)
              1
            }),
            just({
              completed.put("second", true)
              2
            })
          ))
          StepVerifier.create(mono)
            .expectComplete()
          completed should contain key "first"
          completed should contain key "second"
        }

        "of combinator function and monos should emit the value after combined by combinator function" in {
          StepVerifier.create(SMono.zipDelayError((values: Array[Any]) => s"${values(0).toString}-${values(1).toString}", just(1), just("one")))
            .expectNext("1-one")
            .verifyComplete()
        }
      }
    }

    ".zip" - {
      val combinator: Array[AnyRef] => String = { datas => datas.map(_.toString).foldLeft("") { (acc, v) => if (acc.isEmpty) v else s"$acc-$v" } }
      "with combinator function and varargs of mono should fullfill when all Monos are fulfilled" in {
        val mono = SMono.zip(combinator, just(1), just(2))
        StepVerifier.create(mono)
          .expectNext("1-2")
          .verifyComplete()
      }
      "with combinator function and Iterable of mono should fulfill when all Monos are fulfilled" in {
        val mono = SMono.zip(Iterable(just(1), just("2")), combinator)
        StepVerifier.create(mono)
          .expectNext("1-2")
          .verifyComplete()
      }
    }

    ".and" - {
      "should combine this mono and the other" in {
        StepVerifier.create(just(1) and just(2))
          .verifyComplete()
      }
    }

    ".as should transform the Mono to whatever the transformer function is provided" in {
      val mono = just(randomValue)

      StepVerifier.create(mono.as(m => SFlux.fromPublisher(m)))
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".asJava should convert to java" in {
      just(randomValue).asJava() shouldBe a[JMono[_]]
    }

    ".asScala should transform Mono to SMono" in {
      JMono.just(randomValue).asScala shouldBe an[SMono[_]]
    }

    ".block" - {
      "should block the mono to get the value" in {
        just(randomValue).block() shouldBe randomValue
      }
      "with duration should block the mono up to the duration" in {
        just(randomValue).block(10 seconds) shouldBe randomValue
      }
    }

    ".blockOption" - {
      "without duration" - {
        "should block the mono to get value" in {
          just(randomValue).blockOption() shouldBe Some(randomValue)
        }
        "should return None if mono is empty" in {
          SMono.empty.blockOption() shouldBe None
        }
      }
      "with duration" - {
        "should block the mono up to the duration" in {
          just(randomValue).blockOption(10 seconds) shouldBe Some(randomValue)
        }
        "shouldBlock the mono up to the duration and return None" in {
          StepVerifier.withVirtualTime(() => just(SMono.empty.blockOption(10 seconds)))
            .thenAwait(10 seconds)
            .expectNext(None)
            .verifyComplete()
        }
      }
    }

    ".cast (deprecated) should cast the underlying value" in {
      val number = SMono.just(BigDecimal("123")).cast(classOf[ScalaNumber]).block()
      number shouldBe a[ScalaNumber]
    }
    ".cast should cast the underlying value" in {
      val number = SMono.just(BigDecimal("123")).cast[ScalaNumber].block()
      number shouldBe a[ScalaNumber]
    }

    ".cache" - {
      "should cache the value" in {
        val queue = new ArrayBlockingQueue[Int](1)
        queue.put(1)
        val mono = SMono.create[Int](sink => {
          sink.success(queue.poll())
        }).cache()
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
      }
      "with ttl cache the value up to specific time" in {
        import reactor.test.scheduler.VirtualTimeScheduler
        val timeScheduler = VirtualTimeScheduler.getOrSet
        val queue = new ArrayBlockingQueue[Int](1)
        queue.put(1)
        val mono = SMono.create[Int](sink => {
          sink.success(queue.poll())
        }).cache(1 minute)
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
        timeScheduler.advanceTimeBy(59 second)
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
        timeScheduler.advanceTimeBy(2 minute)
        StepVerifier.create(mono)
          .verifyComplete()
      }
    }

    ".cancelOn should cancel the subscriber on a particular scheduler" in {
      val jMono = spy(JMono.just(1))
      new ReactiveSMono[Int](jMono).cancelOn(Schedulers.immediate())
      jMono.cancelOn(any[Scheduler]) was called
    }

    ".compose (deprecated) should defer creating the target mono type" in {
      StepVerifier.create(just(1).compose[String](m => SFlux.fromPublisher(m.map(_.toString))))
        .expectNext("1")
        .verifyComplete()
    }
    ".transformDeferred should defer creating the target mono type" in {
      StepVerifier.create(SMono.just(1).transformDeferred(m => SFlux.fromPublisher(m.map(_.toString))))
        .expectNext("1")
        .verifyComplete()
    }

    ".concatWith should concatenate mono with another source" in {
      StepVerifier.create(just(1).concatWith(just(2)))
        .expectNext(1)
        .expectNext(2)
        .verifyComplete()
    }

    "++ should concatenate mono with another source" in {
      StepVerifier.create(just(1) ++ just(2))
        .expectNext(1)
        .expectNext(2)
        .verifyComplete()
    }

    ".defaultIfEmpty should use the provided default value if the mono is empty" in {
      StepVerifier.create(SMono.empty.defaultIfEmpty(-1))
        .expectNext(-1)
        .verifyComplete()
    }

    ".delayElement" - {
      "should delay the element" in {
        StepVerifier.withVirtualTime(() => just(randomValue).delayElement(5 seconds))
          .thenAwait(5 seconds)
          .expectNext(randomValue)
          .verifyComplete()
      }
      "with timer should delay using timer" in {
        StepVerifier.withVirtualTime(() => just(randomValue).delayElement(5 seconds, Schedulers.immediate()))
          .thenAwait(5 seconds)
          .expectNext(randomValue)
          .verifyComplete()
      }
    }

    ".delaySubscription" - {
      "with delay duration should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => just(1).delaySubscription(1 hour))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()
      }
      "with delay duration and scheduler should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => just(1).delaySubscription(1 hour, Schedulers.single()))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()
      }
      "with another publisher should delay the current subscription until the other publisher completes" in {
        StepVerifier.withVirtualTime(() => just(1).delaySubscription(just("one").delaySubscription(1 hour)))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()

      }
    }

    ".delayUntil should delay until the other provider terminate" in {
      StepVerifier.withVirtualTime(() => just(randomValue).delayUntil(_ => SFlux.just(1, 2).delayElements(2 seconds)))
        .thenAwait(4 seconds)
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".dematerialize should dematerialize the underlying mono" in {
      StepVerifier.create(just(Signal.next(randomValue)).dematerialize())
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".doAfterSuccessOrError (deprecated) should call the callback function after the mono is terminated" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(just(randomValue)
        .doAfterSuccessOrError { t =>
          atomicBoolean.compareAndSet(false, true) shouldBe true
          t shouldBe Success(randomValue)
        })
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
      val exception = new RuntimeException
      StepVerifier.create(SMono.raiseError[Long](exception)
        .doAfterSuccessOrError { t =>
          atomicBoolean.compareAndSet(true, false) shouldBe true
          t shouldBe Failure(exception)
        })
        .expectError()
        .verify()
      atomicBoolean.get() shouldBe false
    }

    ".doAfterTerminate should call the callback function after the mono is terminated" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(just(randomValue).doAfterTerminate(() => atomicBoolean.compareAndSet(false, true)))
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doFinally should call the callback" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(just(randomValue)
        .doFinally(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doOnCancel should call the callback function when the subscription is cancelled" in {
      val atomicBoolean = new AtomicBoolean(false)
      val mono = SMono.delay(1 minute)
        .doOnCancel(() => {
          atomicBoolean.compareAndSet(false, true) shouldBe true
        })

      val subscriptionReference = new AtomicReference[Subscription]()
      mono.subscribe(new BaseSubscriber[Long] {
        override def hookOnSubscribe(subscription: Subscription): Unit = {
          subscriptionReference.set(subscription)
          subscription.request(1)
        }

        override def hookOnNext(value: Long): Unit = ()
      })
      subscriptionReference.get().cancel()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doOnNext should call the callback function when the mono emit data successfully" in {
      val atomicLong = new AtomicLong()
      StepVerifier.create(just(randomValue)
        .doOnNext(t => atomicLong.compareAndSet(0, t)))
        .expectNext(randomValue)
        .verifyComplete()
      atomicLong.get() shouldBe randomValue
    }

    ".doOnSuccess should call the callback function when the mono completes successfully" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(SMono.empty[Int]
        .doOnSuccess(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doOnError" - {
      "with callback function should call the callback function when the mono encounter error" in {
        val atomicBoolean = new AtomicBoolean(false)
        StepVerifier.create(SMono.raiseError(new RuntimeException())
          .doOnError(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
          .expectError(classOf[RuntimeException])
          .verify()
        atomicBoolean shouldBe Symbol("get")
      }
    }

    ".doOnRequest should call the callback function when subscriber request data" in {
      val atomicLong = new AtomicLong(0)
      just(randomValue)
        .doOnRequest(l => atomicLong.compareAndSet(0, l)).subscribe(new BaseSubscriber[Long] {
        override def hookOnSubscribe(subscription: Subscription): Unit = {
          subscription.request(1)
          ()
        }

        override def hookOnNext(value: Long): Unit = ()
      })
      atomicLong.get() shouldBe 1
    }

    ".doOnSubscribe should call the callback function when the mono is subscribed" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(just(randomValue)
        .doOnSubscribe(_ => atomicBoolean.compareAndSet(false, true)))
        .expectNextCount(1)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doOnTerminate should do something on terminate" in {
      val atomicLong = new AtomicLong()
      StepVerifier.create(just(randomValue).doOnTerminate { () => atomicLong.set(randomValue) })
        .expectNext(randomValue)
        .expectComplete()
        .verify()
      atomicLong.get() shouldBe randomValue
    }

    ".elapsed" - {
      "should provide the time elapse when this mono emit value" in {
        StepVerifier.withVirtualTime(() => just(randomValue).delaySubscription(1 second).elapsed(), 1)
          .thenAwait(1 second)
          .expectNextMatches((t: (Long, Long)) => t match {
            case (time, data) => time >= 1000 && data == randomValue
          })
          .verifyComplete()
      }
      "with TimedScheduler should provide the time elapsed using the provided scheduler when this mono emit value" in {
        val virtualTimeScheduler = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(just(randomValue)
          .delaySubscription(1 second, virtualTimeScheduler)
          .elapsed(virtualTimeScheduler), 1)
          .`then`(() => virtualTimeScheduler.advanceTimeBy(1 second))
          .expectNextMatches((t: (Long, Long)) => t match {
            case (time, data) => time >= 1000 && data == randomValue
          })
          .verifyComplete()
      }
    }

    ".expandDeep" - {
      "should expand the mono" in {
        StepVerifier.create(just("a").expandDeep(s => just(s"$s$s")).take(3))
          .expectNext("a", "aa", "aaaa")
          .verifyComplete()
      }
      "with capacity hint should expand the mono" in {
        StepVerifier.create(just("a").expandDeep(s => just(s"$s$s"), 10).take(3))
          .expectNext("a", "aa", "aaaa")
          .verifyComplete()
      }
    }

    ".expand" - {
      "should expand the mono" in {
        StepVerifier.create(just("a").expand(s => just(s"$s$s")).take(3))
          .expectNext("a", "aa", "aaaa")
          .verifyComplete()
      }
      "with capacity hint should expand the mono" in {
        StepVerifier.create(just("a").expand(s => just(s"$s$s"), 10).take(3))
          .expectNext("a", "aa", "aaaa")
          .verifyComplete()
      }
    }

    ".filter should filter the value of mono where it pass the provided predicate" in {
      StepVerifier.create(just(10)
        .filter(i => i < 10))
        .verifyComplete()
    }

    ".filterWhen should replay the value of mono if the first item emitted by the test is true" in {
      StepVerifier.create(just(10).filterWhen((i: Int) => just(i % 2 == 0)))
        .expectNext(10)
        .verifyComplete()
    }

    ".flatMap should flatmap the provided mono" in {
      StepVerifier.create(SMono.just(randomValue).flatMap(l => SMono.just(l.toString)))
        .expectNext(randomValue.toString)
        .verifyComplete()
    }

    ".flatMapMany" - {
      "with a single mapper should flatmap the value mapped by the provided mapper" in {
        StepVerifier.create(just(1).flatMapMany(i => SFlux.just(i, i * 2)))
          .expectNext(1, 2)
          .verifyComplete()
      }
      "with mapperOnNext, mapperOnError and mapperOnComplete should mapped each individual event into values emitted by flux" in {
        StepVerifier.create(just(1)
          .flatMapMany(
            _ => just("one"),
            _ => just("error"),
            () => just("complete")
          ))
          .expectNext("one", "complete")
          .verifyComplete()
      }
    }

    ".flatMapIterable should flatmap the value mapped by the provided mapper" in {
      StepVerifier.create(just("one").flatMapIterable(str => str.toCharArray))
        .expectNext('o', 'n', 'e')
        .verifyComplete()
    }

    ".flux should convert this mono into a flux" in {
      val flux = just(randomValue).flux()
      StepVerifier.create(flux)
        .expectNext(randomValue)
        .verifyComplete()
      flux shouldBe an[SFlux[Long]]
    }

    ".hasElement should convert to another Mono that emit" - {
      "true if it has element" in {
        StepVerifier.create(just(1).hasElement)
          .expectNext(true)
          .verifyComplete()
      }
      "false if it is empty" in {
        StepVerifier.create(SMono.empty.hasElement)
          .expectNext(false)
          .verifyComplete()
      }
    }

    ".handle should handle onNext, onError and onComplete" in {
      StepVerifier.create(just(randomValue)
        .handle((_: Long, s: SynchronousSink[String]) => {
          s.next("One")
          s.complete()
        }))
        .expectNext("One")
        .verifyComplete()
    }

    ".ignoreElement should only emit termination event" in {
      StepVerifier.create(just(randomValue).ignoreElement)
        .verifyComplete()
    }

    ".map should map the type of Mono from T to R" in {
      StepVerifier.create(just(randomValue).map(_.toString))
        .expectNext(randomValue.toString)
        .expectComplete()
        .verify()
    }

    ".mapError" - {
      class MyCustomException(val message: String) extends Exception(message)
      "with mapper should map the error to another error" in {
        StepVerifier.create(SMono.raiseError[Int](new RuntimeException("runtimeException"))
          .onErrorMap { case t: Throwable => new MyCustomException(t.getMessage) })
          .expectErrorMatches((t: Throwable) => {
            t.getMessage shouldBe "runtimeException"
            t should not be a[RuntimeException]
            t shouldBe a[MyCustomException]
            true
          })
          .verify()
      }
      "with an error type and mapper should" - {
        "map the error to another type if the exception is according to the provided type" in {
          StepVerifier.create(SMono.raiseError[Int](new RuntimeException("runtimeException"))
            .onErrorMap { case t: RuntimeException => new MyCustomException(t.getMessage) })
            .expectErrorMatches((t: Throwable) => {
              t.getMessage shouldBe "runtimeException"
              t should not be a[RuntimeException]
              t shouldBe a[MyCustomException]
              true
            })
            .verify()
        }
        "not map the error if the exception is not the type of provided exception class" in {
          StepVerifier.create(SMono.raiseError[Int](new Exception("runtimeException"))
            .onErrorMap {
              case t: RuntimeException => new MyCustomException(t.getMessage)
            })
            .expectErrorMatches((t: Throwable) => {
              t.getMessage shouldBe "runtimeException"
              t should not be a[MyCustomException]
              t shouldBe a[Exception]
              true
            })
            .verify()
        }
      }
      "with a predicate and mapper should" - {
        "map the error to another type if the predicate returns true" in {
          StepVerifier.create(SMono.raiseError[Int](new RuntimeException("should map"))
            .onErrorMap { case t: Throwable if t.getMessage == "should map" => new MyCustomException(t.getMessage) })
            .expectError(classOf[MyCustomException])
            .verify()
        }
        "not map the error to another type if the predicate returns false" in {
          StepVerifier.create(SMono.raiseError[Int](new RuntimeException("should not map"))
            .onErrorMap { case t: Throwable if t.getMessage == "should map" => new MyCustomException(t.getMessage) })
            .expectError(classOf[RuntimeException])
            .verify()
        }
      }
    }

    ".materialize should convert the mono into a mono that emit its signal" in {
      StepVerifier.create(just(randomValue).materialize())
        .expectNext(Signal.next(randomValue))
        .verifyComplete()
    }

    ".mergeWith should convert this mono to flux with value emitted from this mono followed by the other" in {
      StepVerifier.create(just(1).mergeWith(just(2)))
        .expectNext(1, 2)
        .verifyComplete()
    }

    ".ofType (deprecated) should" - {
      "convert the Mono value type to the provided type if it can be casted" in {
        StepVerifier.create(just(BigDecimal("1")).ofType(classOf[ScalaNumber]))
          .expectNextCount(1)
          .verifyComplete()
      }
      "ignore the Mono value if it can't be casted" in {
        StepVerifier.create(just(1).ofType(classOf[String]))
          .verifyComplete()
      }
    }
    ".ofType should" - {
      "convert the Mono value type to the provided type if it can be casted" in {
        StepVerifier.create(SMono.just(BigDecimal("1")).ofType[ScalaNumber])
          .expectNextCount(1)
          .verifyComplete()
      }
      "ignore the Mono value if it can't be casted" in {
        StepVerifier.create(SMono.just(1).ofType[String])
          .verifyComplete()
      }
    }

    ".onErrorRecover" - {
      "should recover with a Mono of element that has been recovered" in {
        StepVerifier.create(SMono.raiseError(new RuntimeException("oops"))
          .onErrorRecover { case _ => Truck(5) })
          .expectNext(Truck(5))
          .verifyComplete()
      }
    }

    ".onErrorResume" - {
      "will fallback to the provided value when error happens" in {
        StepVerifier.create(SMono.raiseError(new RuntimeException()).onErrorResume(_ => just(-1)))
          .expectNext(-1)
          .verifyComplete()
      }
      "with class type and fallback function will fallback to the provided value when the exception is of provided type" in {
        StepVerifier.create(SMono.raiseError(new RuntimeException()).onErrorResume {
          case _: Exception => just(-1)
        })
          .expectNext(-1)
          .verifyComplete()

        StepVerifier.create(SMono.raiseError(new Exception()).onErrorResume {
          case _: RuntimeException => just(-1)
        })
          .expectError(classOf[Exception])
          .verify()
      }
      "with predicate and fallback function will fallback to the provided value when the predicate returns true" in {
        StepVerifier.create(SMono.raiseError(new RuntimeException("fallback")).onErrorResume {
          case t if t.getMessage == "fallback" => just(-1)
        })
          .expectNext(-1)
          .verifyComplete()
      }
    }

    ".or should return Mono that emit the value between the two Monos that is emited first" in {
      StepVerifier.create(SMono.delay(5 seconds).or(just(2)))
        .expectNext(2)
        .verifyComplete()
    }

    ".publish should share and may transform it and consume it as many times as necessary without causing" +
      "multiple subscription" in {
      val mono = just(randomValue).publish[String](ml => ml.map(l => l.toString))

      val counter = new AtomicLong()

      val subscriber = new BaseSubscriber[String] {
        override def hookOnSubscribe(subscription: Subscription): Unit = {
          subscription.request(1)
          counter.incrementAndGet()
        }

        override def hookOnNext(value: String): Unit = ()
      }
      mono.subscribe(subscriber)
      mono.subscribe(subscriber)
      counter.get() shouldBe 1
    }

    ".repeat" - {
      "should return flux that repeat the value from this mono" in {
        StepVerifier.create(just(randomValue).repeat().take(3))
          .expectNext(randomValue, randomValue, randomValue)
          .verifyComplete()
      }
      "with boolean predicate should repeat the value from this mono as long as the predicate returns true" in {
        val counter = new AtomicLong()
        val flux = just(randomValue)
          .repeat(predicate = () => counter.get() < 3)
        val buffer = new LinkedBlockingQueue[Long]()
        val latch = new CountDownLatch(1)
        flux.subscribe(new BaseSubscriber[Long] {
          override def hookOnSubscribe(subscription: Subscription): Unit = subscription.request(Long.MaxValue)

          override def hookOnNext(value: Long): Unit = {
            counter.incrementAndGet()
            buffer.put(value)
          }

          override def hookOnComplete(): Unit = latch.countDown()
        })
        if (latch.await(1, TimeUnit.SECONDS))
          buffer should have size 3
        else
          fail("no completion signal is detected")

      }
      "with number of repeat should repeat value from this value as many as the provided parameter" in {
        StepVerifier.create(just(randomValue).repeat(5))
          //          this is a bug in https://github.com/reactor/reactor-core/issues/1252. It should only be 5 in total
          .expectNext(randomValue, randomValue, randomValue, randomValue, randomValue, randomValue)
          .verifyComplete()
      }
      "with number of repeat and predicate should repeat value from this value as many as provided parameter and as" +
        "long as the predicate returns true" in {
        val counter = new AtomicLong()
        val flux = just(randomValue).repeat(5, () => counter.get() < 3)
        val buffer = new LinkedBlockingQueue[Long]()
        val latch = new CountDownLatch(1)
        flux.subscribe(new BaseSubscriber[Long] {
          override def hookOnSubscribe(subscription: Subscription): Unit = subscription.request(Long.MaxValue)

          override def hookOnNext(value: Long): Unit = {
            counter.incrementAndGet()
            buffer.put(value)
          }

          override def hookOnComplete(): Unit = latch.countDown()
        })
        if (latch.await(1, TimeUnit.SECONDS))
          buffer should have size 3
        else
          fail("no completion signal is detected")
      }
    }

    ".repeatWhen should emit the value of this mono accompanied by the publisher" in {
      StepVerifier.create(just(randomValue).repeatWhen((_: SFlux[Long]) => SFlux.just[Long](10, 20)))
        .expectNext(randomValue, randomValue, randomValue)
        .verifyComplete()
    }

    ".repeatWhenEmpty should emit resubscribe to this mono when the companion is empty" in {
      val counter = new AtomicInteger(0)
      StepVerifier.create(SMono.empty.doOnSubscribe(_ => counter.incrementAndGet()).repeatWhenEmpty((_: SFlux[Long]) => SFlux.just(-1, -2, -3)))
        .verifyComplete()
      counter.get() shouldBe 4
    }

    ".single" - {
      "should enforce the existence of element" in {
        StepVerifier.create(just(randomValue).single())
          .expectNext(randomValue)
          .verifyComplete()
      }
      "should throw exception if it is empty" in {
        StepVerifier.create(SMono.empty.single())
          .expectError(classOf[NoSuchElementException])
          .verify()
      }
    }

    ".subscribe" - {
      "without parameter should return Disposable" in {
        val x = just(randomValue).subscribe()
        x shouldBe a[Disposable]
      }
      "with consumer should invoke the consumer" in {
        val counter = new CountDownLatch(1)
        val disposable = just(randomValue).subscribe(_ => counter.countDown())
        disposable shouldBe a[Disposable]
        counter.await(1, TimeUnit.SECONDS) shouldBe true
      }
      "with consumer and error consumer should invoke the error consumer when error happen" in {
        val counter = new CountDownLatch(1)
        val disposable = SMono.raiseError[Any](new RuntimeException()).subscribe(_ => (), _ => counter.countDown())
        disposable shouldBe a[Disposable]
        counter.await(1, TimeUnit.SECONDS) shouldBe true
      }
      "with consumer, error consumer and completeConsumer should invoke the completeConsumer when it's complete" in {
        val counter = new CountDownLatch(2)
        val disposable = just(randomValue).subscribe(_ => counter.countDown(), _ => (), counter.countDown())
        disposable shouldBe a[Disposable]
        counter.await(1, TimeUnit.SECONDS) shouldBe true
      }
      "with consumer, error consumer, completeConsumer and subscriptionConsumer should invoke the subscriptionConsumer when there is subscription" in {
        val counter = new CountDownLatch(3)
        val disposable = just(randomValue).subscribe(_ => counter.countDown(), _ => (), counter.countDown(), s => {
          s.request(1)
          counter.countDown()
        })
        disposable shouldBe a[Disposable]
        counter.await(1, TimeUnit.SECONDS) shouldBe true
      }
    }

    ".subscribeContext should pass context properly" in {
      val key = "message"
      val r: SMono[String] = just("Hello")
          .flatMap(s => SMono.subscribeContext()
          .map(ctx => s"$s ${ctx.get(key)}"))
          .subscriberContext(ctx => ctx.put(key, "World"))

      StepVerifier.create(r)
          .expectNext("Hello World")
          .verifyComplete()

      StepVerifier.create(just(1).map(i => i + 10),
        StepVerifierOptions.create().withInitialContext(Context.of("foo", "bar")))
        .expectAccessibleContext()
        .contains("foo", "bar")
        .`then`()
        .expectNext(11)
        .verifyComplete()
    }

    ".switchIfEmpty with alternative will emit the value from alternative Mono when this mono is empty" in {
      StepVerifier.create(SMono.empty.switchIfEmpty(just(-1)))
        .expectNext(-1)
        .verifyComplete()
    }

    ".tag should tag the Mono and accessible from Scannable" in {
      val mono = just(randomValue).tag("integer", "one, two, three")
      Scannable.from(Option(mono)).tags shouldBe Stream("integer" -> "one, two, three")
    }

    ".take" - {
      "should complete after duration elapse" in {
        StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).take(5 seconds))
          .thenAwait(5 seconds)
          .verifyComplete()
      }
      "with duration and scheduler should complete after duration elapse" in {
        StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).take(5 seconds, Schedulers.parallel()))
          .thenAwait(5 seconds)
          .verifyComplete()
      }
    }

    ".takeUntilOther should complete if the companion publisher emit any signal first" in {
      StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).takeUntilOther(just("a")))
        .verifyComplete()
    }

    ".then" - {
      "without parameter should only replays complete and error signals from this mono" in {
        StepVerifier.create(just(randomValue).`then`())
          .verifyComplete()
      }
      "with other mono should ignore element from this mono and transform its completion signal into emission and " +
        "completion signal of the provided mono" in {
        StepVerifier.create(just(randomValue).`then`(just("1")))
          .expectNext("1")
          .verifyComplete()
      }
    }

    ".thenEmpty should complete this mono then for a supplied publisher to also complete" in {
      val latch = new CountDownLatch(1)
      val mono = just(randomValue)
        .doOnSuccess(_ => latch.countDown())
        .thenEmpty(SMono.empty)
      StepVerifier.create(mono)
        .verifyComplete()
      latch.await(1, TimeUnit.SECONDS) shouldBe true
    }

    ".thenMany should ignore the element from this mono and transform the completion signal into a Flux that will emit " +
      "from the provided publisher when the publisher is provided " - {
      "directly" in {
        StepVerifier.create(just(randomValue).thenMany(SFlux.just(1, 2, 3)))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".timeout" - {
      "should raise TimeoutException after duration elapse" in {
        StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).timeout(5 seconds))
          .thenAwait(5 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "should fallback to the provided mono if the value doesn't arrive in given duration" in {
        StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).timeout(5 seconds, Option(just(1L))))
          .thenAwait(5 seconds)
          .expectNext(1)
          .verifyComplete()
      }
      "with timeout and timer should signal TimeoutException if the item does not arrive before a given period" in {
        val timer = VirtualTimeScheduler.getOrSet()
        StepVerifier.withVirtualTime(() => SMono.delay(10 seconds).timeout(5 seconds, timer = timer), () => timer, 1)
          .thenAwait(5 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "should raise TimeoutException if this mono has not emit value when the provided publisher has emit value" in {
        val mono = SMono.delay(10 seconds).timeoutWhen(just("whatever"))
        StepVerifier.create(mono)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "should fallback to the provided fallback mono if this mono does not emit value when the provided publisher emits value" in {
        val mono = SMono.delay(10 seconds).timeoutWhen(just("whatever"), Option(just(-1L)))
        StepVerifier.create(mono)
          .expectNext(-1)
          .verifyComplete()
      }
      "with timeout, fallback and timer should fallback to the given mono if the item does not arrive before a given period" in {
        val timer = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(SMono.delay(10 seconds, timer)
          .timeout(5 seconds, Option(just(-1)), timer), 1)
          .`then`(() => timer.advanceTimeBy(5 seconds))
          .expectNext(-1)
          .verifyComplete()
      }
    }

    ".transform should transform this mono in order to generate a target mono" in {
      StepVerifier.create(just(randomValue).transform(ml => just(ml.block().toString)))
        .expectNext(randomValue.toString)
        .verifyComplete()
    }

    ".apply should convert to scala" in {
      val mono = SMono(JMono.just(randomValue))
      mono shouldBe a[SMono[_]]
    }
  }
}