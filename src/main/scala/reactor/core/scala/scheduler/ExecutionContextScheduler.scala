package reactor.core.scala.scheduler

import java.util.concurrent.Executor

import reactor.core.Disposable
import reactor.core.scheduler.Scheduler.Worker
import reactor.core.scheduler.{Scheduler, Schedulers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, ExecutionContextExecutorService}

class ExecutionContextScheduler private(val scheduler: Scheduler) extends Scheduler {
  override def schedule(task: Runnable): Disposable = scheduler.schedule(task)

  override def createWorker(): Worker = scheduler.createWorker()
}

/**
  * Provide an easy way to create [[Scheduler]] using the provided [[ExecutionContext]] so it can be used like:
  * <p>
  * `val mono = Mono.just(1)
  *             .subscribeOn(ExecutionContextScheduler(executionContext))`
  */
object ExecutionContextScheduler {
  def apply(executionContext: ExecutionContext): ExecutionContextScheduler = {
    executionContext match {
      case eces: ExecutionContextExecutorService => new ExecutionContextScheduler(Schedulers.fromExecutorService(eces))
      case ece: ExecutionContextExecutor => new ExecutionContextScheduler(Schedulers.fromExecutor(ece))
      case _ => new ExecutionContextScheduler(Schedulers.fromExecutor(new Executor {
        override def execute(command: Runnable): Unit = executionContext.execute(command)
      }))
    }
  }
}
