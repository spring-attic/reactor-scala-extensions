package reactor.core.scala

import scala.jdk.StreamConverters._

trait VersionedScannable { self: Scannable =>
  def actuals(): LazyList[_ <: Scannable] = jScannable.actuals().toScala(LazyList).map(js => js: Scannable)

  def inners(): LazyList[_ <: Scannable] = jScannable.inners().toScala(LazyList).map(js => js: Scannable)

  /**
    * Return a [[LazyList]] navigating the [[org.reactivestreams.Subscription]]
    * chain (upward).
    *
    * @return a [[LazyList]] navigating the [[org.reactivestreams.Subscription]]
    *                   chain (upward)
    */
  def parents: LazyList[_ <: Scannable] = jScannable.parents().toScala(LazyList).map(js => js: Scannable)

  /**
    * Visit this [[Scannable]] and its [[Scannable.parents()]] and stream all the
    * observed tags
    *
    * @return the stream of tags for this [[Scannable]] and its parents
    */
  def tags: LazyList[(String, String)] = jScannable.tags().toScala(LazyList).map(publisher.tupleTwo2ScalaTuple2)

}
