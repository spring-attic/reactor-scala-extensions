package reactor.core.scala.publisher.model

trait TestSupport {
  sealed trait Vehicle
  case class Sedan(id: Int) extends Vehicle
  case class Truck(id: Int) extends Vehicle
}