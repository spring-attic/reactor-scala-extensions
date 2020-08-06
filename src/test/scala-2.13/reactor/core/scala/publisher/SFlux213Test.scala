package reactor.core.scala.publisher

import java.io.{File, PrintWriter}
import java.nio.file.Files
import java.util

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.Scannable
import reactor.test.StepVerifier

import scala.collection.mutable
import scala.io.Source

class SFlux213Test extends AnyFreeSpec with Matchers {
  "SFlux" - {
    ".fromLazyList" - {
        "should create flux that emit items contained in the supplier" in {
          StepVerifier.create(SFlux.fromLazyList(() => LazyList(1, 2, 3)))
            .expectNext(1, 2, 3)
            .verifyComplete()
        }
    }

    ".using" - {
      "without eager flag should produce some data" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp")
        tempFile.toFile.deleteOnExit()
        new PrintWriter(tempFile.toFile) {
          write(s"1${sys.props("line.separator")}2")
          flush()
          close()
        }

        StepVerifier.create(
          SFlux.using[String, File](() => tempFile.toFile, (file: File) => SFlux.fromIterable[String](Source.fromFile(file).getLines().iterator.to(Iterable)), (file: File) => {
            file.delete()
            ()
          }))
          .expectNext("1", "2")
          .verifyComplete()
      }
      "with eager flag should produce some data" in {
        val tempFile = Files.createTempFile("fluxtest-", ".tmp")
        tempFile.toFile.deleteOnExit()
        new PrintWriter(tempFile.toFile) {
          write(s"1${sys.props("line.separator")}2")
          flush()
          close()
        }
        StepVerifier.create(
          SFlux.using[String, File](() => tempFile.toFile, (file: File) => SFlux.fromIterable[String](Source.fromFile(file).getLines().iterator.to(Iterable)), (file: File) => {
            file.delete()
            ()
          }, eager = true))
          .expectNext("1", "2")
          .verifyComplete()
      }
    }

    ".usingWhen" - {
      "should release all resources properly" in {
        import java.io.PrintWriter
        val files = (0 until 5) map(i => {
          val path = Files.createTempFile(s"bracketCase-$i", ".tmp")
          val file = path.toFile
          new PrintWriter(file) { write(s"$i"); close() }
          file
        })
        files.foreach(f => f.exists() shouldBe true)
        val sf = SFlux.fromIterable(files)
          .usingWhen(f => {
            SFlux.just(Source.fromFile(f))
              .using(br => SFlux.fromIterable(br.getLines().iterator.to(Iterable)))(_.close())
          })((file, _) => file.delete())
        StepVerifier.create(sf)
          .expectNext("0", "1", "2", "3", "4")
          .verifyComplete()
        files.foreach(f => f.exists() shouldBe false)
      }
    }

    ".collectMultimap" - {
      "with keyExtractor, valueExtractor and map supplier should collect the value, extract the key and value from it and put in the provided map" in {
        val map = mutable.HashMap[Int, util.Collection[Int]]()
        StepVerifier.create(SFlux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collectMultimap(i => i % 3, i => i + 6, () => map))
          .expectNextMatches((m: Map[Int, Iterable[Int]]) => {
            m shouldBe map.view.mapValues(vs => vs.toArray().toSeq).toMap
            m shouldBe Map((0, Seq(9, 12, 15)), (1, Seq(7, 10, 13, 16)), (2, Seq(8, 11, 14)))
            true
          })
          .verifyComplete()
      }
    }

    ".tag should tag the Flux and accessible from Scannable" in {
      val flux = SFlux.just(1, 2, 3).tag("integer", "one, two, three")
      Scannable.from(Option(flux)).tags shouldBe LazyList("integer" -> "one, two, three")
    }
    
    ".toLazyList" - {
      "should transform this flux into stream" in {
        SFlux.just(1, 2, 3).toLazyList() shouldBe LazyList(1, 2, 3)
      }
      "with batchSize should transform this flux into stream" in {
        SFlux.just(1, 2, 3).toLazyList(2) shouldBe LazyList(1, 2, 3)
      }
    }
  }
}
