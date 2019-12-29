package reactor.core.scala.publisher

import org.scalatest.freespec.AsyncFreeSpec
import reactor.core.scala.publisher.SMono.just

import scala.concurrent.Future

class SMonoAsyncTest extends AsyncFreeSpec {
  "SMono" - {
    ".toFuture should convert this mono to future" in {
      val future: Future[Int] = just(1).toFuture
      future map { v => {
        assert(v == 1)
      }
      }
    }
    ".toFuture should convert this mono to future with void return" in {
      val future: Future[Int] = SMono.empty[Unit].toFuture.map(_ => 1)
      future map { v => {
        assert(v == 1)
      }
      }
    }
  }
}
