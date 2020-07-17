package reactor.core.scala.publisher

import java.util.stream.{Stream => JStream}

import scala.language.implicitConversions
import scala.jdk.StreamConverters._

package object versioned {
  implicit def scalaStream2JavaStream[T](stream: LazyList[T]): JStream[T] = stream.asJavaSeqStream
}
