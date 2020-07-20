package reactor.core.scala.publisher

import reactor.core.scala.{Scannable, VersionedScannable}

import scala.jdk.CollectionConverters._

trait VersionedFluxProcessor[IN, OUT] extends VersionedScannable { self: FluxProcessor[IN, OUT] =>
  override def inners(): Stream[_ <: Scannable] = jFluxProcessor.inners().iterator().asScala.map(js=> js: Scannable).toStream
}