package reactor.core.scala.publisher

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader, PrintWriter}
import java.nio.file.Files
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.{Callable, CountDownLatch, TimeUnit}
import java.util.function.Predicate

import org.reactivestreams.{Publisher, Subscription}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{GroupedFlux => JGroupedFlux, _}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler
import reactor.util.concurrent.QueueSupplier

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.Source
import scala.language.postfixOps
import scala.math.Ordering.IntOrdering
import scala.math.ScalaNumber
import scala.util.{Failure, Try}

/**
  * Created by winarto on 1/10/17.
  */
class FluxTest extends FreeSpec with Matchers with TableDrivenPropertyChecks {
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

    ".push should create a flux" in {
      val flux = Flux.push[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.next(2)
        emitter.complete()
      })
      StepVerifier.create(flux)
        .expectNext(1, 2)
        .verifyComplete()
    }

    ".defer should create a flux" in {
      val flux = Flux.defer(() => Flux.just(1, 2, 3))
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".empty should create an empty flux" in {
      val flux = Flux.empty
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
        val flux = Flux.firstEmitting(Mono.delay(10 seconds), Mono.just(1L))
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

    ".fromIterable should create flux that emit the items contained in the provided iterable" in {
      val flux = Flux.fromIterable(Iterable(1, 2, 3))
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".fromStream should create flux that emit items contained in the provided stream" in {
      val flux = Flux.fromStream(Stream(1, 2, 3))
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".generate" - {
      "without state supplier" in {
        val counter = new AtomicLong()
        val flux = Flux.generate[Long](sink => {
          sink.next(counter.incrementAndGet())
        }).take(5)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 5)
          .verifyComplete()
      }
      "with state supplier" in {
        val iterator = Iterator(1, 2, 3, 4, 5)
        val flux = Flux.generate(stateSupplier = Option((() => iterator): Callable[Iterator[Int]]),
          generator = (it: Iterator[Int], sink: SynchronousSink[Int]) => {
            if (it.hasNext) sink.next(it.next())
            else sink.complete()
            it
          })
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 5)
          .verifyComplete()
      }
      "with state supplier and state consumer" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp").toFile
        tempFile.deleteOnExit()
        new PrintWriter(tempFile) {
          write(Range(1, 6).mkString(s"${sys.props("line.separator")}"))
          flush()
          close()
        }
        val flux = Flux.generate[Int, BufferedReader](Option((() => new BufferedReader(new InputStreamReader(new FileInputStream(tempFile)))): Callable[BufferedReader]),
          (reader: BufferedReader, sink: SynchronousSink[Int]) => {
            Option(reader.readLine()).filterNot(_.isEmpty).map(_.toInt) match {
              case Some(x) => sink.next(x)
              case None => sink.complete()
            }
            reader
          }, (possibleBufferredReader: Option[BufferedReader]) => possibleBufferredReader.foreach(_.close())
        )
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 5)
          .verifyComplete()
      }
    }
    ".interval" - {
      "without delay should produce flux of Long starting from 0 every provided timespan immediately" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay should produce flux of Long starting from 0 every provided timespan after provided delay" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second, 2 seconds).take(5))
          .thenAwait(11 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with Scheduler should use the provided timed scheduler" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second, Schedulers.single()).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay and Scheduler should use the provided time scheduler after delay" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second, 2 seconds, Schedulers.single()).take(5))
          .thenAwait(11 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
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
      "with publisher of publisher, maxConcurrency and prefetch should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3, 4)): Publisher[Publisher[_ <: Int]], 8, 2)
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
      "with prefetch and varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequential[Int](2, Flux.just(1, 2, 3), Flux.just(2, 3, 4))
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
      "with iterable of publisher, maxConcurrency and prefetch should merge the underlying publisher in sequence of the publisher" in {
        val flux = Flux.mergeSequential[Int](Iterable(Flux.just(1, 2, 3), Flux.just(2, 3, 4)), 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
    }

    ".mergeSequentialDelayError" - {
      "with publisher of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequentialDelayError[Int](Flux.just(Flux.just(1, 2, 3), Flux.just(2, 3, 4)): Publisher[Publisher[_ <: Int]], 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with prefetch, delayError and varargs of publishers should merge the underlying publisher in sequence of publisher" in {
        val flux = Flux.mergeSequentialDelayError[Int](2, Flux.just(1, 2, 3), Flux.just(2, 3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
      "with iterable of publisher, delayError, maxConcurrency and prefetch should merge the underlying publisher in sequence of the publisher" in {
        val flux = Flux.mergeSequentialDelayError[Int](Iterable(Flux.just(1, 2, 3), Flux.just(2, 3, 4)), 8, 2)
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
          write(s"1${sys.props("line.separator")}2")
          flush()
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
          write(s"1${sys.props("line.separator")}2")
          flush()
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

    ".blockFirst" - {
      "should block and return the first element" in {
        val element = Flux.just(1, 2, 3).blockFirst()
        element shouldBe Option(1)
      }
      "with duration should wait up to maximum provided duration" in {
        val element = Flux.just(1, 2, 3).blockFirst(Duration(10, "seconds"))
        element shouldBe Option(1)
      }
    }

    ".blockLast" - {
      "should block and return the last element" in {
        val element = Flux.just(1, 2, 3).blockLast()
        element shouldBe Option(3)
      }
      "with duration should wait up to the maximum provided duration to get the last element" in {
        val element = Flux.just(1, 2, 3).blockLast(10 seconds)
        element shouldBe Option(3)
      }
    }

    ".buffer" - {
      "should buffer all element into a Seq" in {
        val flux = Flux.just(1, 2, 3).buffer()
        StepVerifier.create(flux)
          .expectNext(Seq(1, 2, 3))
          .verifyComplete()
      }
      "with maxSize should buffer element into a batch of Seqs" in {
        val flux = Flux.just(1, 2, 3).buffer(2)
        StepVerifier.create(flux)
          .expectNext(Seq(1, 2), Seq(3))
          .verifyComplete()
      }
      "with maxSize and sequence supplier should buffer element into a batch of sequences provided by supplier" in {
        val seqSet = mutable.Set[mutable.ListBuffer[Int]]()
        val flux = Flux.just(1, 2, 3).buffer(2, () => {
          val seq = mutable.ListBuffer[Int]()
          seqSet += seq
          seq
        })
        StepVerifier.create(flux)
          .expectNextMatches((seq: Seq[Int]) => {
            seq shouldBe Seq(1, 2)
            seqSet should contain(seq)
            true
          })
          .expectNextMatches((seq: Seq[Int]) => {
            seq shouldBe Seq(3)
            seqSet should contain(seq)
            true
          })
          .verifyComplete()
      }
      "with maxSize and skip" - {
        val originalFlux = Flux.just(1, 2, 3, 4, 5)
        val data = Table(
          ("scenario", "maxSize", "skip", "expectedSequence"),
          ("maxSize < skip", 2, 3, Iterable(ListBuffer(1, 2), ListBuffer(4, 5))),
          ("maxSize > skip", 3, 2, Iterable(ListBuffer(1, 2, 3), ListBuffer(3, 4, 5), ListBuffer(5))),
          ("maxSize = skip", 2, 2, Iterable(ListBuffer(1, 2), ListBuffer(3, 4), ListBuffer(5)))
        )
        forAll(data) { (scenario, maxSize, skip, expectedSequence) => {
          s"when $scenario" in {
            val flux = originalFlux.buffer(maxSize, skip)
            StepVerifier.create(flux)
              .expectNextSequence(expectedSequence)
              .verifyComplete()
          }
        }
        }
      }
      "with maxSize, skip and buffer supplier" - {
        val data = Table(
          ("scenario", "maxSize", "skip", "expectedSequence"),
          ("maxSize < skip", 1, 2, Iterable(ListBuffer(1), ListBuffer(3), ListBuffer(5))),
          ("maxSize > skip", 3, 2, Iterable(ListBuffer(1, 2, 3), ListBuffer(3, 4, 5), ListBuffer(5))),
          ("maxSize = skip", 2, 2, Iterable(ListBuffer(1, 2), ListBuffer(3, 4), ListBuffer(5)))
        )
        forAll(data) { (scenario, maxSize, skip, expectedSequence) => {
          val iterator = expectedSequence.iterator
          s"when $scenario" in {
            val originalFlux = Flux.just(1, 2, 3, 4, 5)
            val seqSet = mutable.Set[mutable.ListBuffer[Int]]()
            val flux = originalFlux.buffer(maxSize, skip, () => {
              val seq = mutable.ListBuffer[Int]()
              seqSet += seq
              seq
            })
            StepVerifier.create(flux)
              .expectNextMatches((seq: Seq[Int]) => {
                seq shouldBe iterator.next()
                true
              })
              .expectNextMatches((seq: Seq[Int]) => {
                seq shouldBe iterator.next()
                true
              })
              .expectNextMatches((seq: Seq[Int]) => {
                seq shouldBe iterator.next()
                true
              })
              .verifyComplete()
            iterator.hasNext shouldBe false
          }
        }
        }
      }

      "with timespan should split values every timespan" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).buffer(1500 milliseconds))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2, 3), Seq(4))
          .verifyComplete()
      }

      "with timespan duration and timeshift duration should split the values every timespan" - {
        val data = Table(
          ("scenario", "timespan", "timeshift", "expected"),
          ("timeshift > timespan", 1500 milliseconds, 2 seconds, Seq(Seq(0l), Seq(1l, 2l), Seq(3l, 4l))),
          ("timeshift < timespan", 1500 milliseconds, 1 second, Seq(Seq(0l), Seq(1l), Seq(2l), Seq(3l), Seq(4l))),
          ("timeshift = timespan", 1500 milliseconds, 1500 milliseconds, Seq(Seq(0l), Seq(1l), Seq(2l, 3l), Seq(4l)))
        )
        forAll(data) { (scenario, timespan, timeshift, expected) => {
          s"when $scenario" in {
            StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).buffer(timespan, timeshift))
              .thenAwait(5 seconds)
              .expectNext(expected: _*)
              .verifyComplete()
          }
        }
        }
      }
    }

    ".bufferTimeout" - {
      "with maxSize and duration should aplit values every duration or after maximum has been reached" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferTimeout(1, 1500 milliseconds))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2), Seq(3), Seq(4))
          .verifyComplete()
      }
    }

    ".bufferUntil" - {
      "should buffer until predicate expression returns true" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferUntil(l => l % 3 == 0))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1, 2, 3), Seq(4))
          .verifyComplete()
      }
      "with cutBefore should control if the value that trigger the predicate be included in the previous or after sequence" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferUntil(l => l % 3 == 0, cutBefore = true))
          .thenAwait(5 seconds)
          .expectNext(Seq(0, 1, 2), Seq(3, 4))
          .verifyComplete()
      }
    }

    ".bufferWhile should buffer while the predicate is true" in {
      StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(10).bufferWhile(l => l % 2 == 0 || l % 3 == 0))
        .thenAwait(10 seconds)
        .expectNext(Seq(0), Seq(2, 3, 4), Seq(6), Seq(8, 9))
        .verifyComplete()
    }

    ".cache" - {
      "should turn this into a hot source" in {
        val flux = Flux.just(1, 2, 3).cache()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with history should just retain up to history" in {
        val flux = Flux.just(1, 2, 3).cache(2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
        StepVerifier.create(flux)
          .expectNext(2, 3)
          .verifyComplete()
      }
      "with ttl should retain the cache as long as the provided duration" in {
        try {
          StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(1 second).cache(2 seconds))
            .thenAwait(3 seconds)
            .expectNext(1, 2, 3)
            .verifyComplete()

          /*This does not make sense
                    StepVerifier.create(flux)
                      .`then`(() => vts.advanceTimeBy(20 seconds))
                      .expectNext(2, 3)
                      .verifyComplete()
          */
        } finally {
          VirtualTimeScheduler.reset()
        }

      }
      "with history and ttl should retain the cache up to ttl and max history" in {
        val supplier = () => {
          val tested = Flux.just(1, 2, 3).cache(2, 10 seconds)
          tested.subscribe()
          tested
        }
        StepVerifier.withVirtualTime(supplier)
          .thenAwait(5 seconds)
          .expectNext(2, 3)
          .verifyComplete()
      }
    }

    ".cast should cast the underlying value to a different type" in {
      val number = Flux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")).cast(classOf[ScalaNumber]).blockLast()
      number.get shouldBe a[ScalaNumber]
    }

    ".collect should collect the value into the supplied container" in {
      val mono = Flux.just(1, 2, 3).collect[ListBuffer[Int]](() => ListBuffer.empty, (buffer, v) => buffer += v)
      StepVerifier.create(mono)
        .expectNext(ListBuffer(1, 2, 3))
        .verifyComplete()
    }

    ".collectList should collect the value into a sequence" in {
      val mono = Flux.just(1, 2, 3).collectSeq()
      StepVerifier.create(mono)
        .expectNext(Seq(1, 2, 3))
        .verifyComplete()
    }

    ".collectMap" - {
      "with keyExtractor should collect the value and extract the key to return as Map" in {
        val mono = Flux.just(1, 2, 3).collectMap(i => i + 5)
        StepVerifier.create(mono)
          .expectNext(Map((6, 1), (7, 2), (8, 3)))
          .verifyComplete()
      }
      "with keyExtractor and valueExtractor should collect the value, extract the key and value from it" in {
        val mono = Flux.just(1, 2, 3).collectMap(i => i + 5, i => i + 6)
        StepVerifier.create(mono)
          .expectNext(Map((6, 7), (7, 8), (8, 9)))
          .verifyComplete()
      }
      "with keyExtractor, valueExtractor and mapSupplier should collect value, extract the key and value from it and put in the provided map" in {
        val map = mutable.HashMap[Int, Int]()
        val mono = Flux.just(1, 2, 3).collectMap(i => i + 5, i => i + 6, () => map)
        StepVerifier.create(mono)
          .expectNextMatches((m: Map[Int, Int]) => m == Map((6, 7), (7, 8), (8, 9)) && m == map)
          .verifyComplete()
      }
    }

    ".collectMultimap" - {
      "with keyExtractor should group the value based on the keyExtractor" in {
        val mono = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3)
        StepVerifier.create(mono)
          .expectNext(Map((0, Seq(3, 6, 9)), (1, Seq(1, 4, 7, 10)), (2, Seq(2, 5, 8))))
          .verifyComplete()
      }
      "with keyExtractor and valueExtractor should collect the value, extract the key and value from it" in {
        val mono = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3, i => i + 6)
        StepVerifier.create(mono)
          .expectNext(Map((0, Seq(9, 12, 15)), (1, Seq(7, 10, 13, 16)), (2, Seq(8, 11, 14))))
          .verifyComplete()
      }
      "with keyExtractor, valueExtractor and map supplier should collect the value, extract the key and value from it and put in the provided map" in {
        val map = mutable.HashMap[Int, util.Collection[Int]]()
        val mono = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3, i => i + 6, () => map)
        StepVerifier.create(mono)
          .expectNextMatches((m: Map[Int, Traversable[Int]]) => {
            m shouldBe map.mapValues(vs => vs.toArray().toSeq)
            m shouldBe Map((0, Seq(9, 12, 15)), (1, Seq(7, 10, 13, 16)), (2, Seq(8, 11, 14)))
            true
          })
          .verifyComplete()
      }
    }

    ".collectSortedList" - {
      "should collect and sort the elements" in {
        val mono = Flux.just(5, 2, 3, 1, 4).collectSortedSeq()
        StepVerifier.create(mono)
          .expectNext(Seq(1, 2, 3, 4, 5))
          .verifyComplete()
      }
      "with ordering should collect and sort the elements based on the provided ordering" in {
        val mono = Flux.just(2, 3, 1, 4, 5).collectSortedSeq(new IntOrdering {
          override def compare(x: Int, y: Int): Int = Ordering.Int.compare(x, y) * -1
        })
        StepVerifier.create(mono)
          .expectNext(Seq(5, 4, 3, 2, 1))
          .verifyComplete()
      }
    }

    ".compose should defer transformation of this flux to another publisher" in {
      val flux = Flux.just(1, 2, 3).compose(Mono.from)
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".concatMap" - {
      "with mapper should map the element sequentially" in {
        val flux = Flux.just(1, 2, 3).concatMap(i => Flux.just(i * 2, i * 3))
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with mapper and prefetch should map the element sequentially" in {
        val flux = Flux.just(1, 2, 3).concatMap(i => Flux.just(i * 2, i * 3), 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".concatMapDelayError" - {
      "with mapper, delayUntilEnd and prefetch" in {
        val flux = Flux.just(1, 2, 3).concatMapDelayError(i => {
          if (i == 2) Flux.error[Int](new RuntimeException("runtime ex"))
          else Flux.just(i * 2, i * 3)
        }, delayUntilEnd = true, 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 6, 9)
          .expectError(classOf[RuntimeException])
          .verify()
      }
    }

    ".concatMapIterable" - {
      "with mapper should concat and map an iterable" in {
        val flux = Flux.just(1, 2, 3).concatMapIterable(i => Iterable(i * 2, i * 3))
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with mapper and prefetch should concat and map an iterable" in {
        val flux = Flux.just(1, 2, 3).concatMapIterable(i => Iterable(i * 2, i * 3), 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".delayElement should delay every elements by provided delay in Duration" in {
      try {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(1 second).elapsed())
          .thenAwait(3 seconds)
          .expectNext((1000, 1), (1000, 2), (1000, 3))
          .verifyComplete()
      } finally {
        VirtualTimeScheduler.reset()
      }

    }

    ".count should return Mono which emit the number of value in this flux" in {
      val mono = Flux.just(10, 9, 8).count()
      StepVerifier.create(mono)
        .expectNext(3)
        .verifyComplete()
    }

    ".delaySubscription" - {
      "with delay duration should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delaySubscription(1 hour))
          .thenAwait(1 hour)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with another publisher should delay the current subscription until the other publisher completes" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delaySubscription(Mono.just("one").delaySubscription(1 hour)))
          .thenAwait(1 hour)
          .expectNext(1, 2, 3)
          .verifyComplete()

      }
    }

    ".dematerialize should dematerialize the underlying flux" in {
      val flux = Flux.just(Signal.next(1), Signal.next(2))
      StepVerifier.create(flux.dematerialize())
        .expectNext(1, 2)
        .verifyComplete
    }

    ".distinct" - {
      "should make the flux distinct" in {
        val flux = Flux.just(1, 2, 3, 2, 4, 3, 6).distinct()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 6)
          .verifyComplete()
      }
      "with keySelector should make the flux distinct by using the keySelector" in {
        val flux = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9).distinct(i => i % 3)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".distinctUntilChanged" - {
      "should make the flux always return different subsequent value" in {
        val flux = Flux.just(1, 2, 2, 3, 3, 3, 3, 2, 2, 5).distinctUntilChanged()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 5)
          .verifyComplete()
      }
      "with keySelector should make the flux always return different subsequent value based on keySelector" in {
        val flux = Flux.just(1, 2, 5, 8, 7, 4, 9, 6, 7).distinctUntilChanged(i => i % 3)
        StepVerifier.create(flux)
          .expectNext(1, 2, 7, 9, 7)
          .verifyComplete()
      }
    }

    ".doAfterTerminate should perform an action after it is terminated" in {
      val flag = new AtomicBoolean(false)
      val flux = Flux.just(1, 2, 3).doAfterTerminate(() => {
        flag.compareAndSet(false, true)
        ()
      })
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      flag shouldBe 'get
    }

    ".doOnCancel should perform an action after it is cancelled" in {
      val atomicBoolean = new AtomicBoolean(false)
      val flux = Flux.just(1, 2, 3).delayElements(1 minute)
        .doOnCancel(() => {
          atomicBoolean.compareAndSet(false, true) shouldBe true
          ()
        })

      val subscriptionReference = new AtomicReference[Subscription]()
      flux.subscribe(new BaseSubscriber[Int] {
        override def hookOnSubscribe(subscription: Subscription): Unit = {
          subscriptionReference.set(subscription)
          subscription.request(3)
        }

        override def hookOnNext(value: Int): Unit = ()
      })
      subscriptionReference.get().cancel()
      atomicBoolean shouldBe 'get
    }

    ".doOnComplete should perform action after the flux is completed" in {
      val flag = new AtomicBoolean(false)
      val flux = Flux.just(1, 2, 3).doOnComplete(() => {
        flag.compareAndSet(false, true) shouldBe true
        ()
      })
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      flag shouldBe 'get
    }

    ".doOnEach should perform an action for every signal" in {
      val buffer = ListBuffer[String]()
      val flux = Flux.just(1, 2, 3).doOnEach(s => buffer += s"${s.getType.toString}-${s.get()}")
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      buffer shouldBe Seq("onNext-1", "onNext-2", "onNext-3", "onComplete-null")
    }

    ".doOnError" - {
      "with callback function should call the callback function when the mono encounter error" in {
        val atomicBoolean = new AtomicBoolean(false)
        val flux = Flux.error(new RuntimeException())
          .doOnError(_ => atomicBoolean.compareAndSet(false, true) shouldBe true)
        StepVerifier.create(flux)
          .expectError(classOf[RuntimeException])
      }
      "with exception type and callback function should call the callback function when the mono encounter exception with the provided type" in {
        val atomicBoolean = new AtomicBoolean(false)
        val flux = Flux.error(new RuntimeException())
          .doOnError(classOf[RuntimeException]: Class[RuntimeException],
            ((_: RuntimeException) => atomicBoolean.compareAndSet(false, true) shouldBe true): SConsumer[RuntimeException])
        StepVerifier.create(flux)
          .expectError(classOf[RuntimeException])
      }
      "with predicate and callback fnction should call the callback function when the predicate returns true" in {
        val atomicBoolean = new AtomicBoolean(false)
        val flux = Flux.error[Int](new RuntimeException("Whatever"))
          .doOnError((_: Throwable) => true,
            ((_: Throwable) => atomicBoolean.compareAndSet(false, true) shouldBe true): SConsumer[Throwable])
        StepVerifier.create(flux)
          .expectError(classOf[RuntimeException])

      }
    }

    ".doOnNext should call the callback function when the flux emit data successfully" in {
      val buffer = ListBuffer[Int]()
      val flux = Flux.just(1, 2, 3)
        .doOnNext(t => buffer += t)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      buffer shouldBe Seq(1, 2, 3)
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

    ".doOnTerminate should do something on terminate" in {
      val flag = new AtomicBoolean(false)
      val flux = Flux.just(1, 2, 3).doOnTerminate { () => flag.compareAndSet(false, true) }
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .expectComplete()
        .verify()
      flag shouldBe 'get
    }

    ".doFinally should call the callback" in {
      val atomicBoolean = new AtomicBoolean(false)
      val flux = Flux.just(1, 2, 3)
        .doFinally(_ => atomicBoolean.compareAndSet(false, true) shouldBe true)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".elapsed" - {
      "should provide the time elapse when this mono emit value" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delaySubscription(1 second).delayElements(1 second).elapsed(), 3)
          .thenAwait(4 seconds)
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 1
            }
          })
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 2
            }
          })
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 3
            }
          })
          .verifyComplete()
      }
      "with Scheduler should provide the time elapsed using the provided scheduler when this mono emit value" in {
        val virtualTimeScheduler = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(Flux.just(1, 2, 3)
          .delaySubscription(1 second, virtualTimeScheduler)
          .delayElements(1 second, virtualTimeScheduler)
          .elapsed(virtualTimeScheduler), 3)
          .`then`(() => virtualTimeScheduler.advanceTimeBy(4 seconds))
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 1
            }
          })
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 2
            }
          })
          .expectNextMatches(new Predicate[(Long, Int)] {
            override def test(t: (Long, Int)): Boolean = t match {
              case (time, data) => time >= 1000 && data == 3
            }
          })
          .verifyComplete()
      }
    }

    ".elementAt" - {
      "should emit only the element at given index position" in {
        val mono = Flux.just(1, 2, 3).elementAt(2)
        StepVerifier.create(mono)
          .expectNext(3)
          .verifyComplete()
      }
      "should emit only the element at given index position or default value if the sequence is shorter" in {
        val mono = Flux.just(1, 2, 3, 4).elementAt(10, -1)
        StepVerifier.create(mono)
          .expectNext(-1)
          .verifyComplete()
      }
    }

    ".filter should evaluate each value against given predicate" in {
      val flux = Flux.just(1, 2, 3).filter(i => i > 1)
      StepVerifier.create(flux)
        .expectNext(2, 3)
        .verifyComplete()
    }

    ".filterWhen" - {
      "should replay the value of mono if the first item emitted by the test is true" in {
        val flux = Flux.just(10, 20, 30).filterWhen((i: Int) => Mono.just(i % 2 == 0))
        StepVerifier.create(flux)
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
      "with bufferSize should replay the value of mono if the first item emitted by the test is true" in {
        val flux = Flux.just(10, 20, 30).filterWhen((i: Int) => Mono.just(i % 2 == 0), 1)
        StepVerifier.create(flux)
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
    }

    ".firstEmittingWith should emit from the fastest first sequence" in {
      val flux = Flux.just(10, 20, 30).firstEmittingWith(Flux.just(1, 2, 3).delayElements(1 second))
      StepVerifier.create(flux)
        .expectNext(10, 20, 30)
        .verifyComplete()
    }

    ".flatMap should transform signal emitted by this flux into publishers" in {
      val flux = Flux.just(1, 2, 3).flatMap(_ => Mono.just("next"), _ => Mono.just("error"), () => Mono.just("complete"))
      StepVerifier.create(flux)
        .expectNext("next", "next", "next", "complete")
        .verifyComplete()
    }

    ".flatMapIterable" - {
      "should transform the items emitted by this flux into iterable" in {
        val flux = Flux.just(1, 2, 3).flatMapIterable(i => Iterable(i * 2, i * 3))
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with prefetch should transform the items and prefetch" in {
        val flux = Flux.just(1, 2, 3).flatMapIterable(i => Iterable(i * 2, i * 3), 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".flatMapSequential" - {
      "should transform items emitted by this flux into publisher then flatten them, in order" in {
        val flux = Flux.just(1, 2, 3).flatMapSequential(i => Flux.just(i * 2, i * 3))
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with maxConcurrency, should do the same as before just with provided maxConcurrency" in {
        val flux = Flux.just(1, 2, 3).flatMapSequential(i => Flux.just(i * 2, i * 3), 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with maxConcurrency and prefetch, should do the same as before just with provided maxConcurrency and prefetch" in {
        val flux = Flux.just(1, 2, 3).flatMapSequential(i => Flux.just(i * 2, i * 3), 2, 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with delayError should respect whether error be delayed after current merge backlog" in {
        val flux = Flux.just(1, 2, 3).flatMapSequentialDelayError(i => {
          if (i == 2) Flux.error[Int](new RuntimeException("just an error"))
          else Flux.just(i * 2, i * 3)
        }, 2, 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 6, 9)
          .verifyError(classOf[RuntimeException])
      }
    }

    ".groupBy" - {
      "with keyMapper should group the flux by the key mapper" in {
        val oddBuffer = ListBuffer.empty[Int]
        val evenBuffer = ListBuffer.empty[Int]

        val flux = Flux.just(1, 2, 3, 4, 5, 6).groupBy {
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }
        StepVerifier.create(flux)
          .expectNextMatches(new Predicate[GroupedFlux[String, Int]] {
            override def test(t: GroupedFlux[String, Int]): Boolean = {
              t.subscribe(oddBuffer += _)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[GroupedFlux[String, Int]] {
            override def test(t: GroupedFlux[String, Int]): Boolean = {
              t.subscribe(evenBuffer += _)
              t.key() == "even"
            }
          })
          .verifyComplete()

        oddBuffer shouldBe Seq(1, 3, 5)
        evenBuffer shouldBe Seq(2, 4, 6)
      }
      "with keyMapper and prefetch should group the flux by the key mapper and prefetch the elements from the source" in {
        val oddBuffer = ListBuffer.empty[Int]
        val evenBuffer = ListBuffer.empty[Int]

        val flux = Flux.just(1, 2, 3, 4, 5, 6).groupBy({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, 6)
        StepVerifier.create(flux)
          .expectNextMatches(new Predicate[GroupedFlux[String, Int]] {
            override def test(t: GroupedFlux[String, Int]): Boolean = {
              t.subscribe(oddBuffer += _)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[GroupedFlux[String, Int]] {
            override def test(t: GroupedFlux[String, Int]): Boolean = {
              t.subscribe(evenBuffer += _)
              t.key() == "even"
            }
          })
          .verifyComplete()

        oddBuffer shouldBe Seq(1, 3, 5)
        evenBuffer shouldBe Seq(2, 4, 6)
      }

      "with keyMapper and valueMapper should group the flux by the key mapper and convert the value by value mapper" in {
        val oddBuffer = ListBuffer.empty[String]
        val evenBuffer = ListBuffer.empty[String]

        val flux = Flux.just(1, 2, 3, 4, 5, 6).groupBy[String, String]({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, (i => i.toString): Int => String)
        StepVerifier.create(flux)
          .expectNextMatches(new Predicate[GroupedFlux[String, String]] {
            override def test(t: GroupedFlux[String, String]): Boolean = {
              t.subscribe(oddBuffer += _)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[GroupedFlux[String, String]] {
            override def test(t: GroupedFlux[String, String]): Boolean = {
              t.subscribe(evenBuffer += _)
              t.key() == "even"
            }
          })
          .verifyComplete()

        oddBuffer shouldBe Seq("1", "3", "5")
        evenBuffer shouldBe Seq("2", "4", "6")
      }

      "with keyMapper, valueMapper and prefetch should do the above with prefetch" in {
        val oddBuffer = ListBuffer.empty[String]
        val evenBuffer = ListBuffer.empty[String]

        val flux = Flux.just(1, 2, 3, 4, 5, 6).groupBy[String, String]({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, (i => i.toString): Int => String, 6)
        StepVerifier.create(flux)
          .expectNextMatches(new Predicate[GroupedFlux[String, String]] {
            override def test(t: GroupedFlux[String, String]): Boolean = {
              t.subscribe(oddBuffer += _)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[GroupedFlux[String, String]] {
            override def test(t: GroupedFlux[String, String]): Boolean = {
              t.subscribe(evenBuffer += _)
              t.key() == "even"
            }
          })
          .verifyComplete()

        oddBuffer shouldBe Seq("1", "3", "5")
        evenBuffer shouldBe Seq("2", "4", "6")
      }
    }

    ".handle should handle the values" in {
      val buffer = ListBuffer.empty[Int]
      val flux = Flux.just(1, 2, 3, 4, 5, 6).handle[Seq[Int]] {
        case (v, sink) =>
          buffer += v
          if (v == 6) {
            sink.next(buffer)
            sink.complete()
          }
      }
    }

    ".hasElement should return true if the flux has element matched" in {
      val mono = Flux.just(1, 2, 3, 4, 5).hasElement(4)
      StepVerifier.create(mono)
        .expectNext(true)
        .verifyComplete()
    }

    ".hasElements should return true if this flux has at least one element" in {
      val mono = Flux.just(1, 2, 3).hasElements()
      StepVerifier.create(mono)
        .expectNext(true)
        .verifyComplete()
    }

    ".ignoreElements should ignore all elements and only reacts on termination" in {
      val mono = Flux.just(1, 2, 3).ignoreElements()
      StepVerifier.create(mono)
        .verifyComplete()
    }

    ".last" - {
      "should give last element" in {
        val mono = Flux.just(1, 2, 3).last()
        StepVerifier.create(mono)
          .expectNext(3)
          .verifyComplete()
      }
      "with defaultValue should give the last element or defaultValue if the flux is empty" in {
        val mono = Flux.empty.last(5)
        StepVerifier.create(mono)
          .expectNext(5)
          .verifyComplete()
      }
    }

    ".map should map the type of Flux from T to R" in {
      val flux = Flux.just(1, 2, 3).map(_.toString)

      StepVerifier.create(flux)
        .expectNext("1", "2", "3")
        .expectComplete()
        .verify()
    }

    ".mapError" - {
      "with mapper should map the error" in {
        val flux = Flux.error[Int](new RuntimeException("runtime exception"))
          .onErrorMap((t: Throwable) => new UnsupportedOperationException(t.getMessage))

        StepVerifier.create(flux)
          .expectError(classOf[UnsupportedOperationException])
          .verify()
      }

      "with type and mapper should map the error if the error is of the provided type" in {
        val flux = Flux.error[Int](new RuntimeException("runtime ex"))
          .onErrorMap(classOf[RuntimeException], (t: Throwable) => new UnsupportedOperationException(t.getMessage))
        StepVerifier.create(flux)
          .expectError(classOf[UnsupportedOperationException])
          .verify()
      }

      "with predicate and mapper should map the error if the predicate pass" in {
        val flux = Flux.error[Int](new RuntimeException("runtime exc"))
          .onErrorMap(t => t.getClass == classOf[RuntimeException], (t: Throwable) => new UnsupportedOperationException(t.getMessage))
        StepVerifier.create(flux)
          .expectError(classOf[UnsupportedOperationException])
          .verify()
      }
    }

    ".mergeWith should merge with the provided publisher so they may interleave" in {
      StepVerifier.withVirtualTime(() => Flux.just(1, 3, 5).delayElements(1 second)
        .mergeWith(Flux.just(2, 4, 6).delayElements(1 second).delaySubscription(500 milliseconds)))
        .thenAwait(7 seconds)
        .expectNext(1, 2, 3, 4, 5, 6)
        .verifyComplete()
    }

    ".ofType should filter the value emitted by this flux according to the class" in {
      val flux = Flux.just(1, "2", "3", 4).ofType(classOf[String])
      StepVerifier.create(flux)
        .expectNext("2", "3")
        .verifyComplete()
    }

    ".onErrorResume" - {
      "should resume with a fallback publisher when error happen" in {
        val flux = Flux.just(1, 2).concatWith(Mono.error(new RuntimeException("exception"))).onErrorResume((t: Throwable) => Flux.just(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext(1, 2, 10, 20, 30)
          .verifyComplete()
      }
      "with class type and fallback should resume with fallback publisher when the exception is of provided type" in {
        val flux = Flux.just(1, 2).concatWith(Mono.error(new RuntimeException("exception"))).onErrorResume(classOf[RuntimeException], (t: RuntimeException) => Flux.just(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext(1, 2, 10, 20, 30)
          .verifyComplete()
      }
      "with predicate and fallback should resume with fallback publisher when the predicate is true" in {
        val predicate = (_: Throwable).isInstanceOf[RuntimeException]
        val flux = Flux.just(1, 2)
          .concatWith(Mono.error(new RuntimeException("exception")))
          .onErrorResume(predicate, (t: Throwable) => Flux.just(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext(1, 2, 10, 20, 30)
          .verifyComplete()
      }
    }

    ".onErrorReturn" - {
      "should return the fallback value if error happen" in {
        val flux = Flux.just(1, 2).concatWith(Mono.error(new RuntimeException("exc"))).onErrorReturn(10)
        StepVerifier.create(flux)
          .expectNext(1, 2, 10)
          .verifyComplete()
      }
      "with type and fallbackValue should return the fallback value if the exception is of provided type" in {
        val flux = Flux.just(1, 2).concatWith(Mono.error(new RuntimeException("exc"))).onErrorReturn(classOf[RuntimeException], 10)
        StepVerifier.create(flux)
          .expectNext(1, 2, 10)
          .verifyComplete()
      }
      "with predicate and fallbackValue should return the fallback value if the predicate is true" in {
        val predicate = (_: Throwable).isInstanceOf[RuntimeException]
        val flux = Flux.just(1, 2).concatWith(Mono.error(new RuntimeException("exc")))
          .onErrorReturn(predicate, 10)
        StepVerifier.create(flux)
          .expectNext(1, 2, 10)
          .verifyComplete()
      }
    }

    ".materialize should convert the flux into a flux that emit its signal" in {
      val flux = Flux.just(1, 2, 3).materialize()
      StepVerifier.create(flux)
        .expectNext(Signal.next(1), Signal.next(2), Signal.next(3), Signal.complete())
        .verifyComplete()
    }

    ".next should emit only the first item" in {
      val mono = Flux.just(1, 2, 3).next()
      StepVerifier.create(mono)
        .expectNext(1)
        .verifyComplete()
    }

    ".publishNext should make this flux a hot mono" in {
      val mono = Flux.just(1, 2, 3).publishNext()
      StepVerifier.create(mono)
        .expectNext(1)
        .verifyComplete()
    }

    ".reduce" - {
      "should aggregate the values" in {
        val mono = Flux.just(1, 2, 3).reduce(_ + _)
        StepVerifier.create(mono)
          .expectNext(6)
          .verifyComplete()
      }
      "with initial value should aggregate the values with initial one" in {
        val mono = Flux.just(1, 2, 3).reduce[String]("0", (agg, v) => s"$agg-${v.toString}")
        StepVerifier.create(mono)
          .expectNext("0-1-2-3")
          .verifyComplete()
      }
    }

    ".reduceWith should aggregate the values with initial one" in {
      val mono = Flux.just(1, 2, 3).reduceWith[String](() => "0", (agg, v) => s"$agg-${v.toString}")
      StepVerifier.create(mono)
        .expectNext("0-1-2-3")
        .verifyComplete()
    }

    ".repeat" - {
      "with predicate should repeat the subscription if the predicate returns true" in {
        val counter = new AtomicInteger(0)
        val flux = Flux.just(1, 2, 3).repeat(() => {
          if (counter.getAndIncrement() == 0) true
          else false
        })
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 1, 2, 3)
          .verifyComplete()
      }
      "with numRepeat should repeat as many as the provided parameter" in {
        val flux = Flux.just(1, 2, 3).repeat(3)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 1, 2, 3, 1, 2, 3)
          .verifyComplete()
      }
      "with numRepeat and predicate should repeat as many as provided parameter and as long as the predicate returns true" in {
        val flux = Flux.just(1, 2, 3).repeat(3, () => true)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".retry" - {
      "with numRetries will retry a number of times according to provided parameter" in {
        val flux = Flux.just(1, 2, 3).concatWith(Mono.error(new RuntimeException("ex"))).retry(2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "with predicate will retry until the predicate returns false" in {
        val counter = new AtomicInteger(0)
        val flux = Flux.just(1, 2, 3).concatWith(Mono.error(new RuntimeException("ex"))).retry { _ =>
          if (counter.getAndIncrement() > 0) false
          else true
        }
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "with numRetries and predicate should retry as many as provided numRetries and predicate returns true" in {
        val counter = new AtomicInteger(0)
        val flux = Flux.just(1, 2, 3).concatWith(Mono.error(new RuntimeException("ex"))).retry(3, { _ =>
          if (counter.getAndIncrement() > 0) false
          else true
        })
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
    }

    ".retryWhen should retry the companion publisher produces onNext signal" in {
      val counter = new AtomicInteger(0)
      val flux = Flux.just(1, 2, 3).concatWith(Mono.error(new RuntimeException("ex"))).retryWhen { _ =>
        if (counter.getAndIncrement() > 0) Mono.error[Int](new RuntimeException("another ex"))
        else Mono.just(1)
      }
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
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

    ".sampleFirst should emit the first value during the timespan" in {
      StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5).delayElements(1 second).sampleFirst(1500 milliseconds))
        .thenAwait(6 seconds)
        .expectNext(1, 3, 5)
        .verifyComplete()
    }

    ".scan" - {
      "should scan the values of this flux" in {
        val flux = Flux.just(1, 2, 3, 4).scan { (a, b) => a * b }
        StepVerifier.create(flux)
          .expectNext(1, 2, 6, 24)
          .verifyComplete()
      }
      "with initial value should scan with provided initial value" in {
        val flux = Flux.just(1, 2, 3, 4).scan[Int](2, { (a, b) => a * b })
        StepVerifier.create(flux)
          .expectNext(2, 2, 4, 12, 48)
          .verifyComplete()
      }
    }

    ".scanWith should scan with initial value" in {
      val flux = Flux.just(1, 2, 3, 4).scanWith[Int](() => 2, { (a, b) => a * b })
      StepVerifier.create(flux)
        .expectNext(2, 2, 4, 12, 48)
        .verifyComplete()
    }

    ".single" - {
      "should return a mono" in {
        val mono = Flux.just(1).single()
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
      }
      "with default value should return the default value if the flux is empty" in {
        val mono = Flux.empty.single(2)
        StepVerifier.create(mono)
          .expectNext(2)
          .verifyComplete()
      }
    }

    ".singleOrEmpty should return mono with single value or empty" in {
      val mono = Flux.just(3).singleOrEmpty()
      StepVerifier.create(mono)
        .expectNext(3)
        .verifyComplete()
    }

    ".skip" - {
      "with the number to skip should skip some elements" in {
        val flux = Flux.just(1, 2, 3, 4, 5).skip(2)
        StepVerifier.create(flux)
          .expectNext(3, 4, 5)
          .verifyComplete()
      }
      "with duration should skip all elements within that duration" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5).delayElements(1 second).skip(2 seconds))
          .thenAwait(6 seconds)
          .expectNext(2, 3, 4, 5)
          .verifyComplete()
      }
      "with timer should skip all elements within the millis duration with the provided timer" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5).delayElements(1 second).skip(2 seconds, Schedulers.single()))
          .thenAwait(6 seconds)
          .expectNext(2, 3, 4, 5)
          .verifyComplete()
      }
    }

    ".skipLast should skip the last n elements" in {
      val flux = Flux.just(1, 2, 3, 4, 5).skipLast(2)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".skipUntil should skip until predicate matches" in {
      val flux = Flux.just(1, 2, 3, 4, 5).skipUntil(t => t == 3)
      StepVerifier.create(flux)
        .expectNext(3, 4, 5)
        .verifyComplete()
    }

    ".skipWhile should skip while the predicate returns true" in {
      val flux = Flux.just(1, 2, 3, 4, 5).skipWhile(t => t <= 3)
      StepVerifier.create(flux)
        .expectNext(4, 5)
        .verifyComplete()
    }

    ".sort" - {
      "should sort the elements" in {
        val flux = Flux.just(3, 4, 2, 5, 1, 6).sort()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 4, 5, 6)
          .verifyComplete()
      }
      "with sort function should sort the elements based on the function" in {
        val flux = Flux.just(3, 4, 2, 5, 1, 6).sort(new IntOrdering() {
          override def compare(x: Int, y: Int): Int = super.compare(x, y) * -1
        })
        StepVerifier.create(flux)
          .expectNext(6, 5, 4, 3, 2, 1)
          .verifyComplete()
      }
    }

    ".startWith" - {
      "with iterable should prepend the flux with the provided iterable elements" in {
        val flux = Flux.just(1, 2, 3).startWith(Iterable(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
      "with varargs should prepend the flux with the provided values" in {
        val flux = Flux.just(1, 2, 3).startWith(10, 20, 30)
        StepVerifier.create(flux)
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
      "with publisher should prepend the flux with the provided publisher" in {
        val flux = Flux.just(1, 2, 3).startWith(Flux.just(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
    }

    ".switchIfEmpty should switch if the current flux is empty" in {
      val flux = Flux.empty.switchIfEmpty(Flux.just(10, 20, 30))
      StepVerifier.create(flux)
        .expectNext(10, 20, 30)
        .verifyComplete()
    }

    ".switchMap" - {
      "with function should switch to the new publisher" in {
        val flux = Flux.just(1, 2, 3).switchMap(i => Flux.just(i * 10, i * 20))
        StepVerifier.create(flux)
          .expectNext(10, 20, 20, 40, 30, 60)
          .verifyComplete()
      }
      "with function and prefetch should switch to the new publisher" in {
        val flux = Flux.just(1, 2, 3).switchMap(i => Flux.just(i * 10, i * 20), 2)
        StepVerifier.create(flux)
          .expectNext(10, 20, 20, 40, 30, 60)
          .verifyComplete()
      }
    }

    ".take" - {
      "should emit only n values" in {
        val flux = Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).take(3)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with duration should only emit values during the provided duration" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5).delayElements(1 seconds).take(3500 milliseconds))
          .thenAwait(5 seconds)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with timespan and timed scheduler should only emit values during the provided timespan with the provided TimedScheduler" in {
        val vts = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(Flux.just(1, 2, 3, 4, 5)
          .delayElements(1 second, vts)
          .take(3500 milliseconds, vts), 256)
          .`then`(() => vts.advanceTimeBy(5 seconds))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".takeLast should take the last n values" in {
      val flux = Flux.just(1, 2, 3, 4, 5).takeLast(3)
      StepVerifier.create(flux)
        .expectNext(3, 4, 5)
        .verifyComplete()
    }

    ".takeUntil should emit the values until the predicate returns true" in {
      val flux = Flux.just(1, 2, 3, 4, 5).takeUntil(t => t >= 4)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3, 4)
        .verifyComplete()
    }

    ".takeWhile should emit values until the predicate returns false" in {
      val flux = Flux.just(1, 2, 3, 4, 5).takeWhile(t => t < 4)
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".then" - {
      "without parameter should actively ignore the values" in {
        val mono = Flux.just(1, 2, 3, 4, 5).`then`()
        StepVerifier.create(mono)
          .verifyComplete()
      }
    }

    ".thenEmpty should wait for this to complete and then for the supplied publisher to complete" in {
      val mono = Flux.just(1, 2, 3).thenEmpty(Mono.empty)
      StepVerifier.create(mono)
        .verifyComplete()
    }

    ".thenMany" - {
      "should emit the sequence of the supplied publisher" in {
        val flux = Flux.just(1, 2, 3).thenMany(Flux.just("1", "2", "3"))
        StepVerifier.create(flux)
          .expectNext("1", "2", "3")
          .verifyComplete()
      }
    }

    ".timeout" - {
      "with timeout duration should throw exception if the item is not emitted within the provided duration after previous emited item" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(2 seconds).timeout(1 second))
          .thenAwait(2 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with timeout and optional fallback should fallback if the item is not emitted within the provided duration" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(2 seconds).timeout(1 second, Option(Flux.just(10, 20, 30))))
          .thenAwait(2 seconds)
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
      "with firstTimeout should throw exception if the first item is not emitted before the given publisher emits" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(2 seconds).timeout(Mono.just(1)))
          .thenAwait(2 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with firstTimeout and next timeout factory should throw exception if any of the item from this flux does not emit before the timeout provided" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(2 seconds).timeout(Mono.just(1).delaySubscription(3 seconds), t => Mono.just(1).delaySubscription(t seconds)))
          .thenAwait(5 seconds)
          .expectNext(1)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with firstTimeout, nextTimeoutFactory and fallback should fallback if any of the item is not emitted within the timeout period" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).delayElements(2 seconds).timeout(Mono.just(1).delaySubscription(3 seconds), t => Mono.just(1).delaySubscription(t seconds), Flux.just(10, 20, 30)))
          .thenAwait(5 seconds)
          .expectNext(1, 10, 20, 30)
          .verifyComplete()
      }
    }

    ".toIterable" - {
      "should transform this flux into iterable" in {
        Flux.just(1, 2, 3).toIterable().toStream shouldBe Stream(1, 2, 3)
      }
      "with batchSize should transform this flux into iterable" in {
        Flux.just(1, 2, 3).toIterable(1).toStream shouldBe Stream(1, 2, 3)
      }
      "with batchSize and queue supplier should transform this flux into interable" in {
        Flux.just(1, 2, 3).toIterable(1, Option(QueueSupplier.get[Int](1))).toStream shouldBe Stream(1, 2, 3)
      }
    }

    ".toStream" - {
      "should transform this flux into stream" in {
        Flux.just(1, 2, 3).toStream() shouldBe Stream(1, 2, 3)
      }
      "with batchSize should transform this flux into stream" in {
        Flux.just(1, 2, 3).toStream(2) shouldBe Stream(1, 2, 3)
      }
    }

    ".transform should defer transformation of this flux to another publisher" in {
      val flux = Flux.just(1, 2, 3).transform(Mono.from)
      StepVerifier.create(flux)
        .expectNext(1)
        .verifyComplete()
    }

    ".withLatestFrom should combine with the latest of the other publisher" in {
      StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4).delayElements(1 second).withLatestFrom(Flux.just("one", "two", "three").delayElements(1500 milliseconds), (i: Int, s: String) => (i, s)))
        .thenAwait(5 seconds)
        .expectNext((2, "one"), (3, "two"), (4, "two"))
        .verifyComplete()
    }

    ".zipWith" - {
      "should zip both publishers" in {
        val flux = Flux.just(1, 2, 3).zipWith(Flux.just(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
      "with combinator should zip and apply the combinator" in {
        val flux = Flux.just(1, 2, 3).zipWith[Int, Int](Flux.just(10, 20, 30), (i1: Int, i2: Int) => i1 + i2)
        StepVerifier.create(flux)
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
      "with combinator and prefetch should zip and apply the combinator" in {
        val flux = Flux.just(1, 2, 3).zipWith[Int, Int](Flux.just(10, 20, 30), 1, (i1: Int, i2: Int) => i1 + i2)
        StepVerifier.create(flux)
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
      "with prefetch should zip both publishers" in {
        val flux = Flux.just(1, 2, 3).zipWith(Flux.just(10, 20, 30), 1)
        StepVerifier.create(flux)
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
    }

    ".zipWithIterable" - {
      "should zip with the provided iterable" in {
        val flux = Flux.just(1, 2, 3).zipWithIterable(Iterable(10, 20, 30))
        StepVerifier.create(flux)
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
      "with zipper should zip and apply the zipper" in {
        val flux = Flux.just(1, 2, 3).zipWithIterable[Int, Int](Iterable(10, 20, 30), (i1: Int, i2: Int) => i1 + i2)
        StepVerifier.create(flux)
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
    }
  }
}
