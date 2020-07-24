package reactor.core.scala.publisher

trait ExitCondition

case object Completed extends ExitCondition

case object Cancelled extends ExitCondition

case class Error(ex: Throwable) extends ExitCondition
