package reactor.core.scala

import scala.jdk.CollectionConverters._

trait VersionedScannable { self: Scannable =>
  def actuals(): LazyList[_ <: Scannable] = jScannable.actuals().iterator().asScala.map(js => js: Scannable).to(LazyList)

  def inners(): LazyList[_ <: Scannable] = jScannable.inners().iterator().asScala.map(js => js: Scannable).to(LazyList)

  /**
    * Return a [[Stream]] navigating the [[org.reactivestreams.Subscription]]
    * chain (upward).
    *
    * @return a [[Stream]] navigating the [[org.reactivestreams.Subscription]]
    *                   chain (upward)
    */
  def parents: LazyList[_ <: Scannable] = jScannable.parents().iterator().asScala.map(js => js: Scannable).to(LazyList)

  /**
    * Visit this [[Scannable]] and its [[Scannable.parents()]] and stream all the
    * observed tags
    *
    * @return the stream of tags for this [[Scannable]] and its parents
    */
  def tags: LazyList[(String, String)] = jScannable.tags().iterator().asScala.map(publisher.tupleTwo2ScalaTuple2).to(LazyList)

}
