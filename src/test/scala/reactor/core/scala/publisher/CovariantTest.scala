package reactor.core.scala.publisher

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.model.{ComponentExt, ContainerExt}

class CovariantTest extends AnyFreeSpec with Matchers {
  "SFlux and SMono" - {
    "covariance should be proven with the following compiled" in {
      new ContainerExt {
        override def component: ComponentExt = ???

        override def components: List[ComponentExt] = ???

        override def componentsFlux: SFlux[ComponentExt] = ???

        override def componentMono: SMono[ComponentExt] = ???
      } shouldBe a[ContainerExt]
    }
  }
}