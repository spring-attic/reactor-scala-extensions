package reactor.core.scala.publisher

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader, PrintWriter}
import java.nio.file.Files
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{Callable, CountDownLatch, TimeUnit}

import org.reactivestreams.{Publisher, Subscription}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{BaseSubscriber, FluxSink, SynchronousSink}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.collection.immutable.{HashMap, TreeMap}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.Source
import scala.language.postfixOps
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
    }

    ".intervalMillis" - {
      "without delay should produce flux of Long starting from 0 every provided period in millis immediately" in {
        StepVerifier.withVirtualTime(() => Flux.intervalMillis(1000).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with TimedScheduler should use the provided timed scheduler" in {
        StepVerifier.withVirtualTime(() => Flux.intervalMillis(1000, Schedulers.timer()).take(5))
          .thenAwait(5 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay should produce flux after provided delay" in {
        StepVerifier.withVirtualTime(() => Flux.intervalMillis(1000, 2000).take(5))
          .thenAwait(11 seconds)
          .expectNext(0, 1, 2, 3, 4)
          .verifyComplete()
      }
      "with delay and TimedScheduler should use the provided time scheduler after delay" in {
        StepVerifier.withVirtualTime(() => Flux.intervalMillis(1000, 2000, Schedulers.timer()).take(5))
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

    ".blockFirstMillis should block with parameter timeout in millis" in {
      val element = Flux.just(1, 2, 3).blockFirstMillis(10000)
      element shouldBe Option(1)
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

    ".blockLastMillis should block with parameter timeout in millis until the last element is emitted" in {
      val element = Flux.just(1, 2, 3).blockLastMillis(10000)
      element shouldBe Option(3)
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

    ".bufferMillis" - {
      "should split the values every timespan" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferMillis(1500))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2, 3), Seq(4))
          .verifyComplete()
      }
      "with timedScheduler should split the values every timespan" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferMillis(1500, Schedulers.timer()))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2, 3), Seq(4))
          .verifyComplete()
      }
      "with timespan and timeshift" - {
        val data = Table(
          ("scenario", "timespan", "timeshift", "expected"),
          ("timeshift > timespan", 1500, 2000, Seq(Seq(0l), Seq(1l, 2l), Seq(3l, 4l))),
          ("timeshift < timespan", 1500, 1000, Seq(Seq(0l), Seq(1l), Seq(2l), Seq(3l), Seq(4l))),
          ("timeshift = timespan", 1500, 1500, Seq(Seq(0l), Seq(1l), Seq(2l, 3l), Seq(4l)))
        )
        forAll(data) { (scenario, timespan, timeshift, expected) => {
          s"when $scenario" in {
            StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferMillis(timespan, timeshift))
              .thenAwait(5 seconds)
              .expectNext(expected: _*)
              .verifyComplete()
          }
        }
        }
      }
      "with timespan, timeshift and timed scheduler" - {
        val data = Table(
          ("scenario", "timespan", "timeshift", "expected"),
          ("timeshift > timespan", 1500, 2000, Seq(Seq(0l), Seq(1l, 2l), Seq(3l, 4l))),
          ("timeshift < timespan", 1500, 1000, Seq(Seq(0l), Seq(1l), Seq(2l), Seq(3l), Seq(4l))),
          ("timeshift = timespan", 1500, 1500, Seq(Seq(0l), Seq(1l), Seq(2l, 3l), Seq(4l)))
        )
        forAll(data) { (scenario, timespan, timeshift, expected) => {
          s"when $scenario" in {
            StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferMillis(timespan, timeshift, Schedulers.timer()))
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

    ".bufferTimeoutMillis" - {
      "with maxSize and timespan should split values every timespan or after maximum has been reached" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferTimeoutMillis(1, 1500))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2), Seq(3), Seq(4))
          .verifyComplete()
      }
      "with maxSize, timespan and timer should split values every timespan or after maximum has been reached using provided timer" in {
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferTimeoutMillis(1, 1500, VirtualTimeScheduler.create()))
          .thenAwait(5 seconds)
          .expectNext(Seq(0), Seq(1), Seq(2), Seq(3), Seq(4))
          .verifyComplete()
      }
      "with maxSize, timespan, timer and bufferSupplier should split values and using the buffer supplied by the supplier" in {
        val seqSet = mutable.Set[mutable.ListBuffer[Long]]()
        StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(5).bufferTimeoutMillis(1, 1500, VirtualTimeScheduler.create(), () => {
          val seq = mutable.ListBuffer[Long]()
          seqSet += seq
          seq
        }))
          .thenAwait(5 seconds)
          .expectNextMatches((seq: Seq[Long]) => {
            seq shouldBe Seq(0)
            seqSet should contain(seq)
            true
          })
          .expectNextMatches((seq: Seq[Long]) => {
            seq shouldBe Seq(1)
            seqSet should contain(seq)
            true
          })
          .expectNextMatches((seq: Seq[Long]) => {
            seq shouldBe Seq(2)
            seqSet should contain(seq)
            true
          })
          .expectNextMatches((seq: Seq[Long]) => {
            seq shouldBe Seq(3)
            seqSet should contain(seq)
            true
          })
          .expectNextMatches((seq: Seq[Long]) => {
            seq shouldBe Seq(4)
            seqSet should contain(seq)
            true
          })
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
      StepVerifier.withVirtualTime(() => Flux.interval(1 second).take(10).bufferWhile(l => l %2 == 0 ||  l %3 == 0))
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
        val fluxSupplier = () => Flux.just(1, 2, 3).cache(10 seconds)
        StepVerifier.withVirtualTime(fluxSupplier)
          .thenAwait(5 seconds)
          .expectNext(1, 2, 3)
          .verifyComplete()
/*
How to test noEvent?
        StepVerifier.withVirtualTime(fluxSupplier)
          .thenAwait(11 seconds)
          .expectNoEvent(1 second)
*/
      }
//      TODO: un-ignore this once the underlying flux has been fixed
      "with history and ttl should retain the cache up to ttl and max history" ignore {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3).cache(2, 10 seconds))
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
