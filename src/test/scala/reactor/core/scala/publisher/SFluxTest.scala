package reactor.core.scala.publisher

import java.io.{BufferedReader, FileInputStream, InputStreamReader, PrintWriter}
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

import org.reactivestreams.Subscription
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{BaseSubscriber, FluxSink, SynchronousSink}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier

import scala.concurrent.duration.{Duration, _}
import scala.language.postfixOps
import scala.util.{Failure, Try}

class SFluxTest extends FreeSpec with Matchers {
  "SFlux" - {
    ".apply should return a proper SFlux" in {
      StepVerifier.create(SFlux(1, 2, 3))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".combineLatest" - {
      "of two should combine two publishers into single SFlux that emit tuple2" in {
        StepVerifier.create(SFlux.combineLatest(Mono.just(1), Mono.just(2)))
          .expectNext((1, 2))
          .verifyComplete()
      }
      "of many should combine all of them into single SFlux that emit Seq" in {
        val flux = SFlux.combineLatest(Mono.just(1), Mono.just(2), Mono.just(3), Mono.just(4))
        StepVerifier.create(flux)
          .expectNext(Seq(1, 2, 3, 4))
          .verifyComplete()
      }
    }

    ".combineLatestMap" - {
      "of two should combine two publishers into single SFlux and apply mapper" in {
        StepVerifier.create(SFlux.combineLatestMap(Mono.just(1), Mono.just(2), (i: Int, j: Int) => s"$i-$j"))
          .expectNext("1-2")
          .verifyComplete()
      }
      "of many should combine them into single SFlux and apply mapper" in {
        val flux = SFlux.combineLatestMap((array: Array[Int]) => s"${array(0)}-${array(1)}-${array(2)}", SFlux(1, 2), SFlux(10, 20), SFlux(100, 200))
        StepVerifier.create(flux)
          .expectNext("2-20-100")
          .expectNext("2-20-200")
          .verifyComplete()
      }
    }

    ".concat" - {
      "with varargs of publisher should concatenate the underlying publisher" in {
        val flux = SFlux.concat(Flux.just(1, 2, 3), Mono.just(3), Flux.just(3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 3, 3, 4)
          .verifyComplete()
      }
    }

    ".concatDelayError" - {
      "with varargs of publishers should concatenate all sources emitted from parents" in {
        val flux = SFlux.concatDelayError[Int](Mono.just(1), Mono.just(2), Mono.just(3))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".create should create a flux" in {
      val flux = SFlux.create[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".defer should create a flux" in {
      def f = SFlux(1, 2, 3)

      StepVerifier.create(SFlux.defer(f))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".empty should return an empty SFlux" in {
      StepVerifier.create(SFlux.empty)
        .verifyComplete()
    }

    ".error" - {
      "with throwable and whenRequest flag should" - {
        "emit onError during onSubscribe if the flag is false" in {
          val flag = new AtomicBoolean(false)
          val flux = SFlux.error(new RuntimeException())
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
          val flux = SFlux.error(new RuntimeException(), whenRequested = true)
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

    ".firstEmitter" - {
      "with varargs of publisher should create Flux based on the publisher that emit first onNext or onComplete or onError" in {
        val flux: SFlux[Long] = SFlux.firstEmitter(Mono.delay(Duration("10 seconds")), Mono.just[Long](1L))
        StepVerifier.create(flux)
          .expectNext(1)
          .verifyComplete()
      }
    }

    ".fromArray should create a flux that emits the items contained in the provided array" in {
      StepVerifier.create(SFlux.fromArray(Array("1", "2", "3")))
        .expectNext("1", "2", "3")
        .verifyComplete()
    }

    ".fromIterable should create flux that emit the items contained in the provided iterable" in {
      StepVerifier.create(SFlux.fromIterable(Iterable(1, 2, 3)))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".fromPublisher should expose the specified publisher with flux API" in {
      StepVerifier.create(SFlux.fromPublisher(Mono.just(1)))
        .expectNext(1)
        .verifyComplete()
    }

    ".fromStream" - {
      "with supplier should create flux that emit items contained in the supplier" in {
        StepVerifier.create(SFlux.fromStream(() => Stream(1, 2, 3)))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".generate" - {
      "with state supplier and state consumer" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp").toFile
        tempFile.deleteOnExit()
        new PrintWriter(tempFile) {
          write(Range(1, 6).mkString(s"${sys.props("line.separator")}"))
          flush()
          close()
        }
        val flux = SFlux.generate[Int, BufferedReader](
          (reader: BufferedReader, sink: SynchronousSink[Int]) => {
            Option(reader.readLine()).filterNot(_.isEmpty).map(_.toInt) match {
              case Some(x) => sink.next(x)
              case None => sink.complete()
            }
            reader
          }, Option((() => new BufferedReader(new InputStreamReader(new FileInputStream(tempFile)))): Callable[BufferedReader]),
          Option((bufferredReader: BufferedReader) => bufferredReader.close())
        )
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 5)
          .verifyComplete()
      }
    }

    ".index" - {
      "should return tuple with the index" in {
        val flux = SFlux("a", "b", "c").index()
        StepVerifier.create(flux)
          .expectNext((0l, "a"), (1l, "b"), (2l, "c"))
          .verifyComplete()
      }
      "with index mapper should return the mapped value" in {
        val flux = SFlux("a", "b", "c").index((i, v) => s"$i-$v")
        StepVerifier.create(flux)
          .expectNext("0-a", "1-b", "2-c")
          .verifyComplete()
      }
    }

    ".interval" - {
      "without delay should produce flux of Long starting from 0 every provided timespan immediately" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay should produce flux of Long starting from 0 every provided timespan after provided delay" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second)(2 seconds).take(5))
          .thenAwait(11 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with Scheduler should use the provided timed scheduler" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second, Schedulers.single()).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay and Scheduler should use the provided time scheduler after delay" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(2 seconds, Schedulers.single())(1 second).take(5))
          .thenAwait(11 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
    }

    ".just" - {
      "with varargs should emit values from provided data" in {
        val flux = SFlux.just(1, 2)
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
        StepVerifier.create(SFlux.mergeSequentialPublisher[Int](SFlux(SFlux(1, 2, 3, 4), SFlux(2, 3, 4))))
          .expectNext(1, 2, 3, 4, 2, 3, 4)
          .verifyComplete()
      }
      "with publisher of publisher, maxConcurrency and prefetch should merge the underlying publisher in sequence of publisher" in {
        StepVerifier.create(SFlux.mergeSequentialPublisher[Int](SFlux(SFlux(1, 2, 3), SFlux(2, 3, 4)), maxConcurrency = 8, prefetch = 2))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with publisher of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of publisher" in {
        StepVerifier.create(SFlux.mergeSequentialPublisher[Int](SFlux(SFlux(1, 2, 3), SFlux(2, 3, 4)), delayError = true, 8, 2))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        StepVerifier.create[Int](SFlux.mergeSequential[Int](Seq(SFlux(1, 2, 3), SFlux(2, 3, 4))))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with prefetch and varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        StepVerifier.create(SFlux.mergeSequential[Int](Seq(SFlux(1, 2, 3), SFlux(2, 3, 4)), prefetch = 2))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with prefetch, delayError and varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        StepVerifier.create[Int](SFlux.mergeSequential[Int](Seq(SFlux(1, 2, 3), SFlux(2, 3, 4)), delayError = true, 2))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher should merge the underlying publisher in sequence of the publisher" in {
        StepVerifier.create(SFlux.mergeSequentialIterable[Int](Iterable(SFlux(1, 2, 3), SFlux(2, 3, 4))))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher, maxConcurrency and prefetch should merge the underlying publisher in sequence of the publisher" in {
        StepVerifier.create(SFlux.mergeSequentialIterable[Int](Iterable(SFlux(1, 2, 3), SFlux(2, 3, 4)), maxConcurrency = 8, prefetch = 2))
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of the publisher" in {
        val flux = SFlux.mergeSequentialIterable[Int](Iterable(SFlux(1, 2, 3), SFlux(2, 3, 4)), true, 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
    }

    ".push should create a flux" in {
      val flux = SFlux.push[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.next(2)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1, 2)
        .verifyComplete()
    }

    ".take" - {
      "should emit only n values" in {
        StepVerifier.create(SFlux(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).take(3))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }
  }
}
