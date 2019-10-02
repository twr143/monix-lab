package csv
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.reactive.{Consumer, Observable}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import monix.execution.Scheduler.Implicits.global
import monix.eval._
/**
  * Created by Ilya Volynin on 01.10.2019 at 20:29.
  */
object DirectoryCleaner extends App with LazyLogging {

  val config = ConfigFactory.load()


  def cleanup(config:Config): Future[Unit] = {
    val importDirectory = Paths.get(config.getString("importer.import-directory")).toFile
    val startTime = System.currentTimeMillis()

    val files = importDirectory.listFiles()
    Observable
      .fromIterable(files).mapTask(f =>
      Task(f.delete())
    ).consumeWith(Consumer.complete)
      .runAsync
      .andThen { case _ =>
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        logger.info(s"Cleanup finished in ${elapsedTime}s")
      }
  }

  Await.ready(cleanup(config), Duration.Inf)
}
