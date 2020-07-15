package reactor.util.scala.context

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SMono
import reactor.core.scala.publisher.model.TestSupport
import reactor.test.StepVerifier
import SContext._

class SContextTest extends AnyFreeSpec with Matchers with TestSupport {
  "SContext" - {
    ".getOrNone " - {
      "with missing key should return None" in {
        StepVerifier.create(
          SMono.subscriberContext
               .map{ _.getOrNone("BOGUS") }
        ).expectNext(None)
         .verifyComplete()
      }
      "with value of incompatible type should return None" in {
        StepVerifier.create(
          SMono.subscriberContext
               .map{ _.getOrNone[Sedan]("whatever key") }
               .subscriberContext{ _.put("whatever key", Truck(76254))}
        ).expectNext(None)
         .verifyComplete()
      }
      "with value of exactly the expected type should return Some value" in {
        StepVerifier.create(
          SMono.subscriberContext
               .map{ _.getOrNone[Truck]("whatever key") }
               .subscriberContext{ _.put("whatever key", Truck(76254))}
        ).expectNext(Some(Truck(76254)))
         .verifyComplete()
      }
      "with value of a supertype of the expected type should return Some value" in {
        StepVerifier.create(
          SMono.subscriberContext
               .map{ _.getOrNone[Vehicle](true) }
               .subscriberContext{ _.put(true, Truck(76254))}
        ).expectNext(Some(Truck(76254)))
         .verifyComplete()
      }
    }
  }
}
