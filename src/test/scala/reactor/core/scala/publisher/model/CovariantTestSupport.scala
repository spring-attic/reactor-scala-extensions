package reactor.core.scala.publisher.model

import reactor.core.scala.publisher.{SFlux, SMono}

trait Component

trait Container{
  def component: Component

  def components: List[Component]

  def componentsFlux: SFlux[Component]

  def componentMono: SMono[Component]
}

trait ComponentExt extends Component

trait ContainerExt extends Container{
  def component: ComponentExt

  def components: List[ComponentExt]

  def componentsFlux: SFlux[ComponentExt]

  override def componentMono: SMono[ComponentExt]
}