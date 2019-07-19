package csv.model
import scala.util.Random

/**
  * Created by Ilya Volynin on 11.03.2019 at 8:00.
  */
sealed trait Reading {

  def id: Int
}

case class ValidReading(id: Int, value: Double = Random.nextDouble()) extends Reading {

  override def toString: String = s"[id=$id,value=$value]"
}

case class InvalidReading(id: Int) extends Reading