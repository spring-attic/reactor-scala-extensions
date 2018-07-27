package reactor.core.scala.publisher

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap}

import org.mockito.Mockito.spy
import org.mockito.{ArgumentMatchers, Mockito}
import org.reactivestreams.Subscription
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{BaseSubscriber, Signal, Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.Mono.just
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.ScalaNumber
import scala.util.{Failure, Random, Success}

class SMonoTest extends FreeSpec with Matchers {
  private val randomValue = Random.nextLong()

  "SMono" - {
    ".create should create a Mono" in {
      StepVerifier.create(SMono.create[Long](monoSink => monoSink.success(randomValue)))
        .expectNext(randomValue)
        .expectComplete()
        .verify()
    }

    ".defer should create a Mono with deferred Mono" in {
      StepVerifier.create(SMono.defer(() => SMono.just(randomValue)))
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
        StepVerifier.create(Mono.empty)
          .verifyComplete()
      }
    }

    ".firstEmitter" - {
      "with varargs should create mono that emit the first item" in {
        StepVerifier.withVirtualTime(() => SMono.firstEmitter(SMono.just(1).delaySubscription(3 seconds), SMono.just(2).delaySubscription(2 seconds)))
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
        StepVerifier.create(SMono.fromDirect(Flux.just(1, 2, 3)))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }

      "a future should result Mono that will return the value from the future object" in {
        import scala.concurrent.ExecutionContext.Implicits.global
        StepVerifier.create(SMono.fromFuture(Future[Long] {
          randomValue
        }))
          .expectNext(randomValue)
          .verifyComplete()
      }
    }

    ".ignoreElements should ignore all elements from a publisher and just react on completion signal" in {
      StepVerifier.create(SMono.ignoreElements(SMono.just(randomValue)))
        .expectComplete()
        .verify()
    }

    ".just should emit the specified item" in {
      StepVerifier.create(SMono.just(randomValue))
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
      }
    }

    ".never will never signal any data, error or completion signal" in {
      StepVerifier.create(SMono.never)
        .expectSubscription()
        .expectNoEvent(1 second)
    }

    ".name should call the underlying Mono.name method" in {
      val name = "one two three four"
      val scannable: Scannable = Scannable.from(Option(SMono.just(randomValue).name(name)))
      scannable.name shouldBe name
    }

    ".sequenceEqual should" - {
      "emit Boolean.TRUE when both publisher emit the same value" in {
        StepVerifier.create(SMono.sequenceEqual(just(1), just(1)))
          .expectNext(true)
          .verifyComplete()
      }
      "emit true when both publisher emit the same value according to the isEqual function" in {
        val mono = SMono.sequenceEqual[Int](just(10), just(100), (t1: Int, t2: Int) => t1 % 10 == t2 % 10)
        StepVerifier.create(mono)
          .expectNext(true)
          .verifyComplete()
      }
      "emit true when both publisher emit the same value according to the isEqual function with bufferSize" in {
        val mono = SMono.sequenceEqual[Int](just(10), just(100), (t1: Int, t2: Int) => t1 % 10 == t2 % 10, 2)
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
            SMono.just[Unit]({
              completed.put("first", true)
            }),
            SMono.just[Unit]({
              completed.put("second", true)
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
        val sources = Seq(just[Unit]({
          completed.put("first", true)
        }),
          just[Unit]({
            completed.put("second", true)
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
        StepVerifier.create(SMono.zipDelayError(SMono.just(1), SMono.just("one")))
          .expectNext((1, "one"))
          .verifyComplete()
      }

      "with p1, p2 and p3 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(SMono.just(1), SMono.just("one"), SMono.just(1L)))
          .expectNext((1, "one", 1L))
          .verifyComplete()
      }

      "with p1, p2, p3 and p4 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(SMono.just(1), SMono.just(2), SMono.just(3), SMono.just(4)))
          .expectNext((1, 2, 3, 4))
          .verifyComplete()
      }

      "with p1, p2, p3, p4 and p5 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(SMono.just(1), SMono.just(2), SMono.just(3), SMono.just(4), SMono.just(5)))
          .expectNext((1, 2, 3, 4, 5))
          .verifyComplete()
      }

      "with p1, p2, p3, p4, p5 and p6 should merge when all Monos are fulfilled" in {
        StepVerifier.create(SMono.zipDelayError(SMono.just(1), SMono.just(2), SMono.just(3), SMono.just(4), SMono.just(5), SMono.just(6)))
          .expectNext((1, 2, 3, 4, 5, 6))
          .verifyComplete()
      }

      "with iterable" - {
        "of publisher of unit should return when all of the sources has fulfilled" in {
          val completed = new ConcurrentHashMap[String, Boolean]()
          val mono = SMono.whenDelayError(Iterable(
            SMono.just[Unit]({
              completed.put("first", true)
            }),
            SMono.just[Unit]({
              completed.put("second", true)
            })
          ))
          StepVerifier.create(mono)
            .expectComplete()
          completed should contain key "first"
          completed should contain key "second"
        }

        "of combinator function and monos should emit the value after combined by combinator function" in {
          StepVerifier.create(SMono.zipDelayError((values: Array[Any]) => s"${values(0).toString}-${values(1).toString}", SMono.just(1), SMono.just("one")))
            .expectNext("1-one")
            .verifyComplete()
        }
      }
    }

    ".zip" - {
      val combinator: Array[AnyRef] => String = { datas => datas.map(_.toString).foldLeft("") { (acc, v) => if (acc.isEmpty) v else s"$acc-$v" } }
      "with combinator function and varargs of mono should fullfill when all Monos are fulfilled" in {
        val mono = SMono.zip(combinator, SMono.just(1), SMono.just(2))
        StepVerifier.create(mono)
          .expectNext("1-2")
          .verifyComplete()
      }
      "with combinator function and Iterable of mono should fulfill when all Monos are fulfilled" in {
        val mono = SMono.zip(Iterable(SMono.just(1), SMono.just("2")), combinator)
        StepVerifier.create(mono)
          .expectNext("1-2")
          .verifyComplete()
      }
    }

    ".and" - {
      "should combine this mono and the other" in {
        StepVerifier.create(SMono.just(1) and SMono.just(2))
          .verifyComplete()
      }
    }

    ".asJava should convert to java" in {
      SMono.just(randomValue).asJava() shouldBe a[JMono[_]]
    }

    ".block" - {
      "should block the mono to get the value" in {
        SMono.just(randomValue).block() shouldBe randomValue
      }
      "with duration should block the mono up to the duration" in {
        SMono.just(randomValue).block(10 seconds) shouldBe randomValue
      }
    }

    ".blockOption" - {
      "without duration" - {
        "should block the mono to get value" in {
          SMono.just(randomValue).blockOption() shouldBe Some(randomValue)
        }
        "should return None if mono is empty" in {
          SMono.empty.blockOption() shouldBe None
        }
      }
      "with duration" - {
        "should block the mono up to the duration" in {
          SMono.just(randomValue).blockOption(10 seconds) shouldBe Some(randomValue)
        }
        "shouldBlock the mono up to the duration and return None" in {
          StepVerifier.withVirtualTime(() => SMono.just(SMono.empty.blockOption(10 seconds)))
            .thenAwait(10 seconds)
            .expectNext(None)
            .verifyComplete()
        }
      }
    }

    ".cast should cast the underlying value" in {
      val number = SMono.just(BigDecimal("123")).cast(classOf[ScalaNumber]).block()
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
      Mockito.verify(jMono).cancelOn(ArgumentMatchers.any[Scheduler]())
    }

    ".compose should defer creating the target mono type" in {
      StepVerifier.create(SMono.just(1).compose[String](m => SFlux.fromPublisher(m.map(_.toString))))
        .expectNext("1")
        .verifyComplete()
    }

    ".concatWith should concatenate mono with another source" in {
      StepVerifier.create(SMono.just(1).concatWith(SMono.just(2)))
        .expectNext(1)
        .expectNext(2)
        .verifyComplete()
    }

    "++ should concatenate mono with another source" in {
      StepVerifier.create(SMono.just(1) ++ SMono.just(2))
        .expectNext(1)
        .expectNext(2)
        .verifyComplete()
    }

    ".defaultIfEmpty should use the provided default value if the mono is empty" in {
      StepVerifier.create(SMono.empty[Int].defaultIfEmpty(-1))
        .expectNext(-1)
        .verifyComplete()
    }

    ".delayElement" - {
      "should delay the element" in {
        StepVerifier.withVirtualTime(() => SMono.just(randomValue).delayElement(5 seconds))
          .thenAwait(5 seconds)
          .expectNext(randomValue)
          .verifyComplete()
      }
      "with timer should delay using timer" in {
        StepVerifier.withVirtualTime(() => SMono.just(randomValue).delayElement(5 seconds, Schedulers.immediate()))
          .thenAwait(5 seconds)
          .expectNext(randomValue)
          .verifyComplete()
      }
    }

    ".delaySubscription" - {
      "with delay duration should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => SMono.just(1).delaySubscription(1 hour))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()
      }
      "with delay duration and scheduler should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => SMono.just(1).delaySubscription(1 hour, Schedulers.single()))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()
      }
      "with another publisher should delay the current subscription until the other publisher completes" in {
        StepVerifier.withVirtualTime(() => SMono.just(1).delaySubscription(SMono.just("one").delaySubscription(1 hour)))
          .thenAwait(1 hour)
          .expectNext(1)
          .verifyComplete()

      }
    }

    ".delayUntil should delay until the other provider terminate" in {
      StepVerifier.withVirtualTime(() => SMono.just(randomValue).delayUntil(_ => SFlux.just(1, 2).delayElements(2 seconds)))
        .thenAwait(4 seconds)
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".dematerialize should dematerialize the underlying mono" in {
      StepVerifier.create(SMono.just(Signal.next(randomValue)).dematerialize())
        .expectNext(randomValue)
        .verifyComplete()
    }

    ".doAfterSuccessOrError should call the callback function after the mono is terminated" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(SMono.just(randomValue)
        .doAfterSuccessOrError { t =>
          atomicBoolean.compareAndSet(false, true) shouldBe true
          t shouldBe Success(randomValue)
        })
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe 'get
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
      StepVerifier.create(SMono.just(randomValue).doAfterTerminate(() => atomicBoolean.compareAndSet(false, true)))
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".doFinally should call the callback" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(SMono.just(randomValue)
        .doFinally(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
        .expectNext(randomValue)
        .verifyComplete()
      atomicBoolean shouldBe 'get
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
      atomicBoolean shouldBe 'get
    }

    ".doOnNext should call the callback function when the mono emit data successfully" in {
      val atomicLong = new AtomicLong()
      val mono = SMono.just(randomValue)
        .doOnNext(t => atomicLong.compareAndSet(0, t))
      StepVerifier.create(mono)
        .expectNext(randomValue)
        .verifyComplete()
      atomicLong.get() shouldBe randomValue
    }

    ".doOnSuccess should call the callback function when the mono completes successfully" in {
      val atomicBoolean = new AtomicBoolean(false)
      val mono = SMono.empty[Int]
        .doOnSuccess(_ => atomicBoolean.compareAndSet(false, true) shouldBe true)
      StepVerifier.create(mono)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".doOnError" - {
      "with callback function should call the callback function when the mono encounter error" in {
        val atomicBoolean = new AtomicBoolean(false)
        StepVerifier.create(SMono.raiseError(new RuntimeException())
          .doOnError(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
          .expectError(classOf[RuntimeException])
          .verify()
        atomicBoolean shouldBe 'get
      }
    }

    ".doOnRequest should call the callback function when subscriber request data" in {
      val atomicLong = new AtomicLong(0)
      val mono = SMono.just(randomValue)
        .doOnRequest(l => atomicLong.compareAndSet(0, l))
      mono.subscribe(new BaseSubscriber[Long] {
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
      val mono = SMono.just(randomValue)
        .doOnSubscribe(_ => atomicBoolean.compareAndSet(false, true))
      StepVerifier.create(mono)
        .expectNextCount(1)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".doOnTerminate should do something on terminate" in {
      val atomicLong = new AtomicLong()
      StepVerifier.create(SMono.just(randomValue).doOnTerminate { () => atomicLong.set(randomValue) })
        .expectNext(randomValue)
        .expectComplete()
        .verify()
      atomicLong.get() shouldBe randomValue
    }

    ".elapsed" - {
      "should provide the time elapse when this mono emit value" in {
        StepVerifier.withVirtualTime(() => SMono.just(randomValue).delaySubscription(1 second).elapsed(), 1)
          .thenAwait(1 second)
          .expectNextMatches((t: (Long, Long)) => t match {
            case (time, data) => time >= 1000 && data == randomValue
          })
          .verifyComplete()
      }
      "with TimedScheduler should provide the time elapsed using the provided scheduler when this mono emit value" in {
        val virtualTimeScheduler = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(SMono.just(randomValue)
          .delaySubscription(1 second, virtualTimeScheduler)
          .elapsed(virtualTimeScheduler), 1)
          .`then`(() => virtualTimeScheduler.advanceTimeBy(1 second))
          .expectNextMatches((t: (Long, Long)) => t match {
            case (time, data) => time >= 1000 && data == randomValue
          })
          .verifyComplete()
      }
    }

    ".map should map the type of Mono from T to R" in {
      StepVerifier.create(SMono.just(randomValue).map(_.toString))
        .expectNext(randomValue.toString)
        .expectComplete()
        .verify()
    }
  }
}
