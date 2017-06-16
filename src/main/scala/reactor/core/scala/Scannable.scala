package reactor.core.scala

import reactor.core.Scannable.Attr
import reactor.core.{Scannable => JScannable}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Created by winarto on 17/6/17.
  */
trait Scannable {
  def jScannable: JScannable

  def actuals(): Stream[_ <: Scannable] = jScannable.actuals().iterator().asScala.map(js => js: Scannable).toStream

  def inners(): Stream[_ <: Scannable] = jScannable.inners().iterator().asScala.map(js => js: Scannable).toStream

  def isScanAvailable: Boolean = jScannable.isScanAvailable

  def scan(key: Attr): AnyRef = jScannable.scan(key)

  def scan[T](key: Attr, `type`: Class[T]): T = jScannable.scan(key, `type`)

  def scanOrDefault[T](key: Attr, defaultValue: T): T = jScannable.scanOrDefault(key, defaultValue)

}

object Scannable {
  def from(any: AnyRef): Scannable = new Scannable {
    override def jScannable: JScannable = JScannable.from(any)
  }

  implicit def JScannable2Scannable(js: JScannable): Scannable = new Scannable {
    override def jScannable: JScannable = js
  }
}