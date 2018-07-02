package reactor.core.scala.publisher

import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{Mono => JMono}
import reactor.core.scala.Scannable
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

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

    ".raiseError should create Mono that emit error" in {
      StepVerifier.create(SMono.raiseError(new RuntimeException("runtime error")))
        .expectError(classOf[RuntimeException])
        .verify()
    }

    ".asJava should convert to java" in {
      SMono.just(randomValue).asJava() shouldBe a[JMono[_]]
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

    ".map should map the type of Mono from T to R" in {
      StepVerifier.create(SMono.just(randomValue).map(_.toString))
        .expectNext(randomValue.toString)
        .expectComplete()
        .verify()
    }
  }
}
