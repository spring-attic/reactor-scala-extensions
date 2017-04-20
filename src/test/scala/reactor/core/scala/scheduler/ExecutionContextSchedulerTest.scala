package reactor.core.scala.scheduler

import java.util.concurrent.{Executors, ThreadFactory}

import org.scalatest.{FreeSpec, Matchers}
import reactor.core.scala.publisher.Mono
import reactor.test.StepVerifier

import scala.concurrent.ExecutionContext

/**
  * Created by winarto on 1/26/17.
  */
class ExecutionContextSchedulerTest extends FreeSpec with Matchers {
  "ExecutionContextScheduler" - {
    "should create a Scheduler using provided ExecutionContext" in {
      val executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1, new ThreadFactory {
        override def newThread(r: Runnable): Thread = new Thread(r, "THREAD-NAME")
      }))
      val mono = Mono.just(1)
        .subscribeOn(ExecutionContextScheduler(executionContext))
        .doOnNext(i => Thread.currentThread().getName shouldBe "THREAD-NAME")
      StepVerifier.create(mono)
        .expectNext(1)
        .verifyComplete()
    }
  }
}
