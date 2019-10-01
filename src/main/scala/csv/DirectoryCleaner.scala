package csv
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.reactive.{Consumer, Observable}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import monix.execution.Scheduler.Implicits.global

/**
  * Created by Ilya Volynin on 01.10.2019 at 20:29.
  */
object DirectoryCleaner extends App with LazyLogging {

  val config = ConfigFactory.load()


  def cleanup(config:Config): Future[Unit] = {
    val importDirectory = Paths.get(config.getString("importer.import-directory")).toFile

    val files = importDirectory.listFiles()
    Observable
      .fromIterable(files).mapTask(f => Task {
      f.delete()
    }).consumeWith(Consumer.complete)
      .runAsync
      .andThen { case _ => logger.info("cleaner completed") }
  }

  Await.ready(cleanup(config), Duration.Inf)
}
