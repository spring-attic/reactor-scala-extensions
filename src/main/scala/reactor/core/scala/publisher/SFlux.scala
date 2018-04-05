package reactor.core.scala.publisher

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux => JFlux}

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.reflect.ClassTag

trait SFlux[T] extends Publisher[T] {
  private[publisher] def coreFlux: JFlux[T]

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreFlux.subscribe(s)
}

object SFlux {
  def apply[T](elements: T*): SFlux[T] = SFlux.fromIterable(elements)

  def combineLatest[T1, T2](p1: Publisher[T1], p2: Publisher[T2]): SFlux[(T1, T2)] =
    new ReactiveSFlux[(T1, T2)](JFlux.combineLatest(p1, p2, (t1: T1, t2: T2) => (t1, t2)))

  def combineLatest[T](sources: Publisher[T]*): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](JFlux.combineLatest[T, Seq[T]]
      (sources, (arr: Array[AnyRef]) => arr.toSeq map {case t: T => t }))

  def combineLatestMap[T1, T2, V](p1: Publisher[T1], p2: Publisher[T2], mapper: (T1, T2) => V): SFlux[V] =
    new ReactiveSFlux[V](JFlux.combineLatest(p1, p2, mapper))

  def combineLatestMap[T: ClassTag, V](mapper: Array[T] => V, sources: Publisher[T]*): SFlux[V] = {
    val f = (arr: Array[AnyRef]) => {
      val x: Seq[T] = arr.toSeq collect { case t: T => t }
      mapper(x.toArray)
    }
    new ReactiveSFlux[V](JFlux.combineLatest(sources, f))
  }

  def empty[T]: SFlux[T] = new ReactiveSFlux(JFlux.empty[T]())

  def fromIterable[T](iterable: Iterable[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromIterable(iterable.asJava))
}

private[publisher] class ReactiveSFlux[T](publisher: Publisher[T]) extends SFlux[T] {
  override private[publisher] def coreFlux = JFlux.from(publisher)
}
