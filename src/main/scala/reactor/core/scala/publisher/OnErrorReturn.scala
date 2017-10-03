package reactor.core.scala.publisher

trait OnErrorReturn[T] {
  def onErrorReturn(fallbackValue: T): OnErrorReturn[T]

  def onErrorReturn[E <: Throwable](`type`: Class[E], fallbackValue: T): OnErrorReturn[T]

  def onErrorReturn(predicate: Throwable => Boolean, fallbackValue: T): OnErrorReturn[T]
}
