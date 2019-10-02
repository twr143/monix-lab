package csv
import java.nio.file.Paths
import java.util.UUID
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import csv.model.ValidReading
import monix.reactive.{Consumer, Observable}
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Random
import monix.nio.file.writeAsync
import csv.DirectoryCleaner.cleanup
import csv.RandomDataGenerator.config
import monix.eval.Task

/**
  * Created by Ilya Volynin on 11.03.2019 at 7:56.
  */
object RandomDataGenerator extends App with LazyLogging {

  val config = ConfigFactory.load()

  val numberOfFiles = config.getInt("generator.number-of-files")

  logger.info(s"number of files to generate ${numberOfFiles}")

  val numberOfPairs = config.getInt("generator.number-of-pairs")

  val invalidLineProbability = config.getDouble("generator.invalid-line-probability")

  def generate(): Future[Unit] = {
    logger.info("Starting generation")
    val startTime = System.currentTimeMillis()
    Observable.range(0, numberOfFiles)
      .map(_ => UUID.randomUUID().toString)
      .mapParallelUnordered(8) { fileName: String =>
        Observable.range(0, numberOfPairs).map { _ =>
          val id = Random.nextInt(1000000)
          Seq(ValidReading(id), ValidReading(id)).map { reading =>
            val value = if (Random.nextDouble() > invalidLineProbability) reading.value.toString else "invalid_value"
            s"${reading.id};$value\n"
          }.reduce(_ + _).getBytes
        }.consumeWith(writeAsync(Paths.get(s"data/$fileName.csv")))
      }.completedL.runAsync.andThen { case x =>
      val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
      logger.info(s"generation finished in ${elapsedTime}s")
    }
    //      .doOnFinish { _ =>
    //              val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
    //              logger.info(s"Import finished in ${elapsedTime}s")
    //              Task.unit
    //            }
  }

  val res = for {
    _ <- cleanup(config)
    r <- generate()
  } yield r

  Await.result(res, Duration.Inf)

  logger.info("Generated random data")
}
