package reactor.core.scala

import scala.jdk.CollectionConverters._

trait VersionedScannable { self: Scannable =>
  def actuals(): Stream[_ <: Scannable] = jScannable.actuals().iterator().asScala.map(js => js: Scannable).toStream

  def inners(): Stream[_ <: Scannable] = jScannable.inners().iterator().asScala.map(js => js: Scannable).toStream

  /**
    * Return a [[Stream]] navigating the [[org.reactivestreams.Subscription]]
    * chain (upward).
    *
    * @return a [[Stream]] navigating the [[org.reactivestreams.Subscription]]
    *                   chain (upward)
    */
  def parents: Stream[_ <: Scannable] = jScannable.parents().iterator().asScala.map(js => js: Scannable).toStream

  /**
    * Visit this [[Scannable]] and its [[Scannable.parents()]] and stream all the
    * observed tags
    *
    * @return the stream of tags for this [[Scannable]] and its parents
    */
  def tags: Stream[(String, String)] = jScannable.tags().iterator().asScala.map(publisher.tupleTwo2ScalaTuple2).toStream

}
