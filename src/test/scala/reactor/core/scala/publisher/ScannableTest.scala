package reactor.core.scala.publisher

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.Scannable

class ScannableTest extends AnyFreeSpec with Matchers {
  "Scannable" - {
    ".stepName should return a meaningful String representation of this Scannable in its chain of Scannable.parents and Scannable.actuals" in {
      val scannable: Scannable = Scannable.from(Option(SMono.just(123).onTerminateDetach()))
      scannable.stepName shouldBe "detach"
    }
  }
}
