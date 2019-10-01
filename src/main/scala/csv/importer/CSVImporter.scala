package csv.importer
import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import csv.model.{InvalidReading, Reading, ValidReading}

import scala.concurrent.{Await, ExecutionContext}
//import csv.repository.ReadingRepository
import monix.eval.Task
import monix.execution.CancelableFuture
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import concurrent.duration._

/**
  * Created by Ilya Volynin on 11.03.2019 at 8:03.
  */
class CsvImporter(config: Config) extends LazyLogging {
  import CsvImporter._

  private val importDirectory = Paths.get(config.getString("importer.import-directory")).toFile

  private val linesToSkip = config.getInt("importer.lines-to-skip")

  private val concurrentFiles = config.getInt("importer.concurrent-files")

  private val concurrentWrites = config.getInt("importer.concurrent-writes")

  private val nonIOParallelism = config.getInt("importer.non-io-parallelism")

  def parseLine(line: String): Task[Reading] = Task {
    val fields = line.split(";")
    val id = fields(0).toInt
    try {
      val value = fields(1).toDouble
            logger.info(s" parse thread id: ${Thread.currentThread().getId}")
      ValidReading(id, value)
    } catch {
      case t: Throwable =>
        logger.error(s"Unable to parse line $line: ${t.getMessage}")
        InvalidReading(id)
    }
  }

  val parseFile: Transformer[File, Reading] = _.concatMap { file =>
    Observable.fromLinesReader(new BufferedReader(new InputStreamReader(new FileInputStream(file))))
      .drop(linesToSkip)
            .mapParallelUnordered(nonIOParallelism)(parseLine)
//      .mapEval(parseLine)
  }

  val computeAverage: Transformer[Reading, ValidReading] = _.bufferTumbling(2).mapTask { readings =>
    Task {
      val validReadings = readings.collect { case r: ValidReading => r }
      val average = if (validReadings.nonEmpty) validReadings.map(_.value).sum / validReadings.size else -1
      ValidReading(readings.head.id, average)
    }
  }

  val storeReadings: Consumer[ValidReading, Unit] =
    Consumer.foreachTask(vr => Task{
//      logger.info(s"consumer v r: $vr thread id: ${Thread.currentThread().getId}")
      ()
    })

  val processSingleFile: Transformer[File, ValidReading] = parseFile.andThen(computeAverage)

  def importFromFiles: CancelableFuture[Unit] = {
    val files = importDirectory.listFiles()
    logger.info(s"Starting import of ${files.size} files from ${importDirectory.getPath}")
    val startTime = System.currentTimeMillis()
    Observable
      .fromIterable(files)
      .bufferTumbling(concurrentFiles)
      .flatMap { fs: Seq[File] =>
        Observable.merge(fs.map(f => processSingleFile(Observable.now(f))): _*)
      }
      .consumeWith(storeReadings)
      .doOnFinish { _ =>
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        logger.info(s"Import finished in ${elapsedTime}s")
        Task.unit
      }
      .onErrorHandle(e => logger.error("Import failed", e))
      .runAsync
  }
}

object CsvImporter {

  type Transformer[-A, +B] = Observable[A] ⇒ Observable[B]

  def mapAsyncOrdered[A, B](parallelism: Int)(f: A => Task[B]): Transformer[A, B] =
    _.map(f).bufferTumbling(parallelism).flatMap { tasks =>
      val gathered = Task.gather(tasks)
      Observable.fromTask(gathered).concatMap(Observable.fromIterable)
    }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    //    val readingRepository = new ReadingRepository

    val completed = new CsvImporter(config /*, readingRepository*/).importFromFiles.andThen{case _  => println(s"completed in main")}
    //.onComplete(_ => println(s"completed in main") /*readingRepository.shutdown()*/)
    Await.result(completed, 5.seconds)

    //foreachParallel(concurrentWrites) doen't have any effect
    // the real effect has scheduler threadpool settings:
    //
    //-Dscala.concurrent.context.numThreads=4
    // -Dscala.concurrent.context.maxThreads=8

    // in effect are the following setings max(numThreads,concurrentWrites/Reads)
  }
}