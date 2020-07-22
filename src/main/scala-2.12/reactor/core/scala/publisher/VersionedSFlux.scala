package reactor.core.scala.publisher

import java.util
import java.util.{Collection => JCollection, Map => JMap}

import reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE

import scala.collection.mutable
import scala.collection.JavaConverters._

trait VersionedSFlux[+T] {self: SFlux[T] =>
  final def collectMultimap[K](keyExtractor: T => K): SMono[Map[K, Traversable[T]]] = collectMultimap(keyExtractor, (t: T) => t, ()=>mutable.HashMap.empty[K, util.Collection[T]])

  final def collectMultimap[K, V](keyExtractor: T => K,
                                  valueExtractor: T => V,
                                  mapSupplier: () => mutable.Map[K, util.Collection[V]] = () => mutable.HashMap.empty[K, util.Collection[V]]):
  SMono[Map[K, Traversable[V]]] =
    new ReactiveSMono[Map[K, Traversable[V]]](coreFlux.collectMultimap[K, V](keyExtractor,
      valueExtractor,
      () => mapSupplier().asJava)
      .map((m: JMap[K, JCollection[V]]) => m.asScala.mapValues((vs: JCollection[V]) => vs.asScala.toSeq).toMap))

  final def toStream(batchSize: Int = SMALL_BUFFER_SIZE): Stream[T] = coreFlux.toStream(batchSize).iterator().asScala.toStream

}
