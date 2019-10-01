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

/**
  * Created by Ilya Volynin on 11.03.2019 at 7:56.
  */
object RandomDataGenerator extends App with LazyLogging {

  val config = ConfigFactory.load()

  val numberOfFiles = config.getInt("generator.number-of-files")

  val numberOfPairs = config.getInt("generator.number-of-pairs")

  val invalidLineProbability = config.getDouble("generator.invalid-line-probability")

  logger.info("Starting generation")

  def generate(): Future[Unit] = {
    Observable.range(0, numberOfFiles)
      .map(_ => UUID.randomUUID().toString)
      .mapParallelUnordered(numberOfFiles) { fileName: String =>
        Observable.range(0, numberOfPairs).map { _ =>
          val id = Random.nextInt(1000000)
          Seq(ValidReading(id), ValidReading(id)).map { reading =>
            val value = if (Random.nextDouble() > invalidLineProbability) reading.value.toString else "invalid_value"
            s"${reading.id};$value\n"
          }.reduce(_ + _).getBytes
        }.consumeWith(writeAsync(Paths.get(s"data/$fileName.csv")))
      }
      .completedL
      .runAsync
  }

  Await.ready(cleanup(config).map(_ => generate()), Duration.Inf)

  logger.info("Generated random data")
}
