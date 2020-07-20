package reactor.core.scala.publisher

import java.util.stream.{StreamSupport, Stream => JStream}
import java.util.{Spliterator, Spliterators}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

package object versioned {
  implicit def scalaStream2JavaStream[T](stream: Stream[T]): JStream[T] = StreamSupport.stream(Spliterators.spliteratorUnknownSize[T](stream.toIterator.asJava, Spliterator.NONNULL), false)

}
