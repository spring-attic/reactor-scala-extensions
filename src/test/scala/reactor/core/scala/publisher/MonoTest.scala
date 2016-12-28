package reactor.core.scala.publisher

import java.time
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.function.{Consumer, Function, Supplier}

import org.reactivestreams.{Publisher, Subscription}
import reactor.core.publisher.{BaseSubscriber, DirectProcessor, MonoProcessor, MonoSink, Mono => JMono}
import org.scalatest.{FreeSpec, Matchers}
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.concurrent.duration.Duration
import scala.util.Random

/**
  * Created by winarto on 12/26/16.
  */
class MonoTest extends FreeSpec with Matchers {

  private val randomValue = Random.nextLong()
  "Mono" - {
    ".create should create a Mono" in {
      val mono = createMono

      StepVerifier.create(mono)
        .expectNext(randomValue)
        .expectComplete()
        .verify()
    }

    ".defer should create a Mono with deferred Mono" in {
      val mono = Mono.defer(() => createMono)

      StepVerifier.create(mono)
        .expectNext(randomValue)
        .expectComplete()
        .verify()
    }

    ".delay should create a Mono with the first element delayed according to the provided" - {
      "duration" in {
        StepVerifier.withVirtualTime(new Supplier[Mono[Long]] {
          override def get(): Mono[Long] = Mono.delay(Duration(5, TimeUnit.DAYS))
        })
          .thenAwait(time.Duration.ofDays(5))
          .expectNextCount(1)
          .expectComplete()
          .verify()
      }

      "duration in millis" in {
        StepVerifier.withVirtualTime(new Supplier[Mono[Long]] {
          override def get(): Mono[Long] = Mono.delayMillis(50000)
        })
          .thenAwait(time.Duration.ofSeconds(50))
          .expectNextCount(1)
          .expectComplete()
          .verify()
      }

      "duration in millis with given TimeScheduler" in {
        StepVerifier.withVirtualTime(new Supplier[Mono[Long]] {
          override def get(): Mono[Long] = Mono.delayMillis(50000, VirtualTimeScheduler.enable(false))
        })
          .thenAwait(time.Duration.ofSeconds(50))
          .expectNextCount(1)
          .expectComplete()
          .verify()

      }
    }

    ".empty "- {
      "without source should create an empty Mono" in {
        val mono = Mono.empty
        verifyEmptyMono(mono)
      }


      "with source should ignore onNext event and receive completion" in {
        val mono = Mono.empty(createMono)
        verifyEmptyMono(mono)
      }
      def verifyEmptyMono[T](mono: Mono[T]) = {
        StepVerifier.create(mono)
          .expectComplete()
          .verify()
      }
    }

    ".error should create Mono that emit error" in {
      val mono = Mono.error(new RuntimeException("runtime error"))
      StepVerifier.create(mono)
        .expectError(classOf[RuntimeException])
        .verify()
    }

    ".map should map the type of Mono from T to R" in {
      val mono = createMono.map(_.toString)

      StepVerifier.create(mono)
        .expectNext(randomValue.toString)
        .expectComplete()
        .verify()
    }
  }

  private def createMono = {
    Mono.create[Long](monoSink => monoSink.success(randomValue))
  }
}
