package reactor.core.scala.publisher

import java.util
import java.util.{Collection => JCollection, Map => JMap}

import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE

import scala.collection.mutable
import scala.jdk.CollectionConverters._

trait VersionedSFlux[+T] {self: SFlux[T] =>
  final def collectMultimap[K](keyExtractor: T => K): SMono[Map[K, Iterable[T]]] = collectMultimap(keyExtractor, (t: T) => t, ()=>mutable.HashMap.empty[K, util.Collection[T]])

  final def collectMultimap[K, V](keyExtractor: T => K,
                                  valueExtractor: T => V,
                                  mapSupplier: () => mutable.Map[K, util.Collection[V]] = () => mutable.HashMap.empty[K, util.Collection[V]]):
  SMono[Map[K, Iterable[V]]] =
    new ReactiveSMono[Map[K, Iterable[V]]](coreFlux.collectMultimap[K, V](keyExtractor,
      valueExtractor,
      () => mapSupplier().asJava)
      .map((m: JMap[K, JCollection[V]]) => m.asScala.view.mapValues((vs: JCollection[V]) => vs.asScala.toSeq).toMap))

  @deprecated("Use toLazyList", since = "0.8.0, 2.13.0")
  final def toStream(batchSize: Int = SMALL_BUFFER_SIZE): LazyList[T] = toLazyList(batchSize)

  final def toLazyList(batchSize: Int = SMALL_BUFFER_SIZE): LazyList[T] = coreFlux.toStream(batchSize).iterator().asScala.to(LazyList)

}
