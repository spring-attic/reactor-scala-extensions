package reactor.core.scala.publisher

import org.scalatest.{FreeSpec, Matchers}
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class SMonoTest extends FreeSpec with Matchers {
  private val randomValue = Random.nextLong()

  "SMono" - {
    ".just should emit the specified item" in {
      StepVerifier.create(SMono.just(randomValue))
        .expectNext(randomValue)
        .verifyComplete()
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
