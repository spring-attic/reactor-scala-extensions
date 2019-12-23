package reactor.core.scala.scheduler

import java.util.concurrent.{Executors, ThreadFactory}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SMono
import reactor.test.StepVerifier

import scala.concurrent.ExecutionContext

/**
  * Created by winarto on 1/26/17.
  */
class ExecutionContextSchedulerTest extends AnyFreeSpec with Matchers {
  "ExecutionContextScheduler" - {
    "should create a Scheduler using provided ExecutionContext" - {
      "on Mono" in {
        val executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1, new ThreadFactory {
          override def newThread(r: Runnable): Thread = new Thread(r, "THREAD-NAME-MONO")
        }))
        val mono = SMono.just(1)
          .subscribeOn(ExecutionContextScheduler(executionContext))
          .doOnNext(i => Thread.currentThread().getName shouldBe "THREAD-NAME-MONO")
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
      }
      "on SMono" in {
        val executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1, new ThreadFactory {
          override def newThread(r: Runnable): Thread = new Thread(r, "THREAD-NAME-SMONO")
        }))
        val mono = SMono.just(1)
          .subscribeOn(ExecutionContextScheduler(executionContext))
          .doOnNext(i => Thread.currentThread().getName shouldBe "THREAD-NAME-SMONO")
        StepVerifier.create(mono)
          .expectNext(1)
          .verifyComplete()
      }
    }
  }
}
