package reactor.core.scala.publisher

import reactor.core.publisher.{GroupedFlux => JGroupedFlux}

/**
  * Represents a sequence of events with an associated key.
  *
  * @tparam K the key type
  * @tparam V the value type
  */
class SGroupedFlux[K, V]private(private val jGroupedFlux: JGroupedFlux[K, V]) extends SFlux[V] {

  /**
    * Return defined identifier
    * @return defined identifier
    */
  def key(): K = jGroupedFlux.key()

  override private[publisher] def coreFlux: JGroupedFlux[K, V] = jGroupedFlux
}

object SGroupedFlux {
  def apply[K, V](jGroupFlux: JGroupedFlux[K, V]) = new SGroupedFlux[K, V](jGroupFlux)
}
