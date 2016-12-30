package reactor.core.scala.publisher

import java.time
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{Callable, TimeUnit, TimeoutException}
import java.util.function.Supplier

import org.reactivestreams.Publisher
import org.scalatest.{FreeSpec, Matchers}
import reactor.core.publisher.{Flux, Mono => JMono}
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

import scala.concurrent.Future
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

    ".empty " - {
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

    ".from" - {
      "a publisher should ensure that the publisher will emit 0 or 1 item." in {
        val publisher: Flux[Int] = Flux.just(1, 2, 3, 4, 5)

        val mono = Mono.from(publisher)

        StepVerifier.create(mono)
          .expectNext(1)
          .expectComplete()
          .verify()
      }

      "a callable should ensure that Mono will return a value from the Callable" in {
        val callable = new Callable[Long] {
          override def call(): Long = randomValue
        }
        val mono = Mono.fromCallable(callable)
        StepVerifier.create(mono)
          .expectNext(randomValue)
          .expectComplete()
          .verify()
      }

      "a future should result Mono that will return the value from the future object" in {
        import scala.concurrent.ExecutionContext.Implicits.global
        val future = Future[Long] {randomValue}

        val mono = Mono.fromFuture(future)
        StepVerifier.create(mono)
          .expectNext(randomValue)
          .expectComplete()
          .verify()
      }
    }

    ".map should map the type of Mono from T to R" in {
      val mono = createMono.map(_.toString)

      StepVerifier.create(mono)
        .expectNext(randomValue.toString)
        .expectComplete()
        .verify()
    }
    ".timeout should raise TimeoutException after duration elapse" in {
      StepVerifier.withVirtualTime(new Supplier[Publisher[Long]] {
        override def get(): Mono[Long] = Mono.delayMillis(10000).timeout(Duration(5, TimeUnit.SECONDS))
      })
        .thenAwait(time.Duration.ofSeconds(5))
        .expectError(classOf[TimeoutException])
        .verify()
    }

    ".doOnTerminate should do something on terminate" in {
      val atomicLong = new AtomicLong()
      val mono: Mono[Long] = createMono.doOnTerminate {(l, t) => atomicLong.set(l)}
      StepVerifier.create(mono)
        .expectNext(randomValue)
        .expectComplete()
        .verify()
      atomicLong.get() shouldBe randomValue
    }
  }

  private def createMono = {
    Mono.create[Long](monoSink => monoSink.success(randomValue))
  }
}
