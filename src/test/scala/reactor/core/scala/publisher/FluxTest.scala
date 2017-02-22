package reactor.core.scala.publisher

import java.io.{File, PrintWriter}
import java.nio.file.Files
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.reactivestreams.{Publisher, Subscription}
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{BaseSubscriber, FluxSink}
import reactor.test.StepVerifier

import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Try}

/**
  * Created by winarto on 1/10/17.
  */
class FluxTest extends FreeSpec with Matchers {
  "Flux" - {
    ".combineLatest" - {
      "with combinator and sources should produce latest elements into a single element" in {
        val flux = Flux.combineLatest[Int, String]((array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}", Mono.just(1), Mono.just(2))
        StepVerifier.create(flux)
          .expectNext("1-2")
          .verifyComplete()
      }
      "with combinator, prefetch and sources should produce latest elements into a single element" in {
        val flux = Flux.combineLatest[Int, String]((array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}", 2, Flux.just(1, 2), Flux.just(10, 20))
        StepVerifier.create(flux)
          .expectNext("2-10")
          .expectNext("2-20")
          .verifyComplete()
      }
      "with source1, source2 and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Mono.just(1), Mono.just("a"), (int: Int, string: String) => s"${int.toString}-$string")
        StepVerifier.create(flux)
          .expectNext("1-a")
          .verifyComplete()
      }
      "with source1, source2, source3 and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Mono.just(1), Mono.just("a"), Mono.just(BigDecimal("0")),
          (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}-${array(2).toString}")
        StepVerifier.create(flux)
          .expectNext("1-a-0")
          .verifyComplete()
      }
      "with source1, source2, source3, source4 and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Mono.just(1), Mono.just("a"), Mono.just(BigDecimal("0")), Mono.just(1L),
          (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}-${array(2).toString}-${array(3).toString}")
        StepVerifier.create(flux)
          .expectNext("1-a-0-1")
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5 and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Mono.just(1), Mono.just("a"), Mono.just(BigDecimal("0")), Mono.just(1L), Mono.just(2),
          (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}-${array(2).toString}-${array(3).toString}-${array(4).toString}")
        StepVerifier.create(flux)
          .expectNext("1-a-0-1-2")
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5, source6 and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Mono.just(1), Mono.just("a"), Mono.just(BigDecimal("0")), Mono.just(1L), Mono.just(2), Mono.just(3),
          (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}-${array(2).toString}-${array(3).toString}-${array(4).toString}-${array(5).toString}")
        StepVerifier.create(flux)
          .expectNext("1-a-0-1-2-3")
          .verifyComplete()
      }
      "with iterable and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Iterable(Mono.just(1), Mono.just(2)), (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}")
        StepVerifier.create(flux)
          .expectNext("1-2")
          .verifyComplete()
      }
      "with iterable, prefetch and combinator should produce latest elements into a single element" in {
        val flux = Flux.combineLatest(Iterable(Mono.just(1), Mono.just(2)), 2, (array: Array[AnyRef]) => s"${array(0).toString}-${array(1).toString}")
        StepVerifier.create(flux)
          .expectNext("1-2")
          .verifyComplete()
      }
    }

    ".concat" - {
      "with iterable should concatenate the sources" in {
        val flux = Flux.concat(Iterable(Flux.just(1, 2, 3), Flux.just(2, 3)))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3)
          .verifyComplete()
      }
      "with publisher of publisher should concatenate the underlying publisher" in {
        val flux = Flux.concat(Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3)): Publisher[Publisher[Int]])
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3)
          .verifyComplete()
      }
      "with publisher of publisher and prefetch should concatenate the underlying publisher" in {
        val flux = Flux.concat(Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3)): Publisher[Publisher[Int]], 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3)
          .verifyComplete()
      }
      "with varargs of publisher should concatenate the underlying publisher" in {
        val flux = Flux.concat(Flux.just(1, 2, 3), Mono.just(3), Flux.just(3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 3, 3, 4)
          .verifyComplete()
      }
    }

    ".concatDelayError" - {
      "with publisher of publisher should concatenate all sources emitted from the parents" in {
        val flux = Flux.concatDelayError[Int](Flux.just(Mono.just(1), Mono.just(2), Mono.just(3)): Publisher[Publisher[Int]])
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with publisher of publisher and prefetch should concatenate all sources emitted from the parents" in {
        val flux = Flux.concatDelayError[Int](Flux.just(Mono.just(1), Mono.just(2), Mono.just(3)): Publisher[Publisher[Int]], 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with publisher of publisher, delayUntilEnd and prefetch should concatenate all sources emitted from parents" in {
        val flag = new AtomicBoolean(false)
        val flux = Flux.concatDelayError[Int](Flux.just(Mono.just(1), Mono.error(new RuntimeException()), Mono.just(3).doOnNext(_ => flag.compareAndSet(false, true))): Publisher[Publisher[Int]], delayUntilEnd = true, 2)
        StepVerifier.create(flux)
          .expectNext(1, 3)
          .expectError(classOf[RuntimeException])
          .verify()
        flag shouldBe 'get
      }
      "with varargs of publishers should concatenate all sources emitted from parents" in {
        val flux = Flux.concatDelayError[Int](Mono.just(1), Mono.just(2), Mono.just(3))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".create should create a flux" in {
      val flux = Flux.create[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".defer should create a flux" in {
      val flux = Flux.defer(() => Flux.just(1, 2, 3))
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".empty should create an empty flux" in {
      val flux = Flux.empty()
      StepVerifier.create(flux)
        .verifyComplete()
    }

    ".error" - {
      "with throwable should create a flux with error" in {
        val flux = Flux.error(new RuntimeException())
        StepVerifier.create(flux)
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "with throwable and whenRequest flag should" - {
        "emit onError during onSubscribe if the flag is false" in {
          val flag = new AtomicBoolean(false)
          val flux = Flux.error(new RuntimeException(), whenRequested = false)
            .doOnRequest(_ => flag.compareAndSet(false, true))
          Try(flux.subscribe(new BaseSubscriber[Long] {
            override def hookOnSubscribe(subscription: Subscription): Unit = {
              ()
            }

            override def hookOnNext(value: Long): Unit = ()
          })) shouldBe a[Failure[_]]
          flag.get() shouldBe false
        }
        "emit onError during onRequest if the flag is true" in {
          val flag = new AtomicBoolean(false)
          val flux = Flux.error(new RuntimeException(), whenRequested = true)
            .doOnRequest(_ => flag.compareAndSet(false, true))
          Try(flux.subscribe(new BaseSubscriber[Long] {
            override def hookOnSubscribe(subscription: Subscription): Unit = {
              subscription.request(1)
              ()
            }

            override def hookOnNext(value: Long): Unit = ()
          })) shouldBe a[Failure[_]]
          flag.get() shouldBe true
        }
      }
    }

    ".firstEmitting" - {
      "with varargs of publisher should create Flux based on the publisher that emit first onNext or onComplete or onError" in {
        val flux = Flux.firstEmitting(Mono.delay(Duration("10 seconds")), Mono.just(1L))
        StepVerifier.create(flux)
          .expectNext(1)
          .verifyComplete()
      }
      "with iterable of publisher should create Flux based on the publiher that first emit onNext or onComplete or onError" in {
        val flux = Flux.firstEmitting(Iterable(Mono.delay(Duration("10 seconds")), Mono.just(1L)))
        StepVerifier.create(flux)
          .expectNext(1)
          .verifyComplete()
      }
    }

    ".from should expose the specified publisher with flux API" in {
      val flux = Flux.from(Mono.just(1))
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".fromArray should create a flux that emits the items contained in the provided array" in {
      val flux = Flux.fromArray(Array("1", "2", "3"))
      StepVerifier.create(flux)
        .expectNext("1", "2", "3")
        .verifyComplete()
    }

    ".just" - {
      "with varargs should emit values from provided data" in {
        val flux = Flux.just(1, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2)
          .verifyComplete()
      }
      "with one element should emit value from provided data" in {
        val flux = Flux.just[Int](1)
        StepVerifier.create(flux)
          .expectNext(1)
          .verifyComplete()
      }
    }

    ".mergeSequential" - {
      "with publisher of publisher should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3, 4)): Publisher[Publisher[Int]])
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with publisher of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3, 4)): Publisher[Publisher[Int]], delayError = true, 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](Flux.just(1, 2, 3), Flux.just(2, 3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with prefetch, delayError and varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](2, true, Flux.just(1, 2, 3), Flux.just(2, 3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher should merge the underlying publisher in sequence of the publisher" in {
        val flux = Flux.mergeSequential[Int](Iterable(Flux.just(1, 2, 3), Flux.just(2, 3, 4)))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of the publisher" in {
        val flux = Flux.mergeSequential[Int](Iterable(Flux.just(1, 2, 3), Flux.just(2, 3, 4)), delayError = true, 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
    }

    ".never should never emit any signal" in {
      val flux = Flux.never()
      StepVerifier.create(flux)
        .expectSubscription()
        .expectNoEvent(Duration(1, "second"))
    }

    ".range should emit int within the range" in {
      val flux = Flux.range(10, 5)
      StepVerifier.create(flux)
        .expectNext(10, 11, 12, 13, 14)
        .verifyComplete()
    }

    ".using" - {
      "without eager flag should produce some data" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp")
        tempFile.toFile.deleteOnExit()
        new PrintWriter(tempFile.toFile) {
          write(s"1${sys.props("line.separator")}2");
          flush();
          close()
        }
        val flux: Flux[String] = Flux.using[String, File](() => tempFile.toFile, (file: File) => Flux.fromIterable[String](Source.fromFile(file).getLines().toIterable), (file: File) => {
          file.delete()
          ()
        })
        StepVerifier.create(flux)
          .expectNext("1", "2")
          .verifyComplete()
      }
      "with eager flag should produce some data" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp")
        tempFile.toFile.deleteOnExit()
        new PrintWriter(tempFile.toFile) {
          write(s"1${sys.props("line.separator")}2");
          flush();
          close()
        }
        val flux: Flux[String] = Flux.using[String, File](() => tempFile.toFile, (file: File) => Flux.fromIterable[String](Source.fromFile(file).getLines().toIterable), (file: File) => {
          file.delete()
          ()
        }, eager = true)
        StepVerifier.create(flux)
          .expectNext("1", "2")
          .verifyComplete()
      }
    }

    ".zip" - {
      "with source1, source2 and combinator should combine the data" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"), (i: Int, str: String) => s"$i-$str")
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with source1 and source2 should emit flux with tuple2" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"))
        StepVerifier.create(flux)
          .expectNext((1, "one"), (2, "two"), (3, "three"))
          .verifyComplete()
      }
      "with source1, source2, source3 should emit flux with tuple3" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"), Flux.just(1l, 2l, 3l))
        StepVerifier.create(flux)
          .expectNext((1, "one", 1l), (2, "two", 2l), (3, "three", 3l))
          .verifyComplete()
      }
      "with source1, source2, source3, source4 should emit flux with tuple4" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"), Flux.just(1l, 2l, 3l), Flux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")))
        StepVerifier.create(flux)
          .expectNext((1, "one", 1l, BigDecimal("1")), (2, "two", 2l, BigDecimal("2")), (3, "three", 3l, BigDecimal("3")))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5 should emit flux with tuple5" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"), Flux.just(1l, 2l, 3l), Flux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), Flux.just("a", "i", "u"))
        StepVerifier.create(flux)
          .expectNext((1, "one", 1l, BigDecimal("1"), "a"), (2, "two", 2l, BigDecimal("2"), "i"), (3, "three", 3l, BigDecimal("3"), "u"))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5, source6 should emit flux with tuple6" in {
        val flux = Flux.zip(Flux.just(1, 2, 3), Flux.just("one", "two", "three"), Flux.just(1l, 2l, 3l), Flux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), Flux.just("a", "i", "u"), Flux.just("a", "b", "c"))
        StepVerifier.create(flux)
          .expectNext((1, "one", 1l, BigDecimal("1"), "a", "a"), (2, "two", 2l, BigDecimal("2"), "i", "b"), (3, "three", 3l, BigDecimal("3"), "u", "c"))
          .verifyComplete()
      }
      "with iterable and combinator should emit flux of combined data" in {
        val flux = Flux.zip[String](Iterable(Flux.just(1, 2, 3), Flux.just("one", "two", "three")), (array: Array[_]) => s"${array(0)}-${array(1)}")
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with iterable, prefetch and combinator should emit flux of combined data" in {
        val flux = Flux.zip[String](Iterable(Flux.just(1, 2, 3), Flux.just("one", "two", "three")), 2, (array: Array[_]) => s"${array(0)}-${array(1)}")
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with combinator and varargs publisher should emit flux of combined data" in {
        val flux = Flux.zip((array: Array[AnyRef]) => s"${array(0)}-${array(1)}", Flux.just(1, 2, 3), Flux.just("one", "two", "three"))
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with combinator, prefetch and varargs publisher should emit flux of combined data" in {
        val flux = Flux.zip((array: Array[AnyRef]) => s"${array(0)}-${array(1)}", 2, Flux.just(1, 2, 3), Flux.just("one", "two", "three"))
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
    }

    ".all should check every single element satisfy the predicate" in {
      val mono = Flux.just(1, 2, 3).all(i => i > 0)
      StepVerifier.create(mono)
        .expectNext(true)
        .verifyComplete()
    }

    ".any should check that there is at least one element satisfy the predicate" in {
      val mono = Flux.just(1, 2, 3).any(i => i % 2 == 0)
      StepVerifier.create(mono)
        .expectNext(true)
        .verifyComplete()
    }

    ".as should transform this flux to another publisher" in {
      val mono = Flux.just(1, 2, 3).as(Mono.from)
      StepVerifier.create(mono)
        .expectNext(1)
        .verifyComplete()
    }

    ".compose should defer transformation of this flux to another publisher" in {
      val flux = Flux.just(1, 2, 3).compose(Mono.from)
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".transform should defer transformation of this flux to another publisher" in {
      val flux = Flux.just(1, 2, 3).transform(Mono.from)
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".count should return Mono which emit the number of value in this flux" in {
      val mono = Flux.just(10, 9, 8).count()
      StepVerifier.create(mono)
        .expectNext(3)
        .verifyComplete()
    }

    ".iterable should produce data from iterable" in {
      val flux = Flux.fromIterable[Int](Iterable(1, 2, 3))
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".map should map the type of Flux from T to R" in {
      val flux = Flux.just(1, 2, 3).map(_.toString)

      StepVerifier.create(flux)
        .expectNext("1", "2", "3")
        .expectComplete()
        .verify()
    }

    ".take should emit only n values" in {
      val flux = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).take(3)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".sample should emit the last value for given interval" ignore {
      val flux = Flux.just[Long](1L).sample(Duration(1, "second"))
      val counter = new CountDownLatch(3)
      flux.subscribe(new BaseSubscriber[Long] {
        override def hookOnSubscribe(subscription: Subscription): Unit = subscription.request(Long.MaxValue)

        override def hookOnNext(value: Long): Unit = {
          counter.countDown()
          Console.out.println(value)
        }
      })
      counter.await(4, TimeUnit.SECONDS)
    }

    ".doOnRequest should be called upon request" in {
      val atomicLong = new AtomicLong(0)
      val mono = Flux.just[Long](1L)
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

    ".doOnSubscribe should be called upon subscribe" in {
      val atomicBoolean = new AtomicBoolean(false)
      val mono = Flux.just[Long](1L)
        .doOnSubscribe(_ => atomicBoolean.compareAndSet(false, true))
      StepVerifier.create(mono)
        .expectNextCount(1)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }
  }
}
