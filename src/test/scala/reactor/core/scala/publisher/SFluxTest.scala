package reactor.core.scala.publisher

import java.io._
import java.nio.file.Files
import java.util
import java.util.Comparator
import java.util.concurrent.Callable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.function.{Consumer, Predicate}

import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import org.reactivestreams.Subscription
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.time.{Seconds, Span}
import reactor.core.publisher.BufferOverflowStrategy.DROP_LATEST
import reactor.core.publisher.{Flux => JFlux, _}
import reactor.core.scala.Scannable
import reactor.core.scala.publisher.ScalaConverters._
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler
import reactor.util.concurrent.Queues
import reactor.util.scala.retry.SRetry

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{Duration, _}
import scala.io.Source
import scala.language.postfixOps
import scala.math.Ordering.IntOrdering
import scala.math.ScalaNumber
import scala.util.{Failure, Try}

class SFluxTest extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks with TestSupport with IdiomaticMockito with ArgumentMatchersSugar
  with PatienceConfiguration with Eventually {

  implicit override def patienceConfig: PatienceConfig = PatienceConfig(Span(3, Seconds))

  "SFlux" - {
    ".apply should return a proper SFlux when provided a Publisher" in {
      StepVerifier.create(SFlux(JFlux.just(1,2,3)))
        .expectNext(1,2,3)
        .verifyComplete()
    }

    ".apply should return a proper SFlux when provided a list of elements" in {
      StepVerifier.create(SFlux(1, 2, 3))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".asScala should transform Flux into SFlux" in {
      JFlux.just(1, 2, 3).asScala shouldBe an[SFlux[_]]
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
        val flux = SFlux.concat(SFlux.just(1, 2, 3), Mono.just(3), SFlux.just(3, 4))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3, 3, 3, 4)
          .verifyComplete()
      }
    }

    ".concatDelayError" - {
      "with varargs of publishers should concatenate all sources emitted from parents" in {
        val flux = SFlux.concatDelayError(Mono.just(1), Mono.just(2), Mono.just(3))
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".create should create a flux" in {
      val flux = SFlux.create((emitter: FluxSink[Int]) => {
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

    ".deferWithContext should create a flux with context" in { //TODO: How to verify against a Context?
      StepVerifier.create(SFlux.deferWithContext(context => {
        SFlux.just(1, 2, 3).doOnNext(i => context.put("data", i))
      }))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".empty should return an empty SFlux" in {
      StepVerifier.create(SFlux.empty)
        .verifyComplete()
    }

    ".firstEmitter" - {
      "with varargs of publisher should create Flux based on the publisher that emit first onNext or onComplete or onError" in {
        val flux: SFlux[Long] = SFlux.firstEmitter(SMono.delay(Duration("10 seconds")), SMono.just(1L))
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
        val flux = SFlux.generate(
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
          .expectNext((0L, "a"), (1L, "b"), (2L, "c"))
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
        val flux = SFlux.just(1)
        StepVerifier.create(flux)
          .expectNext(1)
          .verifyComplete()
      }
    }

    ".merge" - {
      "with sequence of publisher should merge the underlying publisher" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just(1, 2, 3, 4, 5).delayElements(5 seconds)
          val sFlux2 = SFlux.just(10, 20, 30, 40, 50).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.merge(Seq(sFlux1, sFlux2))
        }).thenAwait(30 seconds)
          .expectNext(1, 10, 2, 20, 3, 30, 4, 40, 5, 50)
          .verifyComplete()
      }
      "with sequence of publisher and prefetch should merge the underlying publisher" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just(1, 2, 3, 4, 5).delayElements(5 seconds)
          val sFlux2 = SFlux.just(10, 20, 30, 40, 50).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.merge(Seq(sFlux1, sFlux2), 2)
        }).thenAwait(30 seconds)
          .expectNext(1, 10, 2, 20, 3, 30, 4, 40, 5, 50)
          .verifyComplete()
      }
      "with sequence of publisher and prefetch and delayError should merge the underlying publisher" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just(1, 2, 3, 4, 5).delayElements(5 seconds)
          val sFlux2 = SFlux.just(10, 20, 30, 40, 50).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.merge(Seq(sFlux1, sFlux2), 2, delayError = true)
        }).thenAwait(30 seconds)
          .expectNext(1, 10, 2, 20, 3, 30, 4, 40, 5, 50)
          .verifyComplete()
      }
    }

    ".mergeOrdered" - {
      "with sequence of publisher should merge the value in orderly fashion" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just[Integer](1, 20, 40, 60, 80).delayElements(5 seconds)
          val sFlux2 = SFlux.just[Integer](10, 30, 50, 70).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.mergeOrdered(Seq(sFlux1, sFlux2))
        }).thenAwait(30 seconds)
          .expectNext(1, 10, 20, 30, 40, 50, 60, 70, 80)
          .verifyComplete()
      }
      "with sequence of publisher and prefetch should merge the value in orderly fashion" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just[Integer](1, 20, 40, 60, 80).delayElements(5 seconds)
          val sFlux2 = SFlux.just[Integer](10, 30, 50, 70).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.mergeOrdered(Seq(sFlux1, sFlux2), 2)
        }).thenAwait(30 seconds)
          .expectNext(1, 10, 20, 30, 40, 50, 60, 70, 80)
          .verifyComplete()
      }
      "with sequence of publisher and prefetch and Comparable should merge the value in orderly fashion" in {
        StepVerifier.withVirtualTime(() => {
          val sFlux1 = SFlux.just[Integer](1, 20, 40, 60, 80).delayElements(5 seconds)
          val sFlux2 = SFlux.just[Integer](10, 30, 50, 70).delayElements(5 seconds).delaySubscription(2500 millisecond)
          SFlux.mergeOrdered(Seq(sFlux1, sFlux2), 5, Comparator.naturalOrder().reversed())
        }).thenAwait(30 seconds)
          .expectNext(10, 30, 50, 70, 1, 20, 40, 60, 80)
          .verifyComplete()
      }
    }

    ".mergeSequential*" - {
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
      "should compile properly" in {
        val iterators: Iterable[SFlux[String]] = for (_ <- 0 to 4000) yield SFlux.interval(5 seconds).map(_.toString)
        val publishers: SFlux[SFlux[String]] = SFlux.just(iterators).flatMapIterable(identity)
        noException should be thrownBy SFlux.mergeSequentialPublisher(publishers)
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

    ".raiseError" - {
      "with throwable and whenRequest flag should" - {
        "emit onError during onSubscribe if the flag is false" in {
          val flag = new AtomicBoolean(false)
          val flux = SFlux.raiseError(new RuntimeException("Error message"))
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
          val flux = SFlux.raiseError(new RuntimeException(), whenRequested = true)
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
        StepVerifier.create(SFlux.zip3(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1L, 2L, 3L)))
          .expectNext((1, "one", 1L), (2, "two", 2L), (3, "three", 3L))
          .verifyComplete()
      }
      "with source1, source2, source3, source4 should emit flux with tuple4" in {
        StepVerifier.create(SFlux.zip4(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1L, 2L, 3L), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3"))))
          .expectNext((1, "one", 1L, BigDecimal("1")), (2, "two", 2L, BigDecimal("2")), (3, "three", 3L, BigDecimal("3")))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5 should emit flux with tuple5" in {
        StepVerifier.create(SFlux.zip5(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1L, 2L, 3L), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), SFlux.just("a", "i", "u")))
          .expectNext((1, "one", 1L, BigDecimal("1"), "a"), (2, "two", 2L, BigDecimal("2"), "i"), (3, "three", 3L, BigDecimal("3"), "u"))
          .verifyComplete()
      }
      "with source1, source2, source3, source4, source5, source6 should emit flux with tuple6" in {
        StepVerifier.create(SFlux.zip6(SFlux.just(1, 2, 3), SFlux.just("one", "two", "three"), SFlux.just(1L, 2L, 3L), SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")), SFlux.just("a", "i", "u"), SFlux.just("a", "b", "c")))
          .expectNext((1, "one", 1L, BigDecimal("1"), "a", "a"), (2, "two", 2L, BigDecimal("2"), "i", "b"), (3, "three", 3L, BigDecimal("3"), "u", "c"))
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
      StepVerifier.create(SFlux.just(1, 2, 3).as(SMono.fromPublisher))
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
        val originalFlux = SFlux.just(1, 2, 3, 4, 5)
        val data = Table(
          ("scenario", "maxSize", "skip", "expectedSequence"),
          ("maxSize < skip", 2, 3, Iterable(Seq(1, 2), Seq(4, 5))),
          ("maxSize > skip", 3, 2, Iterable(Seq(1, 2, 3), Seq(3, 4, 5), Seq(5))),
          ("maxSize = skip", 2, 2, Iterable(Seq(1, 2), Seq(3, 4), Seq(5)))
        )
        forAll(data) { (scenario, maxSize, skip, expectedSequence) => {
          s"when $scenario" in {
            val flux = originalFlux.buffer(maxSize)(skip)
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
          ("maxSize < skip", 1, 2, Iterable(Seq(1), Seq(3), Seq(5))),
          ("maxSize > skip", 3, 2, Iterable(Seq(1, 2, 3), Seq(3, 4, 5), Seq(5))),
          ("maxSize = skip", 2, 2, Iterable(Seq(1, 2), Seq(3, 4), Seq(5)))
        )
        forAll(data) { (scenario, maxSize, skip, expectedSequence) => {
          val iterator = expectedSequence.iterator
          s"when $scenario" in {
            val originalFlux = SFlux.just(1, 2, 3, 4, 5)
            val seqSet = mutable.Set[mutable.ListBuffer[Int]]()
            val flux = originalFlux.buffer(maxSize, () => {
              val seq = mutable.ListBuffer[Int]()
              seqSet += seq
              seq
            })(skip)
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
        ("timeshift > timespan", 1500 milliseconds, 2 seconds, Seq(Seq(0L), Seq(1L, 2L), Seq(3L, 4L))),
        ("timeshift < timespan", 1500 milliseconds, 1 second, Seq(Seq(0L), Seq(0L, 1L), Seq(1L, 2L), Seq(2L, 3L), Seq(3L, 4L), Seq(4L))),
        ("timeshift = timespan", 1500 milliseconds, 1500 milliseconds, Seq(Seq(0L), Seq(1L), Seq(2L, 3L), Seq(4L)))
      )
      "with duration and timeshift duration should split the values every timespan" in {
        forAll(data) { (_, timespan, timeshift, expected) => {
          StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferTimeSpan(timespan)(timeshift))
            .thenAwait(5 seconds)
            .expectNext(expected: _*)
            .verifyComplete()
        }
        }
      }
      "with other publisher should split the incoming value" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5, 6, 7, 8).delayElements(1 second).bufferPublisher(SFlux.interval(3 seconds)))
          .thenAwait(9 seconds)
          .expectNext(Seq(1, 2), Seq(3, 4, 5), Seq(6, 7, 8))
          .verifyComplete()
      }
      "with other publisher and buffer supplier" in {
        val buffer = ListBuffer.empty[ListBuffer[Int]]
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5, 6, 7, 8).delayElements(1 second).bufferPublisher(SFlux.interval(3 seconds), () => {
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
          .expectNext(Seq(0L, 1), Seq(2L, 3), Seq(4L))
          .verifyComplete()
      }
    }

    ".bufferUntil" - {
      "should buffer until predicate expression returns true" in {
        StepVerifier.withVirtualTime(() => SFlux.interval(1 second).take(5).bufferUntil(l => l % 3 == 0))
          .thenAwait(5 seconds)
          .expectNext(Seq(0L), Seq(1L, 2L, 3L), Seq(4L))
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
          .bufferWhen(SFlux.interval(3 seconds), (_: Long) => SFlux.interval(3 seconds)))
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
        val supplier: () => SFlux[Int] = () => {
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
      val number = SFlux.just(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")).cast[ScalaNumber].blockLast()
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
            m shouldBe map.mapValues(vs => vs.toArray().toSeq).toMap
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
      StepVerifier.create(SFlux.just(1, 2, 3).compose(SMono.fromPublisher))
        .expectNext(1)
        .verifyComplete()
    }
    ".transformDeferred should defer transformation of this flux to another publisher" in {
      StepVerifier.create(SFlux.just(1, 2, 3).transformDeferred(SMono.fromPublisher))
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
          if (i == 2) SFlux.raiseError[Int](new RuntimeException("runtime ex"))
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

    ".concatWith should concatenate with another publisher" in {
      StepVerifier.create(SFlux.just(1, 2, 3).concatWith(SFlux.just(6, 7, 8)))
        .expectNext(1, 2, 3, 6, 7, 8)
        .verifyComplete()
    }

    "++ should concatenate mono with another source" in {
      StepVerifier.create(SFlux.just(1) ++ SFlux.just(2, 3))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".count should return Mono which emit the number of value in this flux" in {
      StepVerifier.create(SFlux.just(10, 9, 8).count())
        .expectNext(3)
        .verifyComplete()
    }

    ".defaultIfEmpty should use the provided default value if the SFlux is empty" in {
      StepVerifier.create(SFlux.empty[Int].defaultIfEmpty(-1))
        .expectNext(-1)
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
          .expectNext((1100L, (100L, 1)), (100L, (100L, 2)), (100L, (100L, 3)))
          .verifyComplete()
      }
      "with scheduler should use the scheduler" in {
        StepVerifier.withVirtualTime[(Long, (Long, Int))](() => SFlux.fromPublisher(SFlux.just[Int](1, 2, 3).delayElements(100 milliseconds).elapsed()).delaySequence(1 seconds, VirtualTimeScheduler.getOrSet()).elapsed())
          .thenAwait(1300 milliseconds)
          .expectNext((1100L, (100L, 1)), (100L, (100L, 2)), (100L, (100L, 3)))
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
      flag shouldBe Symbol("get")
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
      atomicBoolean shouldBe Symbol("get")
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
      flag shouldBe Symbol("get")
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
        StepVerifier.create(SFlux.raiseError(new RuntimeException())
          .doOnError(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "that check exception type should call the callback function when the flux encounter exception with the provided type" in {
        val atomicBoolean = new AtomicBoolean(false)
        StepVerifier.create(SFlux.raiseError(new RuntimeException())
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
      StepVerifier.create(SFlux.just[Long](1L)
        .doOnSubscribe(_ => atomicBoolean.compareAndSet(false, true)))
        .expectNextCount(1)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".doOnTerminate should do something on terminate" in {
      val flag = new AtomicBoolean(false)
      StepVerifier.create(SFlux.just(1, 2, 3).doOnTerminate { () => flag.compareAndSet(false, true) })
        .expectNext(1, 2, 3)
        .expectComplete()
        .verify()
      flag shouldBe Symbol("get")
    }

    ".doFinally should call the callback" in {
      val atomicBoolean = new AtomicBoolean(false)
      StepVerifier.create(SFlux.just(1, 2, 3)
        .doFinally(_ => atomicBoolean.compareAndSet(false, true) shouldBe true))
        .expectNext(1, 2, 3)
        .verifyComplete()
      atomicBoolean shouldBe Symbol("get")
    }

    ".drop should return Flux that drop a number of elements" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4).drop(2))
        .expectNext(3, 4)
        .verifyComplete()
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
          if (i == 2) SFlux.raiseError[Int](new RuntimeException("just an error"))
          else SFlux.just(i * 2, i * 3)
        }, 2, 2, delayError = true))
          .expectNext(2, 3, 6, 9)
          .verifyError(classOf[RuntimeException])
      }
    }

    ".flatMap(Function,Int,Int)" - {
      "should transform items emitted by this flux into publishers then flatten them in the order they complete" in {
        StepVerifier.withVirtualTime(() =>
            SFlux.just(2, 3, 1)
                 .flatMap(i => SFlux.just(i * 2, i * 3).delaySequence(5-i seconds)))
          .thenAwait(2 seconds)
          .expectNext(6, 9)
          .thenAwait(1 second)
          .expectNext(4, 6)
          .thenAwait(1 second)
          .expectNext(2, 3)
          .verifyComplete()
      }
      "with limited maxConcurrency, should further delay an element that would have otherwise returned sooner" in {
        StepVerifier.withVirtualTime(() =>
            SFlux.just(2, 1, 3)
                 .flatMap(i => SFlux.just(i * 2, i * 3).delaySequence(5-i seconds), 2))
          .thenAwait(3 seconds)
          .expectNext(4, 6)
          .thenAwait(1 second)
          .expectNext(2, 3)
          .thenAwait(1 second)
          .expectNext(6, 9)
          .verifyComplete()
      }
      "with delayError should respect whether error be delayed after current merge backlog" in {
        StepVerifier.create(SFlux.just(1, 2, 3).flatMap(i => {
          if (i == 2) SFlux.raiseError[Int](new RuntimeException("just an error"))
          else SFlux.just(i * 2, i * 3)
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

    ".fold should apply a binary operator to an initial value and all element of the source" in {
      val mono = SFlux.just(1, 2, 3).foldLeft(0)((acc: Int, el: Int) => acc + el)
      StepVerifier.create(mono)
        .expectNext(6)
        .verifyComplete()
    }

    ".foldLeft should apply a binary operator to an initial value and all element of the source" in {
      val mono = SFlux.just(1, 2, 3).foldLeft(0)((acc: Int, el: Int) => acc + el)
      StepVerifier.create(mono)
        .expectNext(6)
        .verifyComplete()
    }

    ".groupBy" - {
      "with keyMapper should group the flux by the key mapper" in {
        val oddBuffer = ListBuffer.empty[Int]
        val evenBuffer = ListBuffer.empty[Int]

        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6).groupBy {
          case even: Int if even % 2 == 0 => "even"
          case _: Int => "odd"
        })
          .expectNextMatches(new Predicate[SGroupedFlux[String, Int]] {
            override def test(t: SGroupedFlux[String, Int]): Boolean = {
              t.subscribe(x => oddBuffer append x)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[SGroupedFlux[String, Int]] {
            override def test(t: SGroupedFlux[String, Int]): Boolean = {
              t.subscribe(x => evenBuffer append x)
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
          .expectNextMatches(new Predicate[SGroupedFlux[String, Int]] {
            override def test(t: SGroupedFlux[String, Int]): Boolean = {
              t.subscribe(x => oddBuffer append x)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[SGroupedFlux[String, Int]] {
            override def test(t: SGroupedFlux[String, Int]): Boolean = {
              t.subscribe(x => evenBuffer append x)
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
          .expectNextMatches(new Predicate[SGroupedFlux[String, String]] {
            override def test(t: SGroupedFlux[String, String]): Boolean = {
              t.subscribe(x => oddBuffer append x)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[SGroupedFlux[String, String]] {
            override def test(t: SGroupedFlux[String, String]): Boolean = {
              t.subscribe(x => evenBuffer append x)
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
          .expectNextMatches(new Predicate[SGroupedFlux[String, String]] {
            override def test(t: SGroupedFlux[String, String]): Boolean = {
              t.subscribe(x => oddBuffer append x)
              t.key() == "odd"
            }
          })
          .expectNextMatches(new Predicate[SGroupedFlux[String, String]] {
            override def test(t: SGroupedFlux[String, String]): Boolean = {
              t.subscribe(x => evenBuffer append x)
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
      val flux = SFlux.just(1, 2, 3, 4, 5, 6).handle[ListBuffer[Int]] {
        case (v, sink) =>
          buffer += v
          if (v == 6) {
            sink.next(buffer)
            sink.complete()
          }
      }
      val expected = ListBuffer(1, 2, 3, 4, 5, 6)
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

    ".hasElements should return true if this flux has at least one element" in {
      StepVerifier.create(SFlux.just(1, 2, 3).hasElements)
        .expectNext(true)
        .verifyComplete()
    }

    ".head should return Mono that emit the first value of Flux" in {
      StepVerifier.create(SFlux.just(1, 2, 3).head)
        .expectNext(1)
        .verifyComplete()
    }

    ".ignoreElements should ignore all elements and only reacts on termination" in {
      StepVerifier.create(SFlux.just(1, 2, 3).ignoreElements())
        .verifyComplete()
    }

    ".last" - {
      "should give last element" in {
        StepVerifier.create(SFlux.just(1, 2, 3).last())
          .expectNext(3)
          .verifyComplete()
      }
      "with defaultValue should give the last element or defaultValue if the flux is empty" in {
        StepVerifier.create(SFlux.empty[Int].last(Option(5)))
          .expectNext(5)
          .verifyComplete()
      }
    }

    ".map should map the type of Flux from T to R" in {
      StepVerifier.create(SFlux.just(1, 2, 3).map(_.toString))
        .expectNext("1", "2", "3")
        .expectComplete()
        .verify()
    }

    ".materialize should convert the flux into a flux that emit its signal" in {
      StepVerifier.create(SFlux.just(1, 2, 3).materialize())
        .expectNext(Signal.next(1), Signal.next(2), Signal.next(3), Signal.complete[Int]())
        .verifyComplete()
    }

    ".max" - {
      "of numbers should emit the highest value of ordering" in {
        StepVerifier.create(SFlux.just(4, 3, 6, 5, 8, 7).max)
          .expectNext(Option(8))
          .verifyComplete()
      }
      "of strings should emit the highest value of ordering" in {
        StepVerifier.create(SFlux.just("d", "c", "g", "j", "i").max)
          .expectNext(Option("j"))
          .verifyComplete()
      }
    }

    ".mergeWith should merge with the provided publisher so they may interleave" in {
      StepVerifier.withVirtualTime(() => SFlux.just(1, 3, 5).delayElements(1 second)
        .mergeWith(SFlux.just(2, 4, 6).delayElements(1 second).delaySubscription(500 milliseconds)))
        .thenAwait(7 seconds)
        .expectNext(1, 2, 3, 4, 5, 6)
        .verifyComplete()
    }
    
    ".metrics should be a nop since Micrometer is not on the classpath" in {
      val flux = JFlux.just("plain", "awesome")
      flux.asScala.metrics.coreFlux shouldBe theSameInstanceAs(flux)
    }

    ".min" - {
      "of numbers should emit the lowest value of ordering" in {
        StepVerifier.create(SFlux.just(4, 3, 6, 5, 8).min)
          .expectNext(Option(3))
          .verifyComplete()
      }
      "of strings should emit the lowest value of ordering" in {
        StepVerifier.create(SFlux.just("d", "c", "g", "j").min)
          .expectNext(Option("c"))
          .verifyComplete()
      }
    }

    ".name should call the underlying Flux.name method" in {
      val name = "one two three four"
      val flux = SFlux.just(1, 2, 3, 4).name(name)
      val scannable: Scannable = Scannable.from(Option(flux))
      scannable.name shouldBe name
    }

    ".next should emit only the first item" in {
      StepVerifier.create(SFlux.just(1, 2, 3).next())
        .expectNext(1)
        .verifyComplete()
    }

    ".nonEmpty should return true if this flux has at least one element" in {
      StepVerifier.create(SFlux.just(1, 2, 3).nonEmpty)
        .expectNext(true)
        .verifyComplete()
    }

    ".ofType should filter the value emitted by this flux according to the class" in {
      StepVerifier.create(SFlux.just(1, "2", "3", 4).ofType[String])
        .expectNext("2", "3")
        .verifyComplete()
    }

    ".onBackpressureBuffer" - {
      "should call the underlying method" in {
        val jFlux = spy(JFlux.just(1, 2, 3))
        val flux = SFlux.fromPublisher(jFlux)
        flux.onBackpressureBuffer()
        jFlux.onBackpressureBuffer() was called
      }
      "with maxSize should call the underlying method" in {
        val jFlux = spy(JFlux.just(1, 2, 3))
        val flux = SFlux.fromPublisher(jFlux)
        flux.onBackpressureBuffer(5)
        jFlux.onBackpressureBuffer(5) was called
      }
      "with maxSize and onOverflow handler" in {
        val jFlux = spy(JFlux.just(1, 2, 3))
        val flux = SFlux.fromPublisher(jFlux)
        flux.onBackpressureBuffer(5, _ => ())
        jFlux.onBackpressureBuffer(eqTo(5), any[Consumer[Int]]) was called
      }
      "with maxSize and overflow strategy" in {
        val jFlux = spy(JFlux.just(1, 2, 3))
        val flux = SFlux.fromPublisher(jFlux)
        flux.onBackpressureBuffer(5, DROP_LATEST)
        jFlux.onBackpressureBuffer(5, DROP_LATEST) was called
      }
      "with maxSize, overflow handler and overflow strategy" in {
        val jFlux = spy(JFlux.just(1, 2, 3))
        val flux = SFlux.fromPublisher(jFlux)
        flux.onBackpressureBuffer(5, _ => (), DROP_LATEST)
        jFlux.onBackpressureBuffer(eqTo(5), any[Consumer[Int]], eqTo(DROP_LATEST)) was called
      }
    }

    ".onBackpressureDrop" - {
      val jFlux = spy(JFlux.just(1, 2, 3))
      val flux = SFlux.fromPublisher(jFlux)
      "without consumer" in {
        flux.onBackpressureDrop()
        jFlux.onBackpressureDrop() was called
      }
      "with consumer" in {
        flux.onBackpressureDrop(_ => ())
        jFlux.onBackpressureDrop(any[Consumer[Int]]) was called
      }
    }

    ".onBackpressureError" in {
      val jFlux = spy(JFlux.just(1, 2, 3))
      val flux = SFlux.fromPublisher(jFlux)
      flux.onBackpressureError()
      jFlux.onBackpressureError() was called
    }

    ".onBackpressureLatest" in {
      val jFlux = spy(JFlux.just(1, 2, 3))
      val flux = SFlux.fromPublisher(jFlux)
      flux.onBackpressureLatest()
      jFlux.onBackpressureLatest() was called
    }

    ".onErrorMap" - {
      "with mapper should map the error" in {
        StepVerifier.create(SFlux.raiseError[Int](new RuntimeException("runtime exception"))
          .onErrorMap((t: Throwable) => new UnsupportedOperationException(t.getMessage)))
          .expectError(classOf[UnsupportedOperationException])
          .verify()
      }

      "with type and mapper should map the error if the error is of the provided type" in {
        StepVerifier.create(SFlux.raiseError[Int](new RuntimeException("runtime ex"))
          .onErrorMap { throwable: Throwable =>
            throwable match {
              case t: RuntimeException => new UnsupportedOperationException(t.getMessage)
            }
          })
          .expectError(classOf[UnsupportedOperationException])
          .verify()
      }
    }

    ".onErrorRecover" - {
      "should recover with a Flux of element that has been recovered" in {
        val convoy = SFlux.just[Vehicle](Sedan(1), Sedan(2)).concatWith(SFlux.raiseError(new RuntimeException("oops")))
          .onErrorRecover { case _ => Truck(5) }
        StepVerifier.create(convoy)
          .expectNext(Sedan(1), Sedan(2), Truck(5))
          .verifyComplete()
      }
    }

    ".onErrorRecoverWith" - {
      "should recover with a Flux of element that is provided for recovery" in {
        val convoy = SFlux.just[Vehicle](Sedan(1), Sedan(2)).concatWith(SFlux.raiseError(new RuntimeException("oops")))
          .onErrorRecoverWith { case _ => SFlux.just(Truck(5)) }
        StepVerifier.create(convoy)
          .expectNext(Sedan(1), Sedan(2), Truck(5))
          .verifyComplete()
      }
    }

    ".onErrorResume" - {
      "should resume with a fallback publisher when error happen" in {
        StepVerifier.create(SFlux.just(1, 2).concatWith(SMono.raiseError(new RuntimeException("exception"))).onErrorResume((_: Throwable) => SFlux.just(10, 20, 30)))
          .expectNext(1, 2, 10, 20, 30)
          .verifyComplete()
      }
    }

    ".onErrorReturn" - {
      "should return the fallback value if error happen" in {
        StepVerifier.create(SFlux.just(1, 2).concatWith(SMono.raiseError(new RuntimeException("exc"))).onErrorReturn(10))
          .expectNext(1, 2, 10)
          .verifyComplete()
      }
      "with predicate and fallbackValue should return the fallback value if the predicate is true" in {
        val predicate = (_: Throwable).isInstanceOf[RuntimeException]
        StepVerifier.create(SFlux.just(1, 2).concatWith(SMono.raiseError(new RuntimeException("exc")))
          .onErrorReturn(10, predicate))
          .expectNext(1, 2, 10)
          .verifyComplete()
      }
    }

    ".or should emit from the fastest first sequence" in {
      StepVerifier.create(SFlux.just(10, 20, 30).or(SFlux.just(1, 2, 3).delayElements(1 second)))
        .expectNext(10, 20, 30)
        .verifyComplete()
    }

    ".publish" - {
      "without transformer should produce connectable flux" in {
        val buffer = mutable.ListBuffer.empty[Int]
        val base = SFlux.just(1, 2, 3, 4, 5).delayElements(1 second).publish().autoConnect()
        base.subscribe()
        Thread.sleep(1000)
        base.subscribe(i => buffer += i)
        eventually {
          buffer.size should be > 1
          buffer should not contain 1
        }
      }
    }

    ".publishNext should make this flux a hot mono" in {
      StepVerifier.create(SFlux.just(1, 2, 3).publishNext())
        .expectNext(1)
        .verifyComplete()
    }

    ".reduce" - {
      "should aggregate the values" in {
        StepVerifier.create(SFlux.just(1, 2, 3).reduce(_ + _))
          .expectNext(6)
          .verifyComplete()
      }
      "with initial value should aggregate the values with initial one" in {
        StepVerifier.create(SFlux.just(1, 2, 3).reduce("0")((agg, v) => s"$agg-${v.toString}"))
          .expectNext("0-1-2-3")
          .verifyComplete()
      }
    }

    ".reduceWith should aggregate the values with initial one" in {
      StepVerifier.create(SFlux.just(1, 2, 3).reduceWith[String](() => "0", (agg, v) => s"$agg-${v.toString}"))
        .expectNext("0-1-2-3")
        .verifyComplete()
    }

    ".repeat" - {
      "with predicate should repeat the subscription if the predicate returns true" in {
        val counter = new AtomicInteger(0)
        StepVerifier.create(SFlux.just(1, 2, 3).repeat(predicate = () => {
          if (counter.getAndIncrement() == 0) true
          else false
        }))
          .expectNext(1, 2, 3, 1, 2, 3)
          .verifyComplete()
      }
      "with numRepeat should repeat as many as the provided parameter" in {
        StepVerifier.create(SFlux.just(1, 2, 3).repeat(3))
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with numRepeat and predicate should repeat as many as provided parameter and as long as the predicate returns true" in {
        val flux = SFlux.just(1, 2, 3).repeat(3, () => true)
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
        StepVerifier.create(SFlux.just(1, 2, 3).concatWith(SMono.raiseError(new RuntimeException("ex"))).retry(3))
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "with predicate will retry until the predicate returns false" in {
        val counter = new AtomicInteger(0)
        StepVerifier.create(SFlux.just(1, 2, 3).concatWith(SMono.raiseError(new RuntimeException("ex"))).retry(retryMatcher = (_: Throwable) =>
          if (counter.getAndIncrement() > 0) false
          else true
        ))
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
      "with numRetries and predicate should retry as many as provided numRetries and predicate returns true" in {
        val counter = new AtomicInteger(0)
        val flux = SFlux.just(1, 2, 3).concatWith(SMono.raiseError(new RuntimeException("ex"))).retry(3, { _ =>
          if (counter.getAndIncrement() > 5) false
          else true
        })
        StepVerifier.create(flux)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectNext(1, 2, 3)
          .expectError(classOf[RuntimeException])
          .verify()
      }
    }

/*
//this is deprecated, should be removed on 0.7.x
    ".retryWhen should retry the companion publisher produces onNext signal" in {
      val counter = new AtomicInteger(0)
      val flux = SFlux.just(1, 2, 3).concatWith(SMono.raiseError(new RuntimeException("ex"))).retryWhen { _ =>
        if (counter.getAndIncrement() > 0) SMono.raiseError[Int](new RuntimeException("another ex"))
        else SMono.just(1)
      }
      StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }
*/

    ".retryWhen should retry according to the spec" in {
      val counter = new AtomicInteger(0)
      val sFlux = SFlux.just(1, 2, 3).concatWith(SMono.raiseError(new RuntimeException("ex")))
        .retryWhen(SRetry.from(_ => {
          if (counter.getAndIncrement() > 0) SMono.raiseError[Int](new RuntimeException("another ex"))
          else SMono.just(1)
        }))
      StepVerifier.create(sFlux)
        .expectNext(1, 2, 3)
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".sample should emit the last value for given interval" in {
      StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5, 6).delayElements(1 second).sample(1500 milliseconds))
        .thenAwait(6 seconds)
        .expectNext(1, 2, 4, 5, 6)
        .verifyComplete()
    }

    ".sampleFirst should emit the first value during the timespan" in {
      StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5).delayElements(1 second).sampleFirst(1500 milliseconds))
        .thenAwait(6 seconds)
        .expectNext(1, 3, 5)
        .verifyComplete()
    }

    ".scan" - {
      "should scan the values of this flux" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4).scan { (a, b) => a * b })
          .expectNext(1, 2, 6, 24)
          .verifyComplete()
      }
      "with initial value should scan with provided initial value" in {
        val flux = SFlux.just(1, 2, 3, 4).scan(2){ (a: Int, b: Int) => a * b }
        StepVerifier.create(flux)
          .expectNext(2, 2, 4, 12, 48)
          .verifyComplete()
      }

      "with initial value should be able to call scan without the generic type" in {
        val flux = SFlux.just("item1", "item2").scan("prefix"){(a, b) => a+b}
        StepVerifier.create(flux)
          .expectNext("prefix", "prefixitem1", "prefixitem1item2")
          .verifyComplete()
      }
    }

/*
    ".scanWith should scan with initial value" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4).scanWith(() => 2, { (a, b) => a * b }))
        .expectNext(2, 2, 4, 12, 48)
        .verifyComplete()
    }
*/

    ".single" - {
      "should return a mono" in {
        StepVerifier.create(SFlux.just(1).single())
          .expectNext(1)
          .verifyComplete()
      }
      "or emit onError with IndexOutOfBoundsException" in {
        StepVerifier.create(SFlux.just(1, 2, 3).single())
          .expectError(classOf[IndexOutOfBoundsException])
          .verify()
      }
      "with default value should return the default value if the flux is empty" in {
        StepVerifier.create(SFlux.empty.single(Option(2)))
          .expectNext(2)
          .verifyComplete()
      }
    }

    ".singleOrEmpty should return mono with single value or empty" in {
      StepVerifier.create(SFlux.just(3).singleOrEmpty())
        .expectNext(3)
        .verifyComplete()
    }

    ".skip" - {
      "with the number to skip should skip some elements" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).skip(2))
          .expectNext(3, 4, 5)
          .verifyComplete()
      }
      "with duration should skip all elements within that duration" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5).delayElements(1 second).skip(2 seconds))
          .thenAwait(6 seconds)
          .expectNext(2, 3, 4, 5)
          .verifyComplete()
      }
      "with timer should skip all elements within the millis duration with the provided timer" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5).delayElements(1 second).skip(2 seconds, Schedulers.single()))
          .thenAwait(6 seconds)
          .expectNext(2, 3, 4, 5)
          .verifyComplete()
      }
    }

    ".skipLast should skip the last n elements" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).skipLast(2))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".skipUntil should skip until predicate matches" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).skipUntil(t => t == 3))
        .expectNext(3, 4, 5)
        .verifyComplete()
    }

    ".skipWhile should skip while the predicate returns true" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).skipWhile(t => t <= 3))
        .expectNext(4, 5)
        .verifyComplete()
    }

    ".sort" - {
      "should sort the elements" in {
        StepVerifier.create(SFlux.just(3, 4, 2, 5, 1, 6).sort())
          .expectNext(1, 2, 3, 4, 5, 6)
          .verifyComplete()
      }
      "with sort function should sort the elements based on the function" in {
        StepVerifier.create(SFlux.just(3, 4, 2, 5, 1, 6).sort(new IntOrdering() {
          override def compare(x: Int, y: Int): Int = super.compare(x, y)
        }.reverse))
          .expectNext(6, 5, 4, 3, 2, 1)
          .verifyComplete()
      }
    }

    ".startWith" - {
      "with iterable should prepend the flux with the provided iterable elements" in {
        StepVerifier.create(SFlux.just(1, 2, 3).startWith(Iterable(10, 20, 30)))
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
      "with varargs should prepend the flux with the provided values" in {
        StepVerifier.create(SFlux.just(1, 2, 3).startWith(10, 20, 30))
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
      "with publisher should prepend the flux with the provided publisher" in {
        StepVerifier.create(SFlux.just(1, 2, 3).startWith(SFlux.just(10, 20, 30)))
          .expectNext(10, 20, 30, 1, 2, 3)
          .verifyComplete()
      }
    }

    ".sum should sum up all values at onComplete it emits the total, given the source that emit numeric values" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).sum)
        .expectNext(15)
        .verifyComplete()
    }

    ".switchIfEmpty should switch if the current flux is empty" in {
      StepVerifier.create(SFlux.empty.switchIfEmpty(SFlux.just(10, 20, 30)))
        .expectNext(10, 20, 30)
        .verifyComplete()
    }

    ".switchMap" - {
      "with function should switch to the new publisher" in {
        StepVerifier.create(SFlux.just(1, 2, 3).switchMap(i => SFlux.just(i * 10, i * 20)))
          .expectNext(10, 20, 20, 40, 30, 60)
          .verifyComplete()
      }
      "with function and prefetch should switch to the new publisher" in {
        StepVerifier.create(SFlux.just(1, 2, 3).switchMap(i => SFlux.just(i * 10, i * 20), 2))
          .expectNext(10, 20, 20, 40, 30, 60)
          .verifyComplete()
      }
    }

    ".switchOnNext should switch on the next publisher" in {
      val sFlux = SFlux.switchOnNext(SFlux.just(SFlux.just("A", "B", "C"), SFlux.just("a", "b", "c"), SMono.just("1")))
      StepVerifier.create(sFlux)
        .expectNext("A", "B", "C", "a", "b", "c", "1")
        .verifyComplete()
    }

    ".tag should tag the Flux and accessible from Scannable" in {
      val flux = SFlux.just(1, 2, 3).tag("integer", "one, two, three")
      Scannable.from(Option(flux)).tags shouldBe Stream("integer" -> "one, two, three")
    }

    ".tail should return flux that exclude the head" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).tail)
        .expectNext(2, 3, 4, 5)
        .verifyComplete()
    }

    ".take" - {
      "should emit only n values" in {
        StepVerifier.create(SFlux(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).take(3))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with duration should only emit values during the provided duration" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4, 5).delayElements(1 seconds).take(3500 milliseconds))
          .thenAwait(5 seconds)
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
      "with timespan and timed scheduler should only emit values during the provided timespan with the provided TimedScheduler" in {
        val vts = VirtualTimeScheduler.getOrSet()
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5)
          .delayElements(1 second, vts)
          .take(3500 milliseconds, vts), 256)
          .`then`(() => vts.advanceTimeBy(5 seconds))
          .expectNext(1, 2, 3)
          .verifyComplete()
      }
    }

    ".takeLast should take the last n values" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).takeLast(3))
        .expectNext(3, 4, 5)
        .verifyComplete()
    }

    ".takeUntil should emit the values until the predicate returns true" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).takeUntil(t => t >= 4))
        .expectNext(1, 2, 3, 4)
        .verifyComplete()
    }

    ".takeWhile should emit values until the predicate returns false" in {
      StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).takeWhile(t => t < 4))
        .expectNext(1, 2, 3)
        .verifyComplete()
    }

    ".then" - {
      "without parameter should actively ignore the values" in {
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5).`then`())
          .verifyComplete()
      }
    }

    ".thenEmpty should wait for this to complete and then for the supplied publisher to complete" in {
      StepVerifier.create(SFlux.just(1, 2, 3).thenEmpty(SMono.empty))
        .verifyComplete()
    }

    ".thenMany" - {
      "should emit the sequence of the supplied publisher" in {
        StepVerifier.create(SFlux.just(1, 2, 3).thenMany(SFlux.just("1", "2", "3")))
          .expectNext("1", "2", "3")
          .verifyComplete()
      }
    }

    ".timeout" - {
      "with timeout duration should throw exception if the item is not emitted within the provided duration after previous emited item" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(2 seconds).timeout(1 second))
          .thenAwait(2 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with timeout and optional fallback should fallback if the item is not emitted within the provided duration" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(2 seconds).timeout(1 second, Option(SFlux.just(10, 20, 30))))
          .thenAwait(2 seconds)
          .expectNext(10, 20, 30)
          .verifyComplete()
      }
      "with firstTimeout should throw exception if the first item is not emitted before the given publisher emits" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(2 seconds).timeout(SMono.just(1)))
          .thenAwait(2 seconds)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with firstTimeout and next timeout factory should throw exception if any of the item from this flux does not emit before the timeout provided" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(2 seconds).timeout(SMono.just(1).delaySubscription(3 seconds), t => SMono.just(1).delaySubscription(t seconds)))
          .thenAwait(5 seconds)
          .expectNext(1)
          .expectError(classOf[TimeoutException])
          .verify()
      }
      "with firstTimeout, nextTimeoutFactory and fallback should fallback if any of the item is not emitted within the timeout period" in {
        StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(2 seconds).timeout(SMono.just(1).delaySubscription(3 seconds), t => SMono.just(1).delaySubscription(t seconds), SFlux.just(10, 20, 30)))
          .thenAwait(5 seconds)
          .expectNext(1, 10, 20, 30)
          .verifyComplete()
      }
    }

    ".toIterable" - {
      "should transform this flux into iterable" in {
        SFlux.just(1, 2, 3).toIterable().toList shouldBe Iterable(1, 2, 3)
      }
      "with batchSize should transform this flux into iterable" in {
        SFlux.just(1, 2, 3).toIterable(1).toList shouldBe Iterable(1, 2, 3)
      }
      "with batchSize and queue supplier should transform this flux into iterable" in {
        SFlux.just(1, 2, 3).toIterable(1, Option(Queues.get(1))).toList shouldBe Iterable(1, 2, 3)
      }
    }

    ".toStream" - {
      "should transform this flux into stream" in {
        SFlux.just(1, 2, 3).toStream() shouldBe Stream(1, 2, 3)
      }
      "with batchSize should transform this flux into stream" in {
        SFlux.just(1, 2, 3).toStream(2) shouldBe Stream(1, 2, 3)
      }
    }

    ".transform should defer transformation of this flux to another publisher" in {
      StepVerifier.create(SFlux.just(1, 2, 3).transform(SMono.fromPublisher))
        .expectNext(1)
        .verifyComplete()
    }

    ".withLatestFrom should combine with the latest of the other publisher" in {
      StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3, 4).delayElements(1 second).withLatestFrom(SFlux.just("one", "two", "three").delayElements(1500 milliseconds), (i: Int, s: String) => (i, s)))
        .thenAwait(5 seconds)
        .expectNext((2, "one"), (3, "two"), (4, "two"))
        .verifyComplete()
    }

    ".zipWith" - {
      "should zip both publishers" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWith(SFlux.just(10, 20, 30)))
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
      "with combinator should zip and apply the combinator" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWithCombinator(SFlux.just(10, 20, 30))((i1: Int, i2: Int) => i1 + i2))
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
      "with combinator and prefetch should zip and apply the combinator" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWithCombinator(SFlux.just(10, 20, 30), 1)((i1: Int, i2: Int) => i1 + i2))
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
      "with prefetch should zip both publishers" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWith(SFlux.just(10, 20, 30), 1))
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
    }

    ".zipWithIterable" - {
      "should zip with the provided iterable" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWithIterable(Iterable(10, 20, 30)))
          .expectNext((1, 10), (2, 20), (3, 30))
          .verifyComplete()
      }
      "with zipper should zip and apply the zipper" in {
        StepVerifier.create(SFlux.just(1, 2, 3).zipWithIterable[Int, Int](Iterable(10, 20, 30), (i1: Int, i2: Int) => i1 + i2))
          .expectNext(11, 22, 33)
          .verifyComplete()
      }
    }

    ".zipWithTimeSinceSubscribe should emit tuple2 with the second element as the time taken to emit since subscription in milliseconds" in {
      StepVerifier.withVirtualTime(() => SFlux.just(1, 2, 3).delayElements(1 second).zipWithTimeSinceSubscribe())
        .thenAwait(3 seconds)
        .expectNext((1, 1000L), (2, 2000L), (3, 3000L))
        .verifyComplete()
    }

    ".asJava should convert to java" in {
      SFlux.just(1, 2, 3).asJava() shouldBe a[reactor.core.publisher.Flux[_]]
    }
  }
}
