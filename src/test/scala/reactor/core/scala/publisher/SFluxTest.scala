package reactor.core.scala.publisher

import java.io._
import java.nio.file.Files
import java.util
import java.util.concurrent.Callable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.function.Predicate

import org.reactivestreams.Subscription
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher._
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Duration, _}
import scala.io.Source
import scala.language.postfixOps
import scala.math.Ordering.IntOrdering
import scala.math.ScalaNumber
import scala.util.{Failure, Try}

class SFluxTest extends FreeSpec with Matchers with TableDrivenPropertyChecks {
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
        StepVerifier.create(SFlux.combineLatest(Mono.just(1), Mono.just(2), Mono.just(3), Mono.just(4)))
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
        val flux = SFlux.mergeSequentialIterable[Int](Iterable(SFlux(1, 2, 3), SFlux(2, 3, 4)), delayError = true, 8, 2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 2, 3, 4)
          .verifyComplete()
      }
    }

    ".never should never emit any signal" in {
      StepVerifier.create(SFlux.never())
        .expectSubscription()
        .expectNoEvent(Duration(1, "second"))
    }

    ".push should create a flux" in {
      StepVerifier.create(SFlux.push[Int]((emitter: FluxSink[Int]) => {
        emitter.next(1)
        emitter.next(2)
        emitter.complete()
      }))
        .expectNext(1, 2)
        .verifyComplete()
    }

    ".range should emit int within the range" in {
      StepVerifier.create(SFlux.range(10, 5))
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
        StepVerifier.create(
          SFlux.using[String, File](() => tempFile.toFile, (file: File) => SFlux.fromIterable[String](Source.fromFile(file).getLines().toIterable), (file: File) => {
            file.delete()
            ()
          }))
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
        StepVerifier.create(
          SFlux.using[String, File](() => tempFile.toFile, (file: File) => SFlux.fromIterable[String](Source.fromFile(file).getLines().toIterable), (file: File) => {
            file.delete()
            ()
          }, eager = true))
          .expectNext("1", "2")
          .verifyComplete()
      }
    }

    ".zip" - {
      "with source1, source2 and combinator should combine the data" in {
        val flux = SFlux.zipMap(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), (i: Int, str: String) => s"$i-$str")
        StepVerifier.create(flux)
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with source1 and source2 should emit flux with tuple2" in {
        StepVerifier.create(SFlux.zip(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three")))
          .expectNext((1, "one"), (2, "two"), (3, "three"))
          .verifyComplete()
      }
      "with source1, source2, source3 should emit flux with tuple3" in {
        StepVerifier.create(SFlux.zip3(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1l, 2l, 3l)))
          .expectNext((1, "one", 1l), (2, "two", 2l), (3, "three", 3l))
          .verifyComplete()
      }
      "with source1, source2, source3, source4 should emit flux with tuple4" in {
        StepVerifier.create(SFlux.zip4(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1l, 2l, 3l), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3"))))
          .expectNext((1, "one", 1l, BigDecimal("1")), (2, "two", 2l, BigDecimal("2")), (3, "three", 3l, BigDecimal("3")))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5 should emit flux with tuple5" in {
        StepVerifier.create(SFlux.zip5(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1l, 2l, 3l), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), SFlux.just("a", "i", "u")))
          .expectNext((1, "one", 1l, BigDecimal("1"), "a"), (2, "two", 2l, BigDecimal("2"), "i"), (3, "three", 3l, BigDecimal("3"), "u"))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5, source6 should emit flux with tuple6" in {
        StepVerifier.create(SFlux.zip6(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1l, 2l, 3l), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), SFlux.just("a", "i", "u"), SFlux.just("a", "b", "c")))
          .expectNext((1, "one", 1l, BigDecimal("1"), "a", "a"), (2, "two", 2l, BigDecimal("2"), "i", "b"), (3, "three", 3l, BigDecimal("3"), "u", "c"))
          .verifyComplete()
      }
      "with iterable and combinator should emit flux of combined data" in {
        StepVerifier.create(SFlux.zipMapIterable[String](Iterable(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three")), (array: Array[_]) => s"${array(0)}-${array(1)}"))
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with iterable, prefetch and combinator should emit flux of combined data" in {
        StepVerifier.create(SFlux.zipMapIterable[String](Iterable(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three")), (array: Array[_]) => s"${array(0)}-${array(1)}", 2))
          .expectNext("1-one", "2-two", "3-three")
          .verifyComplete()
      }
      "with combinator and varargs publisher should emit flux of combined data" in {
        StepVerifier.create(SFlux.zipMap((array: Array[AnyRef]) => s"${array(0)}-${array(1)}", Seq(SFlux.just(1, 2, 3), SFlux.just(10, 20, 30))))
          .expectNext("1-10", "2-20", "3-30")
          .verifyComplete()
      }
      "with combinator, prefetch and varargs publisher should emit flux of combined data" in {
        StepVerifier.create(SFlux.zipMap((array: Array[AnyRef]) => s"${array(0)}-${array(1)}", Seq(SFlux.just(1, 2, 3), SFlux.just(10, 20, 30)), 2))
          .expectNext("1-10", "2-20", "3-30")
          .verifyComplete()
      }
    }

    ".all should check every single element satisfy the predicate" in {
      StepVerifier.create(SFlux.just(1, 2, 3).all(i => i > 0))
        .expectNext(true)
        .verifyComplete()
    }

    ".any should check that there is at least one element satisfy the predicate" in {
      StepVerifier.create(SFlux.just(1, 2, 3).any(i => i % 2 == 0))
        .expectNext(true)
        .verifyComplete()
    }

    ".as should transform this flux to another publisher" in {
      StepVerifier.create(SFlux.just(1, 2, 3).as(Mono.from))
        .expectNext(1)
        .verifyComplete()
    }

    ".blockFirst" - {
      "should block and return the first element" in {
        val element = SFlux.just(1, 2, 3).blockFirst()
        element shouldBe Option(1)
      }
      "with duration should wait up to maximum provided duration" in {
        val element = SFlux.just(1, 2, 3).blockFirst(Duration(10, "seconds"))
        element shouldBe Option(1)
      }
    }

    ".blockLast" - {
      "should block and return the last element" in {
        val element = SFlux.just(1, 2, 3).blockLast()
        element shouldBe Option(3)
      }
      "with duration should wait up to the maximum provided duration to get the last element" in {
        val element = SFlux.just(1, 2, 3).blockLast(10 seconds)
        element shouldBe Option(3)
      }
    }

    ".buffer" - {
      "should buffer all element into a Seq" in {
        StepVerifier.create(SFlux.just(1, 2, 3).buffer())
          .expectNext(Seq(1, 2, 3))
          .verifyComplete()
      }
      "with maxSize should buffer element into a batch of Seqs" in {
        StepVerifier.create(SFlux.just(1, 2, 3).buffer(2))
          .expectNext(Seq(1, 2), Seq(3))
          .verifyComplete()
      }
      "with maxSize and sequence supplier should buffer element into a batch of sequences provided by supplier" in {
        val seqSet = mutable.Set[mutable.ListBuffer[Int]]()
        val flux = SFlux.just(1, 2, 3).buffer(2, () => {
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
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferTimeSpan(1500 milliseconds)())
          .thenAwait(5 seconds)
          .expectNext(Seq(0L), Seq(1L), Seq(2L, 3L), Seq(4L))
          .verifyComplete()
      }

      val data = Table(
        ("scenario", "timespan", "timeshift", "expected"),
        ("timeshift > timespan", 1500 milliseconds, 2 seconds, Seq(Seq(0l), Seq(1l, 2l), Seq(3l, 4l))),
        ("timeshift < timespan", 1500 milliseconds, 1 second, Seq(Seq(0l), Seq(1l), Seq(2l), Seq(3l), Seq(4l))),
        ("timeshift = timespan", 1500 milliseconds, 1500 milliseconds, Seq(Seq(0l), Seq(1l), Seq(2l, 3l), Seq(4l)))
      )
      "with duration and timeshift duration should split the values every timespan" in {
        forAll(data) { (scenario, timespan, timeshift, expected) => {
          StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferTimeSpan(timespan)(timeshift))
            .thenAwait(5 seconds)
            .expectNext(expected: _*)
            .verifyComplete()
        }
        }
      }
      "with other publisher should split the incoming value" in {
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5, 6, 7, 8).delayElements(1 second).buffer(Flux.interval(3 seconds)))
          .thenAwait(9 seconds)
          .expectNext(Seq(1, 2), Seq(3, 4, 5), Seq(6, 7, 8))
          .verifyComplete()
      }
      "with other publisher and buffer supplier" in {
        val buffer = ListBuffer.empty[ListBuffer[Int]]
        StepVerifier.withVirtualTime(() => Flux.just(1, 2, 3, 4, 5, 6, 7, 8).delayElements(1 second).buffer(Flux.interval(3 seconds), () => {
          val buff = ListBuffer.empty[Int]
          buffer += buff
          buff
        }))
          .thenAwait(9 seconds)
          .expectNext(Seq(1, 2), Seq(3, 4, 5), Seq(6, 7, 8))
          .verifyComplete()
        buffer shouldBe Seq(Seq(1, 2), Seq(3, 4, 5), Seq(6, 7, 8))
      }
    }

    ".bufferTimeout" - {
      "with maxSize and duration should split values every duration or after maximum has been reached" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferTimeout(3, 1200 milliseconds))
          .thenAwait(5 seconds)
          .expectNext(Seq(0l, 1), Seq(2l, 3), Seq(4l))
          .verifyComplete()
      }
    }

    ".bufferUntil" - {
      "should buffer until predicate expression returns true" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferUntil(l => l % 3 == 0))
          .thenAwait(5 seconds)
          .expectNext(Seq(0l), Seq(1l, 2l, 3l), Seq(4l))
          .verifyComplete()
      }
      "with cutBefore should control if the value that trigger the predicate be included in the previous or after sequence" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferUntil(l => l % 3 == 0, cutBefore = true))
          .thenAwait(5 seconds)
          .expectNext(Seq(0L, 1L, 2L), Seq(3L, 4L))
          .verifyComplete()
      }
    }

    ".bufferWhen" - {
      "should buffer with opening and closing publisher" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9).delayElements(1 second)
          .bufferWhen(Flux.interval(3 seconds), (_: Long) => SFlux.interval(3 seconds)))
          .thenAwait(9 seconds)
          .expectNext(Seq(3, 4, 5), Seq(6, 7, 8), Seq(9))
          .verifyComplete()
      }
      "with buffer supplier should buffer with opening and closing publisher and use the provided supplier" in {
        val buffer = ListBuffer.empty[ListBuffer[Int]]
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9).delayElements(1 second)
          .bufferWhen(SFlux.interval(3 seconds), (_: Long) => SFlux.interval(3 seconds), () => {
            val buff = ListBuffer.empty[Int]
            buffer += buff
            buff
          }))
          .thenAwait(9 seconds)
          .expectNext(Seq(3, 4, 5), Seq(6, 7, 8), Seq(9))
          .verifyComplete()

        buffer shouldBe Seq(Seq(3, 4, 5), Seq(6, 7, 8), Seq(9))
      }
    }

    ".bufferWhile should buffer while the predicate is true" in {
      StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(10).bufferWhile(l => l % 2 == 0 || l % 3 == 0))
        .thenAwait(10 seconds)
        .expectNext(Seq(0L), Seq(2L, 3L, 4L), Seq(6L), Seq(8L, 9L))
        .verifyComplete()
    }

    ".cache" - {
      "should turn this into a hot source" in {
        val flux = SFlux.just(1, 2, 3).cache()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with history should just retain up to history" in {
        val flux = SFlux.just(1, 2, 3).cache(2)
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
        StepVerifier.create(flux)
          .expectNext(2, 3)
          .verifyComplete()
      }
      "with ttl should retain the cache as long as the provided duration" in {
        try {
          StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(1 second).cache(ttl = 2 seconds))
            .thenAwait(3 seconds)
            .expectNext(1, 2, 3)
            .verifyComplete()
        } finally {
          VirtualTimeScheduler.reset()
        }

      }
      "with history and ttl should retain the cache up to ttl and max history" in {
        val supplier = () => {
          val tested = SFlux.just(1, 2, 3).cache(2, 10 seconds)
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
      val number = SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")).cast(classOf[ScalaNumber]).blockLast()
      number.get shouldBe a[ScalaNumber]
    }

    ".collect should collect the value into the supplied container" in {
      StepVerifier.create(SFlux.just(1, 2, 3).collect[ListBuffer[Int]](() => ListBuffer.empty, (buffer, v) => buffer += v))
        .expectNext(ListBuffer(1, 2, 3))
        .verifyComplete()
    }

    ".collectList should collect the value into a sequence" in {
      StepVerifier.create(SFlux.just(1, 2, 3).collectSeq())
        .expectNext(Seq(1, 2, 3))
        .verifyComplete()
    }

    ".collectMap" - {
      "with keyExtractor should collect the value and extract the key to return as Map" in {
        StepVerifier.create(SFlux.just(1, 2, 3).collectMap(i => i + 5))
          .expectNext(Map((6, 1), (7, 2), (8, 3)))
          .verifyComplete()
      }
      "with keyExtractor and valueExtractor should collect the value, extract the key and value from it" in {
        StepVerifier.create(SFlux.just(1, 2, 3).collectMap(i => i + 5, i => i + 6))
          .expectNext(Map((6, 7), (7, 8), (8, 9)))
          .verifyComplete()
      }
      "with keyExtractor, valueExtractor and mapSupplier should collect value, extract the key and value from it and put in the provided map" in {
        val map = mutable.HashMap[Int, Int]()
        StepVerifier.create(SFlux.just(1, 2, 3).collectMap(i => i + 5, i => i + 6, () => map))
          .expectNextMatches((m: Map[Int, Int]) => m == Map((6, 7), (7, 8), (8, 9)) && m == map)
          .verifyComplete()
      }
    }

    ".collectMultimap" - {
      "with keyExtractor should group the value based on the keyExtractor" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3))
          .expectNext(Map((0, Seq(3, 6, 9)), (1, Seq(1, 4, 7, 10)), (2, Seq(2, 5, 8))))
          .verifyComplete()
      }
      "with keyExtractor and valueExtractor should collect the value, extract the key and value from it" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3, i => i + 6))
          .expectNext(Map((0, Seq(9, 12, 15)), (1, Seq(7, 10, 13, 16)), (2, Seq(8, 11, 14))))
          .verifyComplete()
      }
      "with keyExtractor, valueExtractor and map supplier should collect the value, extract the key and value from it and put in the provided map" in {
        val map = mutable.HashMap[Int, util.Collection[Int]]()
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3, i => i + 6, () => map))
          .expectNextMatches((m: Map[Int, Traversable[Int]]) => {
            m shouldBe map.mapValues(vs => vs.toArray().toSeq)
            m shouldBe Map((0, Seq(9, 12, 15)), (1, Seq(7, 10, 13, 16)), (2, Seq(8, 11, 14)))
            true
          })
          .verifyComplete()
      }
    }

    ".collectSortedSeq" - {
      "should collect and sort the elements" in {
        StepVerifier.create(SFlux.just(5, 2, 3, 1, 4).collectSortedSeq())
          .expectNext(Seq(1, 2, 3, 4, 5))
          .verifyComplete()
      }
      "with ordering should collect and sort the elements based on the provided ordering" in {
        StepVerifier.create(SFlux.just(2, 3, 1, 4, 5).collectSortedSeq(new IntOrdering {
          override def compare(x: Int, y: Int): Int = Ordering.Int.compare(x, y) * -1
        }))
          .expectNext(Seq(5, 4, 3, 2, 1))
          .verifyComplete()
      }
    }

    ".compose should defer transformation of this flux to another publisher" in {
      StepVerifier.create(SFlux.just(1, 2, 3).compose(Mono.from))
        .expectNext(1)
        .verifyComplete()
    }

    ".concatMap" - {
      "with mapper should map the element sequentially" in {
        StepVerifier.create(SFlux.just(1, 2, 3).concatMap(i => SFlux.just(i * 2, i * 3)))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with mapper and prefetch should map the element sequentially" in {
        StepVerifier.create(SFlux.just(1, 2, 3).concatMap(i => SFlux.just(i * 2, i * 3), 2))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".concatMapDelayError" - {
      "with mapper, delayUntilEnd and prefetch" in {
        val flux = SFlux.just(1, 2, 3).concatMapDelayError(i => {
          if (i == 2) SFlux.error[Int](new RuntimeException("runtime ex"))
          else SFlux.just(i * 2, i * 3)
        }, delayUntilEnd = true, 2)
        StepVerifier.create(flux)
          .expectNext(2, 3, 6, 9)
          .expectError(classOf[RuntimeException])
          .verify()
      }
    }

    ".concatMapIterable" - {
      "with mapper should concat and map an iterable" in {
        StepVerifier.create(SFlux.just(1, 2, 3).concatMapIterable(i => Iterable(i * 2, i * 3)))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with mapper and prefetch should concat and map an iterable" in {
        StepVerifier.create(SFlux.just(1, 2, 3).concatMapIterable(i => Iterable(i * 2, i * 3), 2))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".count should return Mono which emit the number of value in this flux" in {
      StepVerifier.create(SFlux.just(10, 9, 8).count())
        .expectNext(3)
        .verifyComplete()
    }

    ".delayElement should delay every elements by provided delay in Duration" in {
      try {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(1 second).elapsed())
          .thenAwait(3 seconds)
          .expectNext((1000L, 1), (1000L, 2), (1000L, 3))
          .verifyComplete()
      } finally {
        VirtualTimeScheduler.reset()
      }
    }

    ".delaySequence" - {
      "should delay the element but not subscription" in {
        StepVerifier.withVirtualTime[(Long, (Long, Int))](() => SFlux.fromPublisher(SFlux.just[Int](1, 2, 3).delayElements(100 milliseconds).elapsed()).delaySequence(1 seconds).elapsed())
          .thenAwait(1300 milliseconds)
          .expectNext((1100l, (100l, 1)), (100l, (100l, 2)), (100l, (100l, 3)))
          .verifyComplete()
      }
      "with scheduler should use the scheduler" in {
        StepVerifier.withVirtualTime[(Long, (Long, Int))](() => SFlux.fromPublisher(SFlux.just[Int](1, 2, 3).delayElements(100 milliseconds).elapsed()).delaySequence(1 seconds, VirtualTimeScheduler.getOrSet()).elapsed())
          .thenAwait(1300 milliseconds)
          .expectNext((1100l, (100l, 1)), (100l, (100l, 2)), (100l, (100l, 3)))
          .verifyComplete()
      }
    }

    ".dematerialize should dematerialize the underlying flux" in {
      StepVerifier.create(SFlux.just(Signal.next(1), Signal.next(2)).dematerialize())
        .expectNext(1, 2)
        .verifyComplete
    }

    ".delaySubscription" - {
      "with delay duration should delay subscription as long as the provided duration" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delaySubscription(1 hour))
          .thenAwait(1 hour)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with another publisher should delay the current subscription until the other publisher completes" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delaySubscription(SMono.just("one").delaySubscription(1 hour)))
          .thenAwait(1 hour)
          .expectNext(1, 2, 3)
          .verifyComplete()

      }
    }

    ".distinct" - {
      "should make the flux distinct" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 2, 4, 3, 6).distinct())
          .expectNext(1, 2, 3, 4, 6)
          .verifyComplete()
      }
      "with keySelector should make the flux distinct by using the keySelector" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9).distinct(i => i % 3))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".distinctUntilChanged" - {
      "should make the flux always return different subsequent value" in {
        StepVerifier.create(SFlux.just(1, 2, 2, 3, 3, 3, 3, 2, 2, 5).distinctUntilChanged())
          .expectNext(1, 2, 3, 2, 5)
          .verifyComplete()
      }
      "with keySelector should make the flux always return different subsequent value based on keySelector" in {
        StepVerifier.create(SFlux.just(1, 2, 5, 8, 7, 4, 9, 6, 7).distinctUntilChanged(i => i % 3))
          .expectNext(1, 2, 7, 9, 7)
          .verifyComplete()
      }
      "with keySelector and keyComparator" in {
        StepVerifier.create(SFlux.just(1, 2, 5, 8, 7, 4, 9, 6, 7).distinctUntilChanged(i => i % 3, (x: Int, y: Int) => x == y))
          .expectNext(1, 2, 7, 9, 7)
          .verifyComplete()
      }
    }

    ".doAfterTerminate should perform an action after it is terminated" in {
      val flag = new AtomicBoolean(false)
      val flux = SFlux.just(1, 2, 3).doAfterTerminate(() => {
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
      val flux = SFlux.just(1, 2, 3).delayElements(1 minute)
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
      val flux = SFlux.just(1, 2, 3).doOnComplete(() => {
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
      val flux = SFlux.just(1, 2, 3).doOnEach(s => buffer += s"${s.getType.toString}-${s.get()}")
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete()
      buffer shouldBe Seq("onNext-1", "onNext-2", "onNext-3", "onComplete-null")
    }

    ".doOnError" - {
      "with callback function should call the callback function when the flux encounter error" in {
        val atomicBoolean = new AtomicBoolean(false)
        StepVerifier.create(SFlux.error(new RuntimeException())
          .doOnError(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "that check exception type should call the callback function when the flux encounter exception with the provided type" in {
        val atomicBoolean = new AtomicBoolean(false)
        StepVerifier.create(SFlux.error(new RuntimeException())
          .doOnError { case _: RuntimeException => atomicBoolean.compareAndSet(false, true) shouldBe true })
          .expectError(classOf[RuntimeException])
      }
    }

    ".doOnNext should call the callback function when the flux emit data successfully" in {
      val buffer = ListBuffer[Int]()
      StepVerifier.create(SFlux.just(1, 2, 3)
        .doOnNext(t => buffer += t))
        .expectNext(1, 2, 3)
        .verifyComplete()
      buffer shouldBe Seq(1, 2, 3)
    }

    ".doOnRequest should be called upon request" in {
      val atomicLong = new AtomicLong(0)
      val flux = SFlux.just[Long](1L)
        .doOnRequest(l => atomicLong.compareAndSet(0, l))
      flux.subscribe(new BaseSubscriber[Long] {
        override def hookOnSubscribe(subscription: Subscription): Unit = {
          subscription.request(1)
          ()
        }
      })
      atomicLong.get() shouldBe 1
    }

    ".doOnSubscribe should be called upon subscribe" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(Flux.just[Long](1L)
        .doOnSubscribe(_ => atomicBoolean.compareAndSet(false, true)))
        .expectNextCount(1)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".doOnTerminate should do something on terminate" in {
      val flag = new AtomicBoolean(false)
      StepVerifier.create(SFlux.just(1, 2, 3).doOnTerminate { () => flag.compareAndSet(false, true) })
        .expectNext(1, 2, 3)
        .expectComplete()
        .verify()
      flag shouldBe 'get
    }

    ".doFinally should call the callback" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(SFlux.just(1, 2, 3)
        .doFinally(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
        .expectNext(1, 2, 3)
        .verifyComplete()
      atomicBoolean shouldBe 'get
    }

    ".elapsed" - {
      "should provide the time elapse when this mono emit value" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delaySubscription(1 second).delayElements(1 second).elapsed(), 3)
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
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3)
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
        StepVerifier.create(SFlux.just(1, 2, 3).elementAt(2))
          .expectNext(3)
          .verifyComplete()
      }
      "should emit only the element at given index position or default value if the sequence is shorter" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4).elementAt(10, Option(-1)))
          .expectNext(-1)
          .verifyComplete()
      }
    }

    ".expandDeep" - {
      "should expand the flux" in {
        StepVerifier.create(SFlux.just("a", "b").expandDeep(s => SFlux.just(s"$s$s", s"$s$s$s")).take(5))
          .expectNext("a", "aa", "aaaa", "aaaaaaaa", "aaaaaaaaaaaaaaaa")
          .verifyComplete()
      }
      " with capacity hint should expand the flux" in {
        StepVerifier.create(SFlux.just("a", "b").expandDeep(s => SFlux.just(s"$s$s", s"$s$s$s"), 10).take(5))
          .expectNext("a", "aa", "aaaa", "aaaaaaaa", "aaaaaaaaaaaaaaaa")
          .verifyComplete()
      }
    }

    ".expand" - {
      "should expand the flux" in {
        StepVerifier.create(SFlux.just("a", "b").expand(s => SFlux.just(s"$s$s", s"$s$s$s")).take(10))
          .expectNext("a", "b", "aa", "aaa", "bb", "bbb", "aaaa", "aaaaaa", "aaaaaa", "aaaaaaaaa")
          .verifyComplete()
      }
      " with capacity hint should expand the flux" in {
        StepVerifier.create(SFlux.just("a", "b").expand(s => SFlux.just(s"$s$s", s"$s$s$s"), 5).take(10))
          .expectNext("a", "b", "aa", "aaa", "bb", "bbb", "aaaa", "aaaaaa", "aaaaaa", "aaaaaaaaa")
          .verifyComplete()
      }
    }

    ".filter should evaluate each value against given predicate" in {
      StepVerifier.create(SFlux.just(1, 2, 3).filter(i => i > 1))
        .expectNext(2, 3)
        .verifyComplete()
    }

    ".filterWhen" - {
      "should replay the value of mono if the first item emitted by the test is true" in {
        StepVerifier.create(SFlux.just(10, 20, 30).filterWhen((i: Int) => SMono.just(i % 2 == 0)))
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
      "with bufferSize should replay the value of mono if the first item emitted by the test is true" in {
        StepVerifier.create(SFlux.just(10, 20, 30).filterWhen((i: Int) => SMono.just(i % 2 == 0), 1))
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
    }

    ".flatMap should transform signal emitted by this flux into publishers" in {
      StepVerifier.create(SFlux.just(1, 2, 3).flatMap(_ => SMono.just("next"), _ => SMono.just("error"), () => SMono.just("complete")))
        .expectNext("next", "next", "next", "complete")
        .verifyComplete()
    }

    ".flatMapIterable" - {
      "should transform the items emitted by this flux into iterable" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapIterable(i => Iterable(i * 2, i * 3)))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with prefetch should transform the items and prefetch" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapIterable(i => Iterable(i * 2, i * 3), 2))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".flatMapSequential" - {
      "should transform items emitted by this flux into publisher then flatten them, in order" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapSequential(i => SFlux.just(i * 2, i * 3)))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with maxConcurrency, should do the same as before just with provided maxConcurrency" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapSequential(i => SFlux.just(i * 2, i * 3), 2))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with maxConcurrency and prefetch, should do the same as before just with provided maxConcurrency and prefetch" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapSequential(i => SFlux.just(i * 2, i * 3), 2, 2))
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
      "with delayError should respect whether error be delayed after current merge backlog" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMapSequential(i => {
          if (i == 2) Flux.error[Int](new RuntimeException("just an error"))
          else Flux.just(i * 2, i * 3)
        }, 2, 2, delayError = true))
          .expectNext(2, 3, 6, 9)
          .verifyError(classOf[RuntimeException])
      }
    }

    ".flatten" - {
      "with mapper should map the element sequentially" in {
        StepVerifier.create(SFlux.just(1, 2, 3).map(i => SFlux.just(i * 2, i * 3)).flatten)
          .expectNext(2, 3, 4, 6, 6, 9)
          .verifyComplete()
      }
    }

    ".groupBy" - {
      "with keyMapper should group the flux by the key mapper" in {
        val oddBuffer = ListBuffer.empty[Int]
        val evenBuffer = ListBuffer.empty[Int]

        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6).groupBy {
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        })
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

        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6).groupBy({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, identity, 6))
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

        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6).groupBy[String, String]({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, (i => i.toString): Int => String))
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

        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6).groupBy[String, String]({
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        }: Int => String, (i => i.toString): Int => String, 6))
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
      val flux = SFlux.just(1, 2, 3, 4, 5, 6).handle[Seq[Int]] {
        case (v, sink) =>
          buffer += v
          if (v == 6) {
            sink.next(buffer)
            sink.complete()
          }
      }
      val expected = Seq(1, 2, 3, 4, 5, 6)
      StepVerifier.create(flux)
        .expectNext(expected)
        .verifyComplete()
      buffer shouldBe expected
    }

    ".hasElement should return true if the flux has element matched" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).hasElement(4))
        .expectNext(true)
        .verifyComplete()
    }

    ".map should map the type of Flux from T to R" in {
      StepVerifier.create(SFlux.just(1, 2, 3).map(_.toString))
        .expectNext("1", "2", "3")
        .expectComplete()
        .verify()
    }

    ".or should emit from the fastest first sequence" in {
      StepVerifier.create(SFlux.just(10, 20, 30).or(SFlux.just(1, 2, 3).delayElements(1 second)))
        .expectNext(10, 20, 30)
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
